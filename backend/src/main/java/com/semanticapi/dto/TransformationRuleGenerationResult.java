package com.semanticapi.dto;

import java.util.List;

/**
 * Summary response returned after {@code POST /api/transformation-rules/generate}.
 *
 * <p>Provides a complete audit of the generation run:
 * how many candidates were processed, how many produced valid rules,
 * how many were excluded (LOW_CONFIDENCE / REJECTED), and the full list of rules.
 *
 * <h2>Counts explained</h2>
 * <ul>
 *   <li>{@code totalCandidatesProcessed} — ALL candidates for the session (all statuses)</li>
 *   <li>{@code rulesGenerated} — rules that passed validation (ruleStatus = GENERATED)</li>
 *   <li>{@code rulesInvalid} — candidates that produced an INVALID rule (stored for audit)</li>
 *   <li>{@code skippedNoOp} — identical path+type pairs — no transformation needed</li>
 *   <li>{@code skippedLowConfidence} — LOW_CONFIDENCE candidates never produce rules</li>
 *   <li>{@code skippedRejected} — REJECTED candidates never produce rules</li>
 * </ul>
 */
public class TransformationRuleGenerationResult {

    private Long   reportId;
    private int    totalCandidatesProcessed;
    private int    rulesGenerated;
    private int    rulesInvalid;
    private int    skippedNoOp;
    private int    skippedLowConfidence;
    private int    skippedRejected;

    /** Full list of all rules (GENERATED + INVALID) produced in this run. */
    private List<TransformationRuleResponse> rules;

    // ── Builder-style setters (returned from service) ─────────

    public void setReportId(Long reportId)                              { this.reportId = reportId; }
    public void setTotalCandidatesProcessed(int n)                      { this.totalCandidatesProcessed = n; }
    public void setRulesGenerated(int n)                                { this.rulesGenerated = n; }
    public void setRulesInvalid(int n)                                  { this.rulesInvalid = n; }
    public void setSkippedNoOp(int n)                                   { this.skippedNoOp = n; }
    public void setSkippedLowConfidence(int n)                          { this.skippedLowConfidence = n; }
    public void setSkippedRejected(int n)                               { this.skippedRejected = n; }
    public void setRules(List<TransformationRuleResponse> rules)        { this.rules = rules; }

    // ── Getters ───────────────────────────────────────────────

    public Long   getReportId()                      { return reportId; }
    public int    getTotalCandidatesProcessed()      { return totalCandidatesProcessed; }
    public int    getRulesGenerated()                { return rulesGenerated; }
    public int    getRulesInvalid()                  { return rulesInvalid; }
    public int    getSkippedNoOp()                   { return skippedNoOp; }
    public int    getSkippedLowConfidence()          { return skippedLowConfidence; }
    public int    getSkippedRejected()               { return skippedRejected; }
    public List<TransformationRuleResponse> getRules() { return rules; }
}
