package com.semanticapi.controller;

import com.semanticapi.dto.TransformTestRequest;
import com.semanticapi.dto.TransformTestResponse;
import com.semanticapi.service.TransformTestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Phase 12 — Transformation Tester.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /api/transform/test
 *   Apply a single transformation rule to sample JSON in-process.
 *   Body: { "ruleId": 5, "inputJson": "{\"name\":\"Rishi\"}" }
 *   Returns: TransformTestResponse (HTTP 200 always, success flag in body)
 *   Errors: 400 (invalid request / bad JSON), 404 (rule not found)
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>This endpoint is a safe read-only preview facility.
 * It does NOT forward requests to external APIs, does NOT execute approved rules
 * in production, and does NOT replace the Phase 10 Node.js runtime gateway.
 * Any transformation status (GENERATED, APPROVED, REJECTED, INVALID) can be tested.
 */
@RestController
@RequestMapping("/api/transform")
public class TransformTestController {

    private final TransformTestService transformTestService;

    public TransformTestController(TransformTestService transformTestService) {
        this.transformTestService = transformTestService;
    }

    /**
     * POST /api/transform/test
     *
     * <p>Applies a single transformation rule's operation to the provided sample JSON.
     * Returns HTTP 200 with a success flag in the body — never throws 5xx for
     * transformation logic errors (unsupported operations are described in errorMessage).
     *
     * @param request contains ruleId and inputJson
     * @return transformation preview result
     */
    @PostMapping("/test")
    public ResponseEntity<TransformTestResponse> testTransformation(
            @Valid @RequestBody TransformTestRequest request) {
        TransformTestResponse result = transformTestService.test(request);
        return ResponseEntity.ok(result);
    }
}
