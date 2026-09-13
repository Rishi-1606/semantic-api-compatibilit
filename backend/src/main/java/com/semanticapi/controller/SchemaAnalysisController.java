package com.semanticapi.controller;

import com.semanticapi.dto.SchemaFieldResponse;
import com.semanticapi.service.SchemaAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Phase 4 — Schema Analysis.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/endpoints/{id}/analyze  — Parse and persist schema fields</li>
 *   <li>GET  /api/endpoints/{id}/schema   — Retrieve persisted schema fields</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/endpoints/{id}")
public class SchemaAnalysisController {

    private final SchemaAnalysisService analysisService;

    public SchemaAnalysisController(SchemaAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * POST /api/endpoints/{id}/analyze
     *
     * <p>Parse both the request schema and response schema stored on the endpoint
     * and persist the extracted fields into the {@code schema_fields} table.
     *
     * <p>Idempotent — safe to call multiple times.
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeEndpoint(@PathVariable Long id) {
        SchemaAnalysisService.AnalysisResult result = analysisService.analyzeEndpoint(id);

        List<SchemaFieldResponse> requestFields  = result.requestFields().stream()
                .map(SchemaFieldResponse::from)
                .toList();
        List<SchemaFieldResponse> responseFields = result.responseFields().stream()
                .map(SchemaFieldResponse::from)
                .toList();

        return ResponseEntity.ok(Map.of(
            "endpointId",     result.endpointId(),
            "totalFields",    result.totalFields(),
            "requestFields",  requestFields,
            "responseFields", responseFields
        ));
    }

    /**
     * GET /api/endpoints/{id}/schema
     *
     * <p>Retrieve previously analyzed schema fields for an endpoint.
     * Returns an empty list if the endpoint has not yet been analyzed.
     */
    @GetMapping("/schema")
    public ResponseEntity<List<SchemaFieldResponse>> getSchema(@PathVariable Long id) {
        List<SchemaFieldResponse> fields = analysisService.getSchemaFields(id)
                .stream()
                .map(SchemaFieldResponse::from)
                .toList();
        return ResponseEntity.ok(fields);
    }
}
