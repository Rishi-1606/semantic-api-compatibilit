package com.semanticapi.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/reports/analyze.
 *
 * <p>Triggers Phase 7 compatibility analysis on an existing Phase 6 match session.
 */
public class CompatibilityReportRequest {

    @NotNull(message = "matchSessionId is required")
    private Long matchSessionId;

    public Long getMatchSessionId()        { return matchSessionId; }
    public void setMatchSessionId(Long id) { this.matchSessionId = id; }
}
