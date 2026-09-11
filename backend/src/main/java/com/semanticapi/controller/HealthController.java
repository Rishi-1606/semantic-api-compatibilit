package com.semanticapi.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom health-check controller for the Semantic API backend.
 *
 * <p>Exposes {@code GET /api/health} for quick service verification.
 * Spring Actuator's health endpoint is also available at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name:semantic-api-backend}")
    private String applicationName;

    @Value("${info.app.version:1.0.0}")
    private String version;

    @Value("${info.app.phase:Phase 1 - Foundation}")
    private String phase;

    /**
     * Returns service status, name, version, and timestamp.
     *
     * @return 200 OK with JSON health payload
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "backend");
        response.put("application", applicationName);
        response.put("version", version);
        response.put("phase", phase);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
