package com.semanticapi.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/compatibility/analyze.
 *
 * <p>Specifies the source and target API versions to compare.
 * Phase 6 analyzes all registered endpoints in the source version
 * against corresponding endpoints in the target version.
 */
public class CompatibilityAnalysisRequest {

    @NotNull(message = "sourceVersionId is required")
    private Long sourceVersionId;

    @NotNull(message = "targetVersionId is required")
    private Long targetVersionId;

    /**
     * Optional: restrict analysis to a specific source endpoint HTTP method + path.
     * If null, all endpoints in the source version are analyzed.
     */
    private Long sourceEndpointId;

    /**
     * Optional: restrict analysis to a specific target endpoint.
     * If null, all endpoints in the target version are analyzed.
     */
    private Long targetEndpointId;

    public Long getSourceVersionId()        { return sourceVersionId; }
    public void setSourceVersionId(Long id) { this.sourceVersionId = id; }
    public Long getTargetVersionId()        { return targetVersionId; }
    public void setTargetVersionId(Long id) { this.targetVersionId = id; }
    public Long getSourceEndpointId()       { return sourceEndpointId; }
    public void setSourceEndpointId(Long id){ this.sourceEndpointId = id; }
    public Long getTargetEndpointId()       { return targetEndpointId; }
    public void setTargetEndpointId(Long id){ this.targetEndpointId = id; }
}
