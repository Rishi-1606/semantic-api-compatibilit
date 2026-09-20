package com.semanticapi.controller;

import com.semanticapi.dto.*;
import com.semanticapi.model.TransformationRule;
import com.semanticapi.service.ApprovalService;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Phase 9 — Safety &amp; Approval.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /api/approval/rules/{ruleId}/approve
 *   Human-approve a GENERATED rule.
 *   Body (optional): { "reason": "Reviewed and verified" }
 *   Returns: updated TransformationRuleResponse (HTTP 200)
 *   Errors:  404 (not found), 422 (INVALID/REJECTED/low-confidence/safety failure)
 *
 * POST /api/approval/rules/{ruleId}/reject
 *   Human-reject a GENERATED or APPROVED rule.
 *   Body (optional): { "reason": "Mapped incorrectly" }
 *   Returns: updated TransformationRuleResponse (HTTP 200)
 *   Errors:  404 (not found)
 *
 * POST /api/approval/reports/{reportId}/auto-approve
 *   Batch auto-approve all eligible GENERATED rules for a report.
 *   Eligible = AUTO_APPROVED candidate + confidence ≥ threshold + safety valid.
 *   Returns: AutoApprovalResult (HTTP 200)
 *   Errors:  404 (report not found)
 *
 * GET /api/approval/rules/{ruleId}/validate
 *   Dry-run Phase 9 safety + confidence check. Does NOT change rule status.
 *   Returns: ApprovalValidationReport (HTTP 200)
 *   Errors:  404 (not found)
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>This controller manages approval state only.
 * It does NOT execute transformations, forward API requests, or modify traffic.
 * Phase 10 runtime execution is NOT implemented here.
 */
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * POST /api/approval/rules/{ruleId}/approve
     *
     * <p>Human-approve a GENERATED rule. Idempotent — approving an already-APPROVED
     * rule returns HTTP 200 without error.
     *
     * <p>Rejects (HTTP 422) when:
     * <ul>
     *   <li>rule is INVALID</li>
     *   <li>rule is REJECTED (re-generate required)</li>
     *   <li>confidence is below the minimum threshold</li>
     *   <li>Phase 9 safety re-validation fails</li>
     * </ul>
     */
    @PostMapping("/rules/{ruleId}/approve")
    public TransformationRuleResponse approveRule(
            @PathVariable Long ruleId,
            @RequestBody(required = false) ApproveRejectRuleRequest request) {
        String note = request != null ? request.getReason() : null;
        return TransformationRuleResponse.from(approvalService.approveRule(ruleId, note));
    }

    /**
     * POST /api/approval/rules/{ruleId}/reject
     *
     * <p>Human-reject a GENERATED or APPROVED rule (safety override).
     * Idempotent — rejecting an already-REJECTED rule returns HTTP 200.
     * Rejected rules are stored for auditability but must not be executed by Phase 10.
     */
    @PostMapping("/rules/{ruleId}/reject")
    public TransformationRuleResponse rejectRule(
            @PathVariable Long ruleId,
            @RequestBody(required = false) ApproveRejectRuleRequest request) {
        String note = request != null ? request.getReason() : null;
        return TransformationRuleResponse.from(approvalService.rejectRule(ruleId, note));
    }

    /**
     * POST /api/approval/reports/{reportId}/auto-approve
     *
     * <p>Batch auto-approve all rules for a report that satisfy:
     * <ul>
     *   <li>ruleStatus == GENERATED</li>
     *   <li>candidateStatus == AUTO_APPROVED</li>
     *   <li>confidence ≥ configured auto-approve threshold (default 0.90)</li>
     *   <li>Phase 9 safety re-validation passes</li>
     * </ul>
     *
     * <p>REQUIRES_REVIEW candidates are skipped (human approval required).
     * Rules below the minimum confidence threshold are also skipped.
     *
     * @param reportId Phase 7 compatibility report ID
     */
    @PostMapping("/reports/{reportId}/auto-approve")
    public AutoApprovalResult autoApproveForReport(@PathVariable Long reportId) {
        return approvalService.autoApproveForReport(reportId);
    }

    /**
     * GET /api/approval/rules/{ruleId}/validate
     *
     * <p>Dry-run Phase 9 safety and confidence check.
     * Does NOT change the rule's status.
     * Returns a report explaining whether the rule would be approved
     * and, if not, which conditions are blocking it.
     *
     * @param ruleId rule to validate
     */
    @GetMapping("/rules/{ruleId}/validate")
    public ApprovalValidationReport validateRule(@PathVariable Long ruleId) {
        return approvalService.validateForApproval(ruleId);
    }
}
