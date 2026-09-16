package com.semanticapi.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.semanticapi.model.EndpointCompatibilityResult;

import java.time.Instant;

/**
 * Response DTO for a single endpoint-level compatibility result.
 *
 * <p>Exposes:
 * <ul>
 *   <li>Endpoint paths (source and target)</li>
 *   <li>Classification (COMPATIBLE / PARTIALLY_COMPATIBLE / INCOMPATIBLE)</li>
 *   <li>Score (Phase 7 weighted field-coverage score)</li>
 *   <li>Field counts (required + optional, matched + unmatched)</li>
 *   <li>Matched field paths (JSON array: "source → target")</li>
 *   <li>Unmatched source paths (JSON array)</li>
 *   <li>Unmatched target paths (JSON array)</li>
 *   <li>Human-readable explanation</li>
 * </ul>
 */
public class EndpointCompatibilityResultResponse {

    private Long   id;
    private Long   reportId;

    private Long   sourceEndpointId;
    private String sourceEndpointPath;

    private Long   targetEndpointId;
    private String targetEndpointPath;

    private String compatibilityStatus;
    private double compatibilityScore;

    private int    totalSourceFields;
    private int    matchedRequiredFields;
    private int    unmatchedRequiredFields;
    private int    matchedOptionalFields;
    private int    unmatchedOptionalFields;

    /** Serialised JSON array — rendered as raw JSON in response. */
    @JsonRawValue
    private String matchedFieldPaths;

    @JsonRawValue
    private String unmatchedSourcePaths;

    @JsonRawValue
    private String unmatchedTargetPaths;

    private String  explanation;
    private Instant createdAt;

    public static EndpointCompatibilityResultResponse from(EndpointCompatibilityResult r) {
        EndpointCompatibilityResultResponse dto = new EndpointCompatibilityResultResponse();
        dto.id                      = r.getId();
        dto.reportId                = r.getReport().getId();
        dto.sourceEndpointId        = r.getSourceEndpoint().getId();
        dto.sourceEndpointPath      = r.getSourceEndpointPath();
        dto.targetEndpointId        = r.getTargetEndpoint().getId();
        dto.targetEndpointPath      = r.getTargetEndpointPath();
        dto.compatibilityStatus     = r.getCompatibilityStatus().name();
        dto.compatibilityScore      = r.getCompatibilityScore();
        dto.totalSourceFields       = r.getTotalSourceFields();
        dto.matchedRequiredFields   = r.getMatchedRequiredFields();
        dto.unmatchedRequiredFields = r.getUnmatchedRequiredFields();
        dto.matchedOptionalFields   = r.getMatchedOptionalFields();
        dto.unmatchedOptionalFields = r.getUnmatchedOptionalFields();
        dto.matchedFieldPaths       = r.getMatchedFieldPaths()      != null ? r.getMatchedFieldPaths()      : "[]";
        dto.unmatchedSourcePaths    = r.getUnmatchedSourcePaths()   != null ? r.getUnmatchedSourcePaths()   : "[]";
        dto.unmatchedTargetPaths    = r.getUnmatchedTargetPaths()   != null ? r.getUnmatchedTargetPaths()   : "[]";
        dto.explanation             = r.getExplanation();
        dto.createdAt               = r.getCreatedAt();
        return dto;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long    getId()                      { return id; }
    public Long    getReportId()                { return reportId; }
    public Long    getSourceEndpointId()        { return sourceEndpointId; }
    public String  getSourceEndpointPath()      { return sourceEndpointPath; }
    public Long    getTargetEndpointId()        { return targetEndpointId; }
    public String  getTargetEndpointPath()      { return targetEndpointPath; }
    public String  getCompatibilityStatus()     { return compatibilityStatus; }
    public double  getCompatibilityScore()      { return compatibilityScore; }
    public int     getTotalSourceFields()       { return totalSourceFields; }
    public int     getMatchedRequiredFields()   { return matchedRequiredFields; }
    public int     getUnmatchedRequiredFields() { return unmatchedRequiredFields; }
    public int     getMatchedOptionalFields()   { return matchedOptionalFields; }
    public int     getUnmatchedOptionalFields() { return unmatchedOptionalFields; }
    public String  getMatchedFieldPaths()       { return matchedFieldPaths; }
    public String  getUnmatchedSourcePaths()    { return unmatchedSourcePaths; }
    public String  getUnmatchedTargetPaths()    { return unmatchedTargetPaths; }
    public String  getExplanation()             { return explanation; }
    public Instant getCreatedAt()               { return createdAt; }
}
