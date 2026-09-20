package com.semanticapi.service;

import com.semanticapi.dto.ApprovalValidationReport;
import com.semanticapi.dto.AutoApprovalResult;
import com.semanticapi.dto.TransformationRuleResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.MatchCandidate;
import com.semanticapi.model.TransformationRule;
import com.semanticapi.model.TransformationRule.RuleStatus;
import com.semanticapi.repository.CompatibilityReportRepository;
import com.semanticapi.repository.TransformationRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 9 — Safety &amp; Approval Service.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Apply configurable confidence policy (§ 7) before approving any rule.</li>
 *   <li>Re-run Phase 8 safety validation before approval (high confidence never
 *       overrides structural safety).</li>
 *   <li>Enforce valid lifecycle transitions:
 *       <ul>
 *         <li>GENERATED → APPROVED (confidence ≥ min AND safety valid)</li>
 *         <li>GENERATED → REJECTED (always)</li>
 *         <li>APPROVED  → REJECTED (human safety override)</li>
 *         <li>INVALID   → APPROVED blocked (422)</li>
 *         <li>REJECTED  → APPROVED blocked (422 — re-generate required)</li>
 *       </ul>
 *   </li>
 *   <li>Record audit timestamps ({@code approvedAt}, {@code rejectedAt})
 *       and human review notes ({@code approvalNote}).</li>
 *   <li>Support batch auto-approval for eligible AUTO_APPROVED candidates.</li>
 *   <li>Provide dry-run validation without status changes.</li>
 * </ol>
 *
 * <h2>Confidence policy (spec § 7, configurable)</h2>
 * <pre>
 *   confidence ≥ autoThreshold (default 0.90) AND candidateStatus = AUTO_APPROVED
 *       → eligible for automatic approval
 *
 *   confidence ≥ minThreshold (default 0.70) AND ≤ autoThreshold
 *       → requires explicit human approval (REQUIRES_REVIEW territory)
 *
 *   confidence &lt; minThreshold
 *       → cannot be approved (too low confidence; reject or re-map)
 * </pre>
 *
 * <h2>Safety principle</h2>
 * <p>High confidence NEVER overrides safety validation.
 * A rule with confidence 1.0 that fails {@code validateRule()} is blocked.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    @Value("${app.confidence.auto-approve-threshold:0.90}")
    private double autoApproveThreshold;

    @Value("${app.confidence.human-approval-threshold:0.70}")
    private double minApprovalThreshold;

    private final TransformationRuleRepository  ruleRepository;
    private final CompatibilityReportRepository reportRepository;
    private final TransformationRuleService     ruleService;   // reuse Phase 8 validation

    public ApprovalService(
            TransformationRuleRepository ruleRepository,
            CompatibilityReportRepository reportRepository,
            TransformationRuleService ruleService) {
        this.ruleRepository   = ruleRepository;
        this.reportRepository = reportRepository;
        this.ruleService      = ruleService;
    }

    // ══════════════════════════════════════════════════════════
    // Human approval
    // ══════════════════════════════════════════════════════════

    /**
     * Human-approve a single GENERATED rule.
     *
     * <p>Enforces:
     * <ol>
     *   <li>Rule must exist (404 otherwise).</li>
     *   <li>Idempotent: if already APPROVED, return as-is.</li>
     *   <li>INVALID → 422 (cannot be approved).</li>
     *   <li>REJECTED → 422 (must re-generate to get a fresh GENERATED rule).</li>
     *   <li>Confidence &lt; minThreshold → 422 (rule cannot be approved).</li>
     *   <li>Phase 8 safety re-validation must pass → 422 if errors found.</li>
     *   <li>Set ruleStatus=APPROVED, approvedAt=now(), approvalNote=note.</li>
     * </ol>
     *
     * <p>Note: this does NOT enforce candidateStatus == AUTO_APPROVED.
     * Human approval is permitted for REQUIRES_REVIEW candidates as long as
     * confidence ≥ minThreshold and safety passes.
     *
     * @param ruleId rule to approve
     * @param note   optional human-readable approval note
     * @return the persisted, updated rule
     * @throws ResourceNotFoundException if the rule does not exist
     * @throws IllegalStateException     (→ HTTP 422) if the rule cannot be approved
     */
    @Transactional
    public TransformationRule approveRule(Long ruleId, String note) {
        TransformationRule rule = loadRule(ruleId);

        // ── Idempotent ───────────────────────────────────────
        if (rule.getRuleStatus() == RuleStatus.APPROVED) {
            log.debug("Phase 9: rule {} already APPROVED — idempotent return", ruleId);
            return rule;
        }

        // ── Lifecycle guards ─────────────────────────────────
        if (rule.getRuleStatus() == RuleStatus.INVALID) {
            throw new IllegalStateException(
                    "Rule " + ruleId + " is INVALID and cannot be approved. " +
                    "Validation errors: " + rule.getValidationErrors() + ". " +
                    "Re-generate rules for the report to produce a corrected rule.");
        }
        if (rule.getRuleStatus() == RuleStatus.REJECTED) {
            throw new IllegalStateException(
                    "Rule " + ruleId + " has been REJECTED and cannot be re-approved. " +
                    "Re-generate rules for the report to produce a fresh GENERATED rule.");
        }

        // ── Confidence policy ────────────────────────────────
        checkMinConfidence(rule);

        // ── Safety re-validation (high confidence ≠ safe) ───
        List<String> errors = ruleService.validateRule(rule, null);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Rule " + ruleId + " failed Phase 9 safety re-validation: " +
                    String.join("; ", errors));
        }

        // ── Approve ──────────────────────────────────────────
        Instant now = Instant.now();
        rule.setRuleStatus(RuleStatus.APPROVED);
        rule.setApprovedAt(now);
        rule.setApprovalNote(note);
        TransformationRule saved = ruleRepository.save(rule);

        log.info("Phase 9: rule {} APPROVED (candidateStatus={}, confidence={:.3f})",
                ruleId, rule.getCandidateStatus(), rule.getConfidence());
        return saved;
    }

    /**
     * Human-reject a rule.
     *
     * <p>Allowed from GENERATED or APPROVED (human safety override).
     * Idempotent if already REJECTED. The {@code rejectionReason} field
     * is set; {@code rejectedAt} and {@code approvalNote} are also recorded.
     *
     * @param ruleId rule to reject
     * @param note   human-readable rejection reason
     * @return the persisted, updated rule
     * @throws ResourceNotFoundException if the rule does not exist
     */
    @Transactional
    public TransformationRule rejectRule(Long ruleId, String note) {
        TransformationRule rule = loadRule(ruleId);

        // ── Idempotent ───────────────────────────────────────
        if (rule.getRuleStatus() == RuleStatus.REJECTED) {
            log.debug("Phase 9: rule {} already REJECTED — idempotent return", ruleId);
            return rule;
        }

        Instant now = Instant.now();
        rule.setRuleStatus(RuleStatus.REJECTED);
        rule.setRejectedAt(now);
        rule.setRejectionReason(note != null ? note : "No reason provided");
        rule.setApprovalNote(note);
        TransformationRule saved = ruleRepository.save(rule);

        log.info("Phase 9: rule {} REJECTED (previous status={}, reason={})",
                ruleId, rule.getRuleStatus(), rule.getRejectionReason());
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // Batch auto-approval
    // ══════════════════════════════════════════════════════════

    /**
     * Batch auto-approve all eligible rules for a Phase 7 compatibility report.
     *
     * <p>A rule is auto-approved only when ALL conditions hold:
     * <ul>
     *   <li>ruleStatus == GENERATED</li>
     *   <li>candidateStatus == AUTO_APPROVED</li>
     *   <li>confidence ≥ autoApproveThreshold</li>
     *   <li>Phase 8 safety re-validation passes (zero errors)</li>
     * </ul>
     *
     * <p>Rules that require human review (REQUIRES_REVIEW candidateStatus or
     * confidence between minThreshold and autoThreshold) are left as GENERATED.
     * Rules below the minimum confidence threshold are also left as GENERATED
     * (they cannot be approved at all, even manually).
     *
     * @param reportId the Phase 7 compatibility report ID
     * @return summary of auto-approval outcome
     * @throws ResourceNotFoundException if the report does not exist
     */
    @Transactional
    public AutoApprovalResult autoApproveForReport(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException("CompatibilityReport", reportId);
        }

        List<TransformationRule> generatedRules =
                ruleRepository.findByReportIdAndRuleStatus(reportId, RuleStatus.GENERATED);

        int totalGenerated       = generatedRules.size();
        int autoApproved         = 0;
        int requiresHumanReview  = 0;
        int skippedLowConf       = 0;
        int skippedReviewRange   = 0;
        int skippedInvalid       = 0;
        List<TransformationRule> approvedRules = new ArrayList<>();

        for (TransformationRule rule : generatedRules) {
            double   conf   = rule.getConfidence();
            String   cs     = rule.getCandidateStatus();
            boolean  isAuto = MatchCandidate.ApprovalStatus.AUTO_APPROVED.name().equals(cs);

            // ── Not AUTO_APPROVED candidate → human review required ──
            if (!isAuto) {
                requiresHumanReview++;
                log.debug("  Phase 9 auto-approve: rule {} skipped — candidateStatus={} (human review required)",
                        rule.getId(), cs);
                continue;
            }

            // ── Confidence below minimum → cannot be approved at all ──
            if (conf < minApprovalThreshold) {
                skippedLowConf++;
                log.debug("  Phase 9 auto-approve: rule {} skipped — confidence {:.3f} < min {:.2f}",
                        rule.getId(), conf, minApprovalThreshold);
                continue;
            }

            // ── Confidence in review range (min ≤ conf < auto) → human required ──
            if (conf < autoApproveThreshold) {
                skippedReviewRange++;
                log.debug("  Phase 9 auto-approve: rule {} skipped — confidence {:.3f} < auto-threshold {:.2f}",
                        rule.getId(), conf, autoApproveThreshold);
                continue;
            }

            // ── Safety re-validation (confidence cannot override safety) ──
            List<String> errors = ruleService.validateRule(rule, null);
            if (!errors.isEmpty()) {
                skippedInvalid++;
                log.warn("  Phase 9 auto-approve: rule {} BLOCKED by safety re-validation: {}",
                        rule.getId(), errors);
                continue;
            }

            // ── Auto-approve ──────────────────────────────────
            Instant now = Instant.now();
            rule.setRuleStatus(RuleStatus.APPROVED);
            rule.setApprovedAt(now);
            rule.setApprovalNote("Auto-approved: confidence=" + conf
                    + ", candidateStatus=" + cs);
            approvedRules.add(rule);
            autoApproved++;
            log.debug("  Phase 9 auto-approve: rule {} APPROVED (confidence={:.3f})",
                    rule.getId(), conf);
        }

        // Persist all newly approved rules in one batch
        if (!approvedRules.isEmpty()) {
            ruleRepository.saveAll(approvedRules);
        }

        log.info("Phase 9 auto-approve: report {} → {} auto-approved, {} requires-human, " +
                 "{} low-conf, {} review-range, {} invalid/failed out of {} generated",
                reportId, autoApproved, requiresHumanReview,
                skippedLowConf, skippedReviewRange, skippedInvalid, totalGenerated);

        AutoApprovalResult result = new AutoApprovalResult();
        result.setReportId(reportId);
        result.setTotalGenerated(totalGenerated);
        result.setAutoApproved(autoApproved);
        result.setRequiresHumanReview(requiresHumanReview);
        result.setSkippedLowConfidence(skippedLowConf);
        result.setSkippedRequiresReview(skippedReviewRange);
        result.setSkippedInvalidOrFailed(skippedInvalid);
        result.setApprovedRules(
                approvedRules.stream().map(TransformationRuleResponse::from).toList());
        return result;
    }

    // ══════════════════════════════════════════════════════════
    // Dry-run validation
    // ══════════════════════════════════════════════════════════

    /**
     * Dry-run Phase 9 safety and confidence check for a single rule.
     *
     * <p>Does NOT change the rule's status. Returns a report explaining
     * whether the rule would be approved and, if not, why.
     *
     * @param ruleId rule to validate
     * @return validation report
     * @throws ResourceNotFoundException if the rule does not exist
     */
    @Transactional(readOnly = true)
    public ApprovalValidationReport validateForApproval(Long ruleId) {
        TransformationRule rule = loadRule(ruleId);

        List<String> blockingReasons = new ArrayList<>();

        // ── Lifecycle checks ─────────────────────────────────
        if (rule.getRuleStatus() == RuleStatus.INVALID) {
            blockingReasons.add("Rule is INVALID and cannot be approved. Errors: "
                    + rule.getValidationErrors());
        }
        if (rule.getRuleStatus() == RuleStatus.REJECTED) {
            blockingReasons.add("Rule is REJECTED. Re-generate to get a fresh GENERATED rule.");
        }
        if (rule.getRuleStatus() == RuleStatus.APPROVED) {
            // Already approved — report as eligible with no blockers
            return buildReport(rule, true, List.of());
        }

        // ── Confidence policy ────────────────────────────────
        double conf = rule.getConfidence();
        if (conf < minApprovalThreshold) {
            blockingReasons.add(String.format(
                    "Confidence %.3f is below minimum approval threshold %.2f",
                    conf, minApprovalThreshold));
        }

        // ── Safety re-validation ─────────────────────────────
        if (rule.getRuleStatus() == RuleStatus.GENERATED) {
            List<String> safetyErrors = ruleService.validateRule(rule, null);
            blockingReasons.addAll(safetyErrors);
        }

        return buildReport(rule, blockingReasons.isEmpty(), blockingReasons);
    }

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    private TransformationRule loadRule(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("TransformationRule", ruleId));
    }

    /**
     * Enforces the minimum confidence threshold.
     * Throws IllegalStateException (→ 422) if the rule's confidence is below the minimum.
     */
    private void checkMinConfidence(TransformationRule rule) {
        if (rule.getConfidence() < minApprovalThreshold) {
            throw new IllegalStateException(String.format(
                    "Rule %d cannot be approved: confidence %.3f is below the minimum " +
                    "approval threshold %.2f. Re-map with a higher-confidence semantic match.",
                    rule.getId(), rule.getConfidence(), minApprovalThreshold));
        }
    }

    private ApprovalValidationReport buildReport(
            TransformationRule rule, boolean eligible, List<String> reasons) {
        ApprovalValidationReport report = new ApprovalValidationReport();
        report.setRuleId(rule.getId());
        report.setRuleStatus(rule.getRuleStatus().name());
        report.setCandidateStatus(rule.getCandidateStatus());
        report.setConfidence(rule.getConfidence());
        report.setOperation(rule.getOperation() != null ? rule.getOperation().name() : null);
        report.setEligible(eligible);
        report.setBlockingReasons(reasons);
        return report;
    }

    // ── Threshold accessors (for testing) ────────────────────

    public double getAutoApproveThreshold() { return autoApproveThreshold; }
    public double getMinApprovalThreshold() { return minApprovalThreshold; }
}
