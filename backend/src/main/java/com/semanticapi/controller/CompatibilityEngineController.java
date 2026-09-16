package com.semanticapi.controller;

import com.semanticapi.dto.*;
import com.semanticapi.model.CompatibilityReport;
import com.semanticapi.model.EndpointCompatibilityResult;
import com.semanticapi.service.CompatibilityEngineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Phase 7 — Compatibility Engine.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /api/reports/analyze
 *   Trigger Phase 7 compatibility analysis on an existing Phase 6 match session.
 *   Body: { "matchSessionId": 1 }
 *   Returns: full compatibility report with per-endpoint breakdown.
 *
 * GET /api/reports/{reportId}
 *   Retrieve a specific compatibility report with all endpoint results.
 *
 * GET /api/reports/{reportId}/endpoints
 *   Retrieve just the endpoint-level compatibility results for a report.
 *
 * GET /api/reports/version/{versionId}
 *   List all compatibility reports involving a specific API version.
 *
 * GET /api/reports/session/{sessionId}
 *   List all compatibility reports produced from a specific match session.
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>This controller produces read-only compatibility analysis results.
 * It does NOT generate transformation rules, approve candidates, or forward requests.
 */
@RestController
@RequestMapping("/api/reports")
public class CompatibilityEngineController {

    private final CompatibilityEngineService engineService;

    public CompatibilityEngineController(CompatibilityEngineService engineService) {
        this.engineService = engineService;
    }

    /**
     * POST /api/reports/analyze
     *
     * <p>Runs Phase 7 compatibility analysis on a completed Phase 6 match session.
     * Groups Phase 6 candidates by endpoint pair, computes weighted coverage scores,
     * and classifies COMPATIBLE / PARTIALLY_COMPATIBLE / INCOMPATIBLE.
     */
    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public CompatibilityReportResponse analyze(@Valid @RequestBody CompatibilityReportRequest request) {
        CompatibilityReport report = engineService.analyzeSession(request.getMatchSessionId());
        List<EndpointCompatibilityResult> epResults =
                engineService.getEndpointResults(report.getId());
        List<EndpointCompatibilityResultResponse> epDtos =
                epResults.stream().map(EndpointCompatibilityResultResponse::from).toList();
        return CompatibilityReportResponse.from(report, epDtos);
    }

    /**
     * GET /api/reports/{reportId}
     *
     * <p>Retrieve a specific compatibility report with its full per-endpoint breakdown.
     * The response includes matched/unmatched field paths and a human-readable explanation.
     */
    @GetMapping("/{reportId}")
    public CompatibilityReportResponse getReport(@PathVariable Long reportId) {
        CompatibilityReport report = engineService.getReport(reportId);
        List<EndpointCompatibilityResult> epResults = engineService.getEndpointResults(reportId);
        List<EndpointCompatibilityResultResponse> epDtos =
                epResults.stream().map(EndpointCompatibilityResultResponse::from).toList();
        return CompatibilityReportResponse.from(report, epDtos);
    }

    /**
     * GET /api/reports/{reportId}/endpoints
     *
     * <p>Retrieve only the per-endpoint compatibility results for a report.
     * Useful for building endpoint-by-endpoint UI views.
     */
    @GetMapping("/{reportId}/endpoints")
    public List<EndpointCompatibilityResultResponse> getEndpointResults(@PathVariable Long reportId) {
        return engineService.getEndpointResults(reportId)
                .stream()
                .map(EndpointCompatibilityResultResponse::from)
                .toList();
    }

    /**
     * GET /api/reports/version/{versionId}
     *
     * <p>List all compatibility reports where the given API version appears
     * as source or target (summary only, no endpoint breakdown).
     */
    @GetMapping("/version/{versionId}")
    public List<CompatibilityReportResponse> getReportsForVersion(@PathVariable Long versionId) {
        return engineService.getReportsForVersion(versionId)
                .stream()
                .map(r -> CompatibilityReportResponse.from(r, List.of()))
                .toList();
    }

    /**
     * GET /api/reports/session/{sessionId}
     *
     * <p>List all compatibility reports produced from a specific Phase 6 session.
     */
    @GetMapping("/session/{sessionId}")
    public List<CompatibilityReportResponse> getReportsForSession(@PathVariable Long sessionId) {
        return engineService.getReportsForSession(sessionId)
                .stream()
                .map(r -> CompatibilityReportResponse.from(r, List.of()))
                .toList();
    }
}
