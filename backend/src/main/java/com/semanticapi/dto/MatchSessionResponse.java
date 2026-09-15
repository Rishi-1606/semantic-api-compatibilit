package com.semanticapi.dto;

import com.semanticapi.model.MatchSession;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for a {@link MatchSession} — includes the session metadata
 * and all candidate pairs, sorted by confidence descending.
 */
public class MatchSessionResponse {

    private Long   id;
    private Long   sourceVersionId;
    private String sourceVersionName;
    private Long   targetVersionId;
    private String targetVersionName;

    private String status;
    private String compatibilityStatus;
    private Double overallScore;

    private String embeddingProvider;
    private String embeddingModel;

    private int    fieldPairCount;
    private String errorMessage;

    private Instant createdAt;
    private Instant completedAt;

    private List<MatchCandidateResponse> candidates;

    public static MatchSessionResponse from(MatchSession s, List<MatchCandidateResponse> candidates) {
        MatchSessionResponse r = new MatchSessionResponse();
        r.id                  = s.getId();
        r.sourceVersionId     = s.getSourceVersion().getId();
        r.sourceVersionName   = s.getSourceVersion().getVersion();
        r.targetVersionId     = s.getTargetVersion().getId();
        r.targetVersionName   = s.getTargetVersion().getVersion();
        r.status              = s.getStatus().name();
        r.compatibilityStatus = s.getCompatibilityStatus() != null
                ? s.getCompatibilityStatus().name() : null;
        r.overallScore        = s.getOverallScore();
        r.embeddingProvider   = s.getEmbeddingProvider();
        r.embeddingModel      = s.getEmbeddingModel();
        r.fieldPairCount      = s.getFieldPairCount();
        r.errorMessage        = s.getErrorMessage();
        r.createdAt           = s.getCreatedAt();
        r.completedAt         = s.getCompletedAt();
        r.candidates          = candidates;
        return r;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long    getId()                 { return id; }
    public Long    getSourceVersionId()    { return sourceVersionId; }
    public String  getSourceVersionName()  { return sourceVersionName; }
    public Long    getTargetVersionId()    { return targetVersionId; }
    public String  getTargetVersionName()  { return targetVersionName; }
    public String  getStatus()             { return status; }
    public String  getCompatibilityStatus(){ return compatibilityStatus; }
    public Double  getOverallScore()       { return overallScore; }
    public String  getEmbeddingProvider()  { return embeddingProvider; }
    public String  getEmbeddingModel()     { return embeddingModel; }
    public int     getFieldPairCount()     { return fieldPairCount; }
    public String  getErrorMessage()       { return errorMessage; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getCompletedAt()        { return completedAt; }
    public List<MatchCandidateResponse> getCandidates() { return candidates; }
}
