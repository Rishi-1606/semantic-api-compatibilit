package com.semanticapi.dto;

import java.util.List;

/**
 * Dry-run Phase 9 safety validation report for a single transformation rule.
 *
 * <p>This is returned by the {@code GET /api/approval/rules/{id}/validate} endpoint.
 * It does NOT change the rule's status — it only reports whether the rule
 * would pass approval if submitted now.
 */
public class ApprovalValidationReport {

    private Long         ruleId;

    /** Current status of the rule. */
    private String       ruleStatus;

    /** candidateStatus preserved from Phase 6. */
    private String       candidateStatus;

    /** Confidence score preserved from Phase 6. */
    private double       confidence;

    /** Operation type of this rule. */
    private String       operation;

    /**
     * True if the rule passes all Phase 9 safety checks and the confidence
     * policy, and would be approved if submitted now.
     */
    private boolean      eligible;

    /**
     * Human-readable list of reasons why the rule cannot be approved.
     * Empty when {@code eligible == true}.
     *
     * <p>Examples:
     * <ul>
     *   <li>"Rule is INVALID and cannot be approved"</li>
     *   <li>"Confidence 0.65 is below minimum threshold 0.70"</li>
     *   <li>"candidateStatus REQUIRES_REVIEW — human approval required, not auto-approval"</li>
     *   <li>"TYPE_CONVERSION involving object or array types is unsafe"</li>
     * </ul>
     */
    private List<String> blockingReasons;

    // ── Getters / Setters ────────────────────────────────────

    public Long         getRuleId()                              { return ruleId; }
    public void         setRuleId(Long id)                       { this.ruleId = id; }
    public String       getRuleStatus()                          { return ruleStatus; }
    public void         setRuleStatus(String s)                  { this.ruleStatus = s; }
    public String       getCandidateStatus()                     { return candidateStatus; }
    public void         setCandidateStatus(String s)             { this.candidateStatus = s; }
    public double       getConfidence()                          { return confidence; }
    public void         setConfidence(double c)                  { this.confidence = c; }
    public String       getOperation()                           { return operation; }
    public void         setOperation(String op)                  { this.operation = op; }
    public boolean      isEligible()                             { return eligible; }
    public void         setEligible(boolean b)                   { this.eligible = b; }
    public List<String> getBlockingReasons()                     { return blockingReasons; }
    public void         setBlockingReasons(List<String> reasons) { this.blockingReasons = reasons; }
}
