package com.semanticapi.dto;

import com.semanticapi.model.TransformationRule;

import java.time.Instant;

/**
 * Response DTO for a single {@link TransformationRule}.
 *
 * <p>Contains every field required for Phase 10 rule execution.
 * No hidden context is needed beyond what is included here.
 *
 * <h2>Key fields for Phase 10</h2>
 * <ul>
 *   <li>{@code operation} — the exact transformation type</li>
 *   <li>{@code sourcePath} / {@code targetPath} — primary field paths</li>
 *   <li>{@code sourcePaths} / {@code targetPaths} — additional paths for COMBINE/SPLIT</li>
 *   <li>{@code parameters} — JSON with all operation-specific config (separator, mappings, etc.)</li>
 *   <li>{@code ruleStatus} — only APPROVED rules may be executed</li>
 * </ul>
 */
public class TransformationRuleResponse {

    private Long    id;
    private Long    reportId;
    private Long    matchCandidateId;

    private Long    sourceEndpointId;
    private Long    targetEndpointId;
    private String  sourceEndpointPath;
    private String  targetEndpointPath;

    private String  sourcePath;
    private String  targetPath;
    private String  sourcePaths;   // JSON array — COMBINE only
    private String  targetPaths;   // JSON array — SPLIT only

    private String  operation;
    private String  sourceType;
    private String  targetType;
    private double  confidence;
    private String  candidateStatus;
    private String  ruleStatus;

    private String  parameters;        // JSON object — operation-specific config
    private String  rejectionReason;
    private String  validationErrors;

    private Instant createdAt;
    private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────

    public static TransformationRuleResponse from(TransformationRule r) {
        TransformationRuleResponse dto = new TransformationRuleResponse();
        dto.id                  = r.getId();
        dto.reportId            = r.getReport().getId();
        dto.matchCandidateId    = r.getMatchCandidate() != null ? r.getMatchCandidate().getId() : null;
        dto.sourceEndpointId    = r.getSourceEndpoint().getId();
        dto.targetEndpointId    = r.getTargetEndpoint().getId();
        dto.sourceEndpointPath  = r.getSourceEndpointPath();
        dto.targetEndpointPath  = r.getTargetEndpointPath();
        dto.sourcePath          = r.getSourcePath();
        dto.targetPath          = r.getTargetPath();
        dto.sourcePaths         = r.getSourcePaths();
        dto.targetPaths         = r.getTargetPaths();
        dto.operation           = r.getOperation().name();
        dto.sourceType          = r.getSourceType();
        dto.targetType          = r.getTargetType();
        dto.confidence          = r.getConfidence();
        dto.candidateStatus     = r.getCandidateStatus();
        dto.ruleStatus          = r.getRuleStatus().name();
        dto.parameters          = r.getParameters();
        dto.rejectionReason     = r.getRejectionReason();
        dto.validationErrors    = r.getValidationErrors();
        dto.createdAt           = r.getCreatedAt();
        dto.updatedAt           = r.getUpdatedAt();
        return dto;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long    getId()                 { return id; }
    public Long    getReportId()           { return reportId; }
    public Long    getMatchCandidateId()   { return matchCandidateId; }
    public Long    getSourceEndpointId()   { return sourceEndpointId; }
    public Long    getTargetEndpointId()   { return targetEndpointId; }
    public String  getSourceEndpointPath() { return sourceEndpointPath; }
    public String  getTargetEndpointPath() { return targetEndpointPath; }
    public String  getSourcePath()         { return sourcePath; }
    public String  getTargetPath()         { return targetPath; }
    public String  getSourcePaths()        { return sourcePaths; }
    public String  getTargetPaths()        { return targetPaths; }
    public String  getOperation()          { return operation; }
    public String  getSourceType()         { return sourceType; }
    public String  getTargetType()         { return targetType; }
    public double  getConfidence()         { return confidence; }
    public String  getCandidateStatus()    { return candidateStatus; }
    public String  getRuleStatus()         { return ruleStatus; }
    public String  getParameters()         { return parameters; }
    public String  getRejectionReason()    { return rejectionReason; }
    public String  getValidationErrors()   { return validationErrors; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }
}
