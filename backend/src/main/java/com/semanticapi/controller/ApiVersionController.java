package com.semanticapi.controller;

import com.semanticapi.dto.ApiVersionResponse;
import com.semanticapi.dto.CreateApiVersionRequest;
import com.semanticapi.service.ApiRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for API version management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/versions      — Add a new version to an existing API</li>
 *   <li>GET  /api/versions/{id} — Get a specific version by ID</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/versions")
public class ApiVersionController {

    private final ApiRegistryService registryService;

    public ApiVersionController(ApiRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * POST /api/versions
     * Register a new version for an existing API.
     */
    @PostMapping
    public ResponseEntity<ApiVersionResponse> createVersion(
            @Valid @RequestBody CreateApiVersionRequest request) {
        ApiVersionResponse response = registryService.createVersion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/versions/{id}
     * Get a specific API version by its database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiVersionResponse> getVersion(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getVersion(id));
    }
}
