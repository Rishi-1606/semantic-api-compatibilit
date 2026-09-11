package com.semanticapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Placeholder controller for the API Registry.
 *
 * <p>All endpoints return {@code 501 Not Implemented} in Phase 1.
 * Full implementation is in Phase 3.
 *
 * <p>Planned endpoints:
 * <ul>
 *   <li>POST   /api/apis         — Register a new API</li>
 *   <li>GET    /api/apis         — List all registered APIs</li>
 *   <li>GET    /api/apis/{id}    — Get a specific API by ID</li>
 *   <li>DELETE /api/apis/{id}    — Delete an API</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/apis")
public class ApiRegistryController {

    private static final Map<String, String> NOT_IMPLEMENTED =
            Map.of("message", "Not implemented yet — coming in Phase 3", "phase", "3");

    @PostMapping
    public ResponseEntity<Map<String, String>> registerApi() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NOT_IMPLEMENTED);
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> listApis() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NOT_IMPLEMENTED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, String>> getApi(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NOT_IMPLEMENTED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteApi(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(NOT_IMPLEMENTED);
    }
}
