package com.semanticapi.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.dto.ApprovalValidationReport;
import com.semanticapi.dto.AutoApprovalResult;
import com.semanticapi.dto.TransformationRuleResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.repository.CompatibilityReportRepository;
import com.semanticapi.repository.TransformationRuleRepository;
import com.semanticapi.service.ApprovalService;
import com.semanticapi.service.TransformationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ApprovalService} (Phase 9 — Safety &amp; Approval).
 *
 * <p>All JPA repositories and the TransformationRuleService are mocked.
 * No Spring context or database needed.
 *
 * <h2>Covered scenarios</h2>
 * <ol>
 *   <li>Human approval — happy paths</li>
 *   <li>Human approval — INVALID rule blocked</li>
 *   <li>Human approval — REJECTED rule blocked</li>
 *   <li>Human approval — already APPROVED (idempotent)</li>
 *   <li>Human approval — low confidence blocked</li>
 *   <li>Human approval — safety re-validation failure blocked</li>
 *   <li>Human approval — REQUIRES_REVIEW with sufficient confidence → allowed</li>
 *   <li>Human rejection — from GENERATED</li>
 *   <li>Human rejection — from APPROVED (safety override)</li>
 *   <li>Human rejection — already REJECTED (idempotent)</li>
 *   <li>Auto-approve batch — AUTO_APPROVED high-confidence → approved</li>
 *   <li>Auto-approve batch — REQUIRES_REVIEW → skipped</li>
 *   <li>Auto-approve batch — confidence below min → skipped</li>
 *   <li>Auto-approve batch — confidence in review range → skipped</li>
 *   <li>Auto-approve batch — safety fails → skipped</li>
 *   <li>Auto-approve batch — INVALID rule → skipped</li>
 *   <li>Auto-approve batch — report not found → 404</li>
 *   <li>Dry-run validate — eligible rule</li>
 *   <li>Dry-run validate — INVALID rule → not eligible</li>
 *   <li>Dry-run validate — REJECTED rule → not eligible</li>
 *   <li>Dry-run validate — low confidence → not eligible</li>
 *   <li>Dry-run validate — already APPROVED → eligible</li>
 *   <li>Dry-run validate — safety errors → not eligible</li>
 *   <li>Regression — rule not found → ResourceNotFoundException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService")
class ApprovalServiceTest {

    @Mock TransformationRuleRepository  ruleRepository;
    @Mock CompatibilityReportRepository reportRepository;
    @Mock TransformationRuleService     ruleService;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(ruleRepository, reportRepository, ruleService);
        // Inject threshold values directly via reflection (no Spring context)
        setField(service, "autoApproveThreshold", 0.90);
        setField(service, "minApprovalThreshold",  0.70);
    }

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TransformationRule rule(long id,
                                    TransformationRule.RuleStatus status,
                                    String candidateStatus,
                                    double confidence) {
        ApiVersion srcVer = new ApiVersion(); srcVer.setVersion("V1"); setId(srcVer, 1L);
        ApiVersion tgtVer = new ApiVersion(); tgtVer.setVersion("V2"); setId(tgtVer, 2L);
        MatchSession session = new MatchSession();
        session.setSourceVersion(srcVer); session.setTargetVersion(tgtVer); setId(session, 10L);
        CompatibilityReport report = new CompatibilityReport();
        report.setMatchSession(session);
        report.setSourceVersion(srcVer); report.setTargetVersion(tgtVer);
        report.setCompatibilityStatus(CompatibilityReport.CompatibilityStatus.COMPATIBLE);
        report.setOverallScore(0.95); setId(report, 5L);

        Endpoint srcEp = new Endpoint(); srcEp.setHttpMethod("POST"); srcEp.setPath("/v1"); setId(srcEp, 1L);
        Endpoint tgtEp = new Endpoint(); tgtEp.setHttpMethod("POST"); tgtEp.setPath("/v2"); setId(tgtEp, 2L);

        TransformationRule r = new TransformationRule();
        setId(r, id);
        r.setReport(report);
        r.setSourceEndpoint(srcEp);
        r.setTargetEndpoint(tgtEp);
        r.setSourceEndpointPath("POST /v1");
        r.setTargetEndpointPath("POST /v2");
        r.setSourcePath("name");
        r.setTargetPath("fullName");
        r.setOperation(TransformationRule.Operation.RENAME);
        r.setSourceType("string");
        r.setTargetType("string");
        r.setConfidence(confidence);
        r.setCandidateStatus(candidateStatus);
        r.setRuleStatus(status);
        r.setParameters("{}");
        return r;
    }

    private TransformationRule generated(long id, String candidateStatus, double confidence) {
        return rule(id, TransformationRule.RuleStatus.GENERATED, candidateStatus, confidence);
    }

    private TransformationRule approved(long id) {
        return rule(id, TransformationRule.RuleStatus.APPROVED, "AUTO_APPROVED", 0.95);
    }

    private TransformationRule rejected(long id) {
        return rule(id, TransformationRule.RuleStatus.REJECTED, "AUTO_APPROVED", 0.95);
    }

    private TransformationRule invalid(long id) {
        TransformationRule r = rule(id, TransformationRule.RuleStatus.INVALID, "AUTO_APPROVED", 0.95);
        r.setValidationErrors("Unsafe: object → string");
        return r;
    }

    // ══════════════════════════════════════════════════════════
    // 1. Human Approval
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Human Approval")
    class HumanApprovalTests {

        @Test
        @DisplayName("valid GENERATED AUTO_APPROVED high-confidence → APPROVED")
        void approve_validGeneratedAutoApproved_becomesApproved() {
            TransformationRule r = generated(1L, "AUTO_APPROVED", 0.95);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.approveRule(1L, "Looks good");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
            assertThat(result.getApprovedAt()).isNotNull();
            assertThat(result.getApprovalNote()).isEqualTo("Looks good");
            verify(ruleRepository).save(r);
        }

        @Test
        @DisplayName("valid GENERATED REQUIRES_REVIEW with sufficient confidence → APPROVED")
        void approve_requiresReviewSufficientConfidence_becomesApproved() {
            TransformationRule r = generated(2L, "REQUIRES_REVIEW", 0.75);
            when(ruleRepository.findById(2L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.approveRule(2L, "Manually verified");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
        }

        @Test
        @DisplayName("INVALID rule → IllegalStateException (422)")
        void approve_invalidRule_throwsIllegalState() {
            TransformationRule r = invalid(3L);
            when(ruleRepository.findById(3L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveRule(3L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("REJECTED rule → IllegalStateException (422)")
        void approve_rejectedRule_throwsIllegalState() {
            TransformationRule r = rejected(4L);
            when(ruleRepository.findById(4L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveRule(4L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");
        }

        @Test
        @DisplayName("already APPROVED → idempotent, returns as-is")
        void approve_alreadyApproved_idempotentReturn() {
            TransformationRule r = approved(5L);
            when(ruleRepository.findById(5L)).thenReturn(Optional.of(r));

            TransformationRule result = service.approveRule(5L, "again");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
            verify(ruleRepository, never()).save(any());
        }

        @Test
        @DisplayName("confidence below min threshold (0.60 < 0.70) → IllegalStateException (422)")
        void approve_belowMinConfidence_throwsIllegalState() {
            TransformationRule r = generated(6L, "AUTO_APPROVED", 0.60);
            when(ruleRepository.findById(6L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveRule(6L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("confidence")
                    .hasMessageContaining("0.70");
        }

        @Test
        @DisplayName("safety re-validation fails → IllegalStateException (422)")
        void approve_safetyReFails_throwsIllegalState() {
            TransformationRule r = generated(7L, "AUTO_APPROVED", 0.95);
            when(ruleRepository.findById(7L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null))
                    .thenReturn(List.of("Unsafe: object → string"));

            assertThatThrownBy(() -> service.approveRule(7L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("safety re-validation")
                    .hasMessageContaining("Unsafe");
        }

        @Test
        @DisplayName("rule not found → ResourceNotFoundException")
        void approve_notFound_throwsNotFound() {
            when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveRule(999L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 2. Human Rejection
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Human Rejection")
    class HumanRejectionTests {

        @Test
        @DisplayName("GENERATED → REJECTED with note")
        void reject_generated_becomesRejected() {
            TransformationRule r = generated(10L, "AUTO_APPROVED", 0.92);
            when(ruleRepository.findById(10L)).thenReturn(Optional.of(r));
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.rejectRule(10L, "Mapped incorrectly");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.REJECTED);
            assertThat(result.getRejectionReason()).isEqualTo("Mapped incorrectly");
            assertThat(result.getRejectedAt()).isNotNull();
        }

        @Test
        @DisplayName("APPROVED → REJECTED (human safety override)")
        void reject_approved_becomesRejected() {
            TransformationRule r = approved(11L);
            when(ruleRepository.findById(11L)).thenReturn(Optional.of(r));
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.rejectRule(11L, "Safety concern found");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.REJECTED);
        }

        @Test
        @DisplayName("already REJECTED → idempotent, returns as-is")
        void reject_alreadyRejected_idempotentReturn() {
            TransformationRule r = rejected(12L);
            when(ruleRepository.findById(12L)).thenReturn(Optional.of(r));

            TransformationRule result = service.rejectRule(12L, "again");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.REJECTED);
            verify(ruleRepository, never()).save(any());
        }

        @Test
        @DisplayName("no reason provided → default note")
        void reject_noReason_defaultNote() {
            TransformationRule r = generated(13L, "REQUIRES_REVIEW", 0.80);
            when(ruleRepository.findById(13L)).thenReturn(Optional.of(r));
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.rejectRule(13L, null);

            assertThat(result.getRejectionReason()).isEqualTo("No reason provided");
        }

        @Test
        @DisplayName("rule not found → ResourceNotFoundException")
        void reject_notFound_throwsNotFound() {
            when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rejectRule(999L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 3. Batch Auto-approval
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Batch Auto-approval")
    class AutoApproveTests {

        @Test
        @DisplayName("AUTO_APPROVED + confidence 0.95 + safety OK → auto-approved")
        void autoApprove_eligibleRule_approved() {
            TransformationRule r = generated(20L, "AUTO_APPROVED", 0.95);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(1);
            assertThat(result.getTotalGenerated()).isEqualTo(1);
            assertThat(result.getApprovedRules()).hasSize(1);
            assertThat(r.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
            assertThat(r.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("REQUIRES_REVIEW candidate → skipped (requires human)")
        void autoApprove_requiresReview_skipped() {
            TransformationRule r = generated(21L, "REQUIRES_REVIEW", 0.95);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getRequiresHumanReview()).isEqualTo(1);
            verify(ruleRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("confidence below min (0.60) → skipped")
        void autoApprove_belowMinConfidence_skipped() {
            TransformationRule r = generated(22L, "AUTO_APPROVED", 0.60);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getSkippedLowConfidence()).isEqualTo(1);
        }

        @Test
        @DisplayName("confidence in review range (0.80) → skipped (not auto-approvable)")
        void autoApprove_reviewRangeConfidence_skipped() {
            TransformationRule r = generated(23L, "AUTO_APPROVED", 0.80);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getSkippedRequiresReview()).isEqualTo(1);
        }

        @Test
        @DisplayName("high-confidence but safety fails → skipped (safety overrides confidence)")
        void autoApprove_highConfidenceSafetyFails_skipped() {
            TransformationRule r = generated(24L, "AUTO_APPROVED", 0.98);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));
            when(ruleService.validateRule(r, null))
                    .thenReturn(List.of("Unsafe: object → string"));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getSkippedInvalidOrFailed()).isEqualTo(1);
        }

        @Test
        @DisplayName("mixed: 1 eligible + 1 REQUIRES_REVIEW + 1 low-conf → 1 approved")
        void autoApprove_mixedRules_onlyEligibleApproved() {
            TransformationRule eligible    = generated(30L, "AUTO_APPROVED",   0.95);
            TransformationRule reviewNeeded = generated(31L, "REQUIRES_REVIEW", 0.95);
            TransformationRule lowConf      = generated(32L, "AUTO_APPROVED",   0.50);

            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(eligible, reviewNeeded, lowConf));
            when(ruleService.validateRule(eligible, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(1);
            assertThat(result.getRequiresHumanReview()).isEqualTo(1);
            assertThat(result.getSkippedLowConfidence()).isEqualTo(1);
            assertThat(result.getTotalGenerated()).isEqualTo(3);
            assertThat(eligible.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
            assertThat(reviewNeeded.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.GENERATED);
            assertThat(lowConf.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.GENERATED);
        }

        @Test
        @DisplayName("no GENERATED rules → empty result")
        void autoApprove_noGeneratedRules_emptyResult() {
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(Collections.emptyList());

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getTotalGenerated()).isEqualTo(0);
            verify(ruleRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("report not found → ResourceNotFoundException")
        void autoApprove_reportNotFound_throwsNotFound() {
            when(reportRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.autoApproveForReport(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 4. Dry-run validation
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Dry-run Validation")
    class DryRunValidationTests {

        @Test
        @DisplayName("valid GENERATED rule → eligible=true, no blocking reasons")
        void validate_validGenerated_eligible() {
            TransformationRule r = generated(40L, "AUTO_APPROVED", 0.92);
            when(ruleRepository.findById(40L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());

            ApprovalValidationReport report = service.validateForApproval(40L);

            assertThat(report.isEligible()).isTrue();
            assertThat(report.getBlockingReasons()).isEmpty();
            assertThat(report.getConfidence()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("INVALID rule → eligible=false, blocking reason contains 'INVALID'")
        void validate_invalidRule_notEligible() {
            TransformationRule r = invalid(41L);
            when(ruleRepository.findById(41L)).thenReturn(Optional.of(r));

            ApprovalValidationReport report = service.validateForApproval(41L);

            assertThat(report.isEligible()).isFalse();
            assertThat(report.getBlockingReasons()).anyMatch(s -> s.contains("INVALID"));
        }

        @Test
        @DisplayName("REJECTED rule → eligible=false, blocking reason contains 'REJECTED'")
        void validate_rejectedRule_notEligible() {
            TransformationRule r = rejected(42L);
            when(ruleRepository.findById(42L)).thenReturn(Optional.of(r));

            ApprovalValidationReport report = service.validateForApproval(42L);

            assertThat(report.isEligible()).isFalse();
            assertThat(report.getBlockingReasons()).anyMatch(s -> s.contains("REJECTED"));
        }

        @Test
        @DisplayName("low confidence (0.60) → eligible=false, blocking reason mentions threshold")
        void validate_lowConfidence_notEligible() {
            TransformationRule r = generated(43L, "AUTO_APPROVED", 0.60);
            when(ruleRepository.findById(43L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());

            ApprovalValidationReport report = service.validateForApproval(43L);

            assertThat(report.isEligible()).isFalse();
            assertThat(report.getBlockingReasons()).anyMatch(s ->
                    s.contains("0.70") || s.contains("threshold"));
        }

        @Test
        @DisplayName("already APPROVED → eligible=true, no blocking reasons")
        void validate_approved_eligible() {
            TransformationRule r = approved(44L);
            when(ruleRepository.findById(44L)).thenReturn(Optional.of(r));

            ApprovalValidationReport report = service.validateForApproval(44L);

            assertThat(report.isEligible()).isTrue();
            assertThat(report.getBlockingReasons()).isEmpty();
        }

        @Test
        @DisplayName("safety errors → eligible=false, blocking reasons include safety errors")
        void validate_safetyErrors_notEligible() {
            TransformationRule r = generated(45L, "AUTO_APPROVED", 0.92);
            when(ruleRepository.findById(45L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null))
                    .thenReturn(List.of("Unsafe: object → string"));

            ApprovalValidationReport report = service.validateForApproval(45L);

            assertThat(report.isEligible()).isFalse();
            assertThat(report.getBlockingReasons()).contains("Unsafe: object → string");
        }

        @Test
        @DisplayName("rule not found → ResourceNotFoundException")
        void validate_notFound_throwsNotFound() {
            when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateForApproval(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 5. Safety policy regression
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Safety Policy Regression")
    class SafetyPolicyTests {

        @Test
        @DisplayName("confidence exactly at min threshold (0.70) → allowed (boundary)")
        void approve_exactlyAtMinThreshold_allowed() {
            TransformationRule r = generated(50L, "REQUIRES_REVIEW", 0.70);
            when(ruleRepository.findById(50L)).thenReturn(Optional.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.save(r)).thenReturn(r);

            TransformationRule result = service.approveRule(50L, "Boundary case");

            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
        }

        @Test
        @DisplayName("confidence just below min (0.699) → blocked")
        void approve_justBelowMinThreshold_blocked() {
            TransformationRule r = generated(51L, "AUTO_APPROVED", 0.699);
            when(ruleRepository.findById(51L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveRule(51L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0.70");
        }

        @Test
        @DisplayName("confidence exactly at auto threshold (0.90) → auto-approvable")
        void autoApprove_exactlyAtAutoThreshold_approved() {
            TransformationRule r = generated(52L, "AUTO_APPROVED", 0.90);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));
            when(ruleService.validateRule(r, null)).thenReturn(Collections.emptyList());
            when(ruleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(1);
        }

        @Test
        @DisplayName("confidence just below auto threshold (0.899) → not auto-approved (human required)")
        void autoApprove_justBelowAutoThreshold_requiresHuman() {
            TransformationRule r = generated(53L, "AUTO_APPROVED", 0.899);
            when(reportRepository.existsById(5L)).thenReturn(true);
            when(ruleRepository.findByReportIdAndRuleStatus(5L, TransformationRule.RuleStatus.GENERATED))
                    .thenReturn(List.of(r));

            AutoApprovalResult result = service.autoApproveForReport(5L);

            assertThat(result.getAutoApproved()).isEqualTo(0);
            assertThat(result.getSkippedRequiresReview()).isEqualTo(1);
        }

        @Test
        @DisplayName("REJECTED → APPROVED is BLOCKED (re-generate required)")
        void approve_rejected_blocked() {
            TransformationRule r = rejected(54L);
            when(ruleRepository.findById(54L)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveRule(54L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");
        }

        @Test
        @DisplayName("APPROVED → REJECTED is ALLOWED (human safety override)")
        void reject_approved_allowed() {
            TransformationRule r = approved(55L);
            when(ruleRepository.findById(55L)).thenReturn(Optional.of(r));
            when(ruleRepository.save(r)).thenReturn(r);

            assertThatCode(() -> service.rejectRule(55L, "Safety concern"))
                    .doesNotThrowAnyException();
        }
    }
}
