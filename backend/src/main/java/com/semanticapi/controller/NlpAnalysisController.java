package com.semanticapi.controller;

import com.semanticapi.dto.NlpFieldResultResponse;
import com.semanticapi.service.NlpAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Phase 5 — NLP Analysis.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/endpoints/{id}/nlp  — Run NLP preprocessing on all schema fields</li>
 *   <li>GET  /api/endpoints/{id}/nlp  — Retrieve persisted NLP results</li>
 * </ul>
 *
 * <p>Prerequisites: Phase 4 schema analysis ({@code POST /api/endpoints/{id}/analyze})
 * must be run before NLP analysis can produce meaningful results.
 */
@RestController
@RequestMapping("/api/endpoints/{id}/nlp")
public class NlpAnalysisController {

    private final NlpAnalysisService nlpService;

    public NlpAnalysisController(NlpAnalysisService nlpService) {
        this.nlpService = nlpService;
    }

    /**
     * POST /api/endpoints/{id}/nlp
     *
     * <p>Run NLP preprocessing on all Phase 4 schema fields for this endpoint
     * and persist the results.  Idempotent — safe to call multiple times.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> analyzeNlp(@PathVariable Long id) {
        List<NlpFieldResultResponse> results = nlpService.analyzeEndpoint(id)
                .stream()
                .map(NlpFieldResultResponse::from)
                .toList();

        return ResponseEntity.ok(Map.of(
            "endpointId", id,
            "processedFields", results.size(),
            "results", results
        ));
    }

    /**
     * GET /api/endpoints/{id}/nlp
     *
     * <p>Retrieve previously analyzed NLP results for an endpoint.
     * Returns an empty list if NLP analysis has not yet been run.
     */
    @GetMapping
    public ResponseEntity<List<NlpFieldResultResponse>> getNlpResults(@PathVariable Long id) {
        List<NlpFieldResultResponse> results = nlpService.getNlpResults(id)
                .stream()
                .map(NlpFieldResultResponse::from)
                .toList();
        return ResponseEntity.ok(results);
    }
}
