package com.semanticapi.dto;

import com.semanticapi.model.CompatibilityReport;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a {@link CompatibilityReport}.
 *
 * <p>Includes overall classification, score, aggregate field counts,
 * the human-readable explanation, and optionally the per-endpoint breakdown.
 *
 * <h2>Score explained</h2>
 * <p>The {@code overallScore} is a Phase 7 weighted field-coverage score,
 * distinct from Phase 6's cosine similarity.
 * See {@link com.semanticapi.service.CompatibilityEngineService} for the formula.
 */
public class CompatibilityReportResponse {

    private Long   id;
    private Long   matchSessionId;
    private Long   sourceVersionId;
    private String sourceVersionName;
    private Long   targetVersionId;
    private String targetVersionName;

    private String compatibilityStatus;
    private double overallScore;

    // Field-level counts
    private int    totalSourceFields;
    private int    matchedFields;
    private int    unmatchedRequiredFields;
    private int    autoApprovedCount;
    private int    requiresReviewCount;
    private int    lowConfidenceCount;

    private String explanation;
    private Instant createdAt;

    /** Per-endpoint breakdown (populated on full GET, empty on list endpoints). */
    private List<EndpointCompatibilityResultResponse> endpointResults;

    public static CompatibilityReportResponse from(CompatibilityReport r,
                                                    List<EndpointCompatibilityResultResponse> epResults) {
        CompatibilityReportResponse dto = new CompatibilityReportResponse();
        dto.id                      = r.getId();
        dto.matchSessionId          = r.getMatchSession().getId();
        dto.sourceVersionId         = r.getSourceVersion().getId();
        dto.sourceVersionName       = r.getSourceVersion().getVersion();
        dto.targetVersionId         = r.getTargetVersion().getId();
        dto.targetVersionName       = r.getTargetVersion().getVersion();
        dto.compatibilityStatus     = r.getCompatibilityStatus().name();
        dto.overallScore            = r.getOverallScore();
        dto.totalSourceFields       = r.getTotalSourceFields();
        dto.matchedFields           = r.getMatchedFields();
        dto.unmatchedRequiredFields = r.getUnmatchedRequiredFields();
        dto.autoApprovedCount       = r.getAutoApprovedCount();
        dto.requiresReviewCount     = r.getRequiresReviewCount();
        dto.lowConfidenceCount      = r.getLowConfidenceCount();
        dto.explanation             = r.getExplanation();
        dto.createdAt               = r.getCreatedAt();
        dto.endpointResults         = epResults;
        return dto;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long    getId()                      { return id; }
    public Long    getMatchSessionId()          { return matchSessionId; }
    public Long    getSourceVersionId()         { return sourceVersionId; }
    public String  getSourceVersionName()       { return sourceVersionName; }
    public Long    getTargetVersionId()         { return targetVersionId; }
    public String  getTargetVersionName()       { return targetVersionName; }
    public String  getCompatibilityStatus()     { return compatibilityStatus; }
    public double  getOverallScore()            { return overallScore; }
    public int     getTotalSourceFields()       { return totalSourceFields; }
    public int     getMatchedFields()           { return matchedFields; }
    public int     getUnmatchedRequiredFields() { return unmatchedRequiredFields; }
    public int     getAutoApprovedCount()       { return autoApprovedCount; }
    public int     getRequiresReviewCount()     { return requiresReviewCount; }
    public int     getLowConfidenceCount()      { return lowConfidenceCount; }
    public String  getExplanation()             { return explanation; }
    public Instant getCreatedAt()               { return createdAt; }
    public List<EndpointCompatibilityResultResponse> getEndpointResults() { return endpointResults; }
}
