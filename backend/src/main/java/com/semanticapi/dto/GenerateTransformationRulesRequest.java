package com.semanticapi.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/transformation-rules/generate}.
 *
 * <p>Triggers Phase 8 rule generation for all accepted (AUTO_APPROVED and
 * REQUIRES_REVIEW) MatchCandidates linked to the given compatibility report.
 */
public class GenerateTransformationRulesRequest {

    @NotNull(message = "reportId must not be null")
    private Long reportId;

    public Long getReportId()              { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
}
