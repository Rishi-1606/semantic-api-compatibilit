package com.semanticapi.controller;

import com.semanticapi.dto.*;
import com.semanticapi.model.TransformationRule;
import com.semanticapi.service.TransformationRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Phase 8 — Transformation Rule Generator.
 *
 * <p>The approve and reject endpoints are powered by the Phase 9
 * {@link com.semanticapi.service.ApprovalService}, which enforces the confidence
 * policy and safety re-validation before allowing any status transition.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /api/transformation-rules/generate
 *   Trigger Phase 8 rule generation for a Phase 7 compatibility report.
 *   Body: { "reportId": 5 }
 *   Returns: generation summary with all produced rules.
 *
 * GET /api/transformation-rules/report/{reportId}
 *   List all rules (GENERATED, APPROVED, REJECTED, INVALID) for a report.
 *
 * GET /api/transformation-rules/{ruleId}
 *   Retrieve a single rule by its ID.
 *
 * GET /api/transformation-rules/endpoint-pair
 *   List rules for a source/target endpoint pair.
 *   Query params: srcEndpointId, tgtEndpointId
 *   Phase 10 uses this filtered to ruleStatus=APPROVED.
 *
 * POST /api/transformation-rules/{ruleId}/approve
 *   Approve a GENERATED rule. Optional body: { "reason": "..." }
 *
 * POST /api/transformation-rules/{ruleId}/reject
 *   Reject a GENERATED or APPROVED rule. Optional body: { "reason": "..." }
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>This controller produces and manages structured rule data only.
 * It does NOT execute transformations, forward requests, or modify API traffic.
 */
@RestController
@RequestMapping("/api/transformation-rules")
public class TransformationRuleController {

    private final TransformationRuleService ruleService;
    private final com.semanticapi.service.ApprovalService approvalService;

    public TransformationRuleController(
            TransformationRuleService ruleService,
            com.semanticapi.service.ApprovalService approvalService) {
        this.ruleService     = ruleService;
        this.approvalService = approvalService;
    }

    /**
     * POST /api/transformation-rules/generate
     *
     * <p>Generates transformation rules for all accepted MatchCandidates linked to
     * the given compatibility report. Idempotent — re-running replaces existing rules.
     * LOW_CONFIDENCE and REJECTED candidates are always excluded.
     *
     * <p>Returns HTTP 201 Created with the full generation summary.
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public TransformationRuleGenerationResult generate(
            @Valid @RequestBody GenerateTransformationRulesRequest request) {
        return ruleService.generateRules(request.getReportId());
    }

    /**
     * GET /api/transformation-rules/report/{reportId}
     *
     * <p>List all transformation rules for a compatibility report, ordered by
     * confidence descending. Includes rules of all statuses so the caller
     * can see the complete picture (GENERATED, APPROVED, REJECTED, INVALID).
     */
    @GetMapping("/report/{reportId}")
    public List<TransformationRuleResponse> getRulesByReport(@PathVariable Long reportId) {
        return ruleService.getRulesByReport(reportId)
                .stream()
                .map(TransformationRuleResponse::from)
                .toList();
    }

    /**
     * GET /api/transformation-rules/{ruleId}
     *
     * <p>Retrieve a single transformation rule. Returns 404 if not found.
     */
    @GetMapping("/{ruleId}")
    public TransformationRuleResponse getRuleById(@PathVariable Long ruleId) {
        return TransformationRuleResponse.from(ruleService.getRuleById(ruleId));
    }

    /**
     * GET /api/transformation-rules/endpoint-pair
     *
     * <p>List all rules for a specific (source, target) endpoint pair.
     * This is the primary query used by Phase 10 to retrieve runtime rules.
     *
     * <p>Phase 10 will call this filtered to {@code ruleStatus=APPROVED}.
     * Future: add an optional {@code ruleStatus} query parameter for filtering.
     *
     * @param srcEndpointId source endpoint ID
     * @param tgtEndpointId target endpoint ID
     */
    @GetMapping("/endpoint-pair")
    public List<TransformationRuleResponse> getRulesByEndpointPair(
            @RequestParam Long srcEndpointId,
            @RequestParam Long tgtEndpointId) {
        return ruleService.getRulesByEndpointPair(srcEndpointId, tgtEndpointId)
                .stream()
                .map(TransformationRuleResponse::from)
                .toList();
    }

    /**
     * POST /api/transformation-rules/{ruleId}/approve
     *
     * <p>Approve a GENERATED rule via Phase 9 ApprovalService.
     * Enforces confidence policy and safety re-validation.
     * Idempotent — approving an already-APPROVED rule returns HTTP 200 without error.
     * INVALID rules cannot be approved.
     *
     * <p>A REQUIRES_REVIEW candidate produces a GENERATED rule that requires
     * explicit human approval here. An AUTO_APPROVED candidate's rule may be
     * approved here or by the batch auto-approve endpoint.
     */
    @PostMapping("/{ruleId}/approve")
    public TransformationRuleResponse approveRule(
            @PathVariable Long ruleId,
            @RequestBody(required = false) ApproveRejectRuleRequest request) {
        String reason = request != null ? request.getReason() : null;
        return TransformationRuleResponse.from(approvalService.approveRule(ruleId, reason));
    }

    /**
     * POST /api/transformation-rules/{ruleId}/reject
     *
     * <p>Reject a GENERATED or APPROVED rule via Phase 9 ApprovalService.
     * Idempotent — rejecting an already-REJECTED rule returns HTTP 200.
     * Rejected rules are stored for auditability but must not be executed by Phase 10.
     */
    @PostMapping("/{ruleId}/reject")
    public TransformationRuleResponse rejectRule(
            @PathVariable Long ruleId,
            @RequestBody(required = false) ApproveRejectRuleRequest request) {
        String reason = request != null ? request.getReason() : null;
        return TransformationRuleResponse.from(approvalService.rejectRule(ruleId, reason));
    }
}
