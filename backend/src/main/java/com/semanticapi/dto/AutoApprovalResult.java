package com.semanticapi.dto;

import java.util.List;

/**
 * Result of a Phase 9 batch auto-approval operation for one compatibility report.
 *
 * <p>Only rules that pass ALL of the following are auto-approved:
 * <ul>
 *   <li>candidateStatus == AUTO_APPROVED</li>
 *   <li>confidence ≥ configured auto-approve threshold (default 0.90)</li>
 *   <li>ruleStatus == GENERATED (not already APPROVED/REJECTED/INVALID)</li>
 *   <li>Phase 9 safety re-validation passes with zero errors</li>
 * </ul>
 */
public class AutoApprovalResult {

    private Long                         reportId;

    /** Total GENERATED rules found for this report at the time of the call. */
    private int                          totalGenerated;

    /** Rules auto-approved in this batch. */
    private int                          autoApproved;

    /**
     * Rules skipped because candidateStatus is REQUIRES_REVIEW
     * (human approval is mandatory for these).
     */
    private int                          requiresHumanReview;

    /**
     * Rules skipped because confidence is below the minimum approval threshold
     * (spec § 7: below 70% → cannot be approved at all).
     */
    private int                          skippedLowConfidence;

    /**
     * Rules skipped because confidence is above the minimum but below the
     * auto-approval threshold (70%–89%): require human review, not auto-approval.
     */
    private int                          skippedRequiresReview;

    /**
     * Rules skipped because ruleStatus == INVALID or re-validation failed.
     * These rules need to be re-generated with corrected semantic mappings.
     */
    private int                          skippedInvalidOrFailed;

    /**
     * The auto-approved rules, in confidence-descending order.
     * Includes only rules that were newly approved in this call.
     */
    private List<TransformationRuleResponse> approvedRules;

    // ── Getters / Setters ────────────────────────────────────

    public Long    getReportId()               { return reportId; }
    public void    setReportId(Long id)        { this.reportId = id; }

    public int     getTotalGenerated()         { return totalGenerated; }
    public void    setTotalGenerated(int n)    { this.totalGenerated = n; }

    public int     getAutoApproved()           { return autoApproved; }
    public void    setAutoApproved(int n)      { this.autoApproved = n; }

    public int     getRequiresHumanReview()    { return requiresHumanReview; }
    public void    setRequiresHumanReview(int n) { this.requiresHumanReview = n; }

    public int     getSkippedLowConfidence()   { return skippedLowConfidence; }
    public void    setSkippedLowConfidence(int n) { this.skippedLowConfidence = n; }

    public int     getSkippedRequiresReview()  { return skippedRequiresReview; }
    public void    setSkippedRequiresReview(int n) { this.skippedRequiresReview = n; }

    public int     getSkippedInvalidOrFailed() { return skippedInvalidOrFailed; }
    public void    setSkippedInvalidOrFailed(int n) { this.skippedInvalidOrFailed = n; }

    public List<TransformationRuleResponse> getApprovedRules()              { return approvedRules; }
    public void    setApprovedRules(List<TransformationRuleResponse> rules) { this.approvedRules = rules; }
}
