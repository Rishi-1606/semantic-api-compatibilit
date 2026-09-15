package com.semanticapi.controller;

import com.semanticapi.dto.CompatibilityAnalysisRequest;
import com.semanticapi.dto.MatchSessionResponse;
import com.semanticapi.service.SemanticMatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Phase 6 — Semantic Compatibility Analysis.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /api/compatibility/analyze
 *   Start a new semantic matching session between two API versions.
 *   Body: { sourceVersionId, targetVersionId }
 *   Returns: full session + all candidate pairs
 *
 * GET /api/compatibility/{sessionId}
 *   Retrieve a specific session with all its candidate mappings.
 *
 * GET /api/compatibility/version/{versionId}
 *   List all sessions involving a specific API version (as source or target).
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>This controller only reads/creates analysis sessions.
 * It does NOT approve mappings, generate transformation rules, or forward requests.
 * Those belong to later phases.
 */
@RestController
@RequestMapping("/api/compatibility")
public class SemanticMatchingController {

    private final SemanticMatchingService matchingService;

    public SemanticMatchingController(SemanticMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * POST /api/compatibility/analyze
     *
     * <p>Triggers semantic matching between the source and target API versions.
     * Embeds all Phase 5 NLP-normalized field names and produces confidence-scored
     * candidate mappings.
     */
    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchSessionResponse analyze(@Valid @RequestBody CompatibilityAnalysisRequest request) {
        return matchingService.analyze(request);
    }

    /**
     * GET /api/compatibility/{sessionId}
     *
     * <p>Retrieve a completed matching session and all its candidate pairs,
     * sorted by confidence score descending.
     */
    @GetMapping("/{sessionId}")
    public MatchSessionResponse getSession(@PathVariable Long sessionId) {
        return matchingService.getSession(sessionId);
    }

    /**
     * GET /api/compatibility/version/{versionId}
     *
     * <p>List all matching sessions where this version appears as source or target.
     * Returns session summaries (without candidate lists for brevity).
     */
    @GetMapping("/version/{versionId}")
    public List<MatchSessionResponse> getSessionsForVersion(@PathVariable Long versionId) {
        return matchingService.getSessionsForVersion(versionId);
    }
}
