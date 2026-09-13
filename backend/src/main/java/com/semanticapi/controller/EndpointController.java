package com.semanticapi.controller;

import com.semanticapi.dto.CreateEndpointRequest;
import com.semanticapi.dto.EndpointResponse;
import com.semanticapi.service.ApiRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for endpoint management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/endpoints                       — Register an endpoint under an API version</li>
 *   <li>GET  /api/endpoints/{id}                  — Get a specific endpoint by ID</li>
 *   <li>GET  /api/versions/{versionId}/endpoints  — List all endpoints for a version</li>
 * </ul>
 */
@RestController
public class EndpointController {

    private final ApiRegistryService registryService;

    public EndpointController(ApiRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * POST /api/endpoints
     * Register a new endpoint under an API version.
     */
    @PostMapping("/api/endpoints")
    public ResponseEntity<EndpointResponse> createEndpoint(
            @Valid @RequestBody CreateEndpointRequest request) {
        EndpointResponse response = registryService.createEndpoint(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/endpoints/{id}
     * Get a specific endpoint by its database ID.
     */
    @GetMapping("/api/endpoints/{id}")
    public ResponseEntity<EndpointResponse> getEndpoint(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getEndpoint(id));
    }

    /**
     * GET /api/versions/{versionId}/endpoints
     * List all endpoints registered under a specific API version.
     */
    @GetMapping("/api/versions/{versionId}/endpoints")
    public ResponseEntity<List<EndpointResponse>> getEndpointsForVersion(
            @PathVariable Long versionId) {
        return ResponseEntity.ok(registryService.getEndpointsForVersion(versionId));
    }
}
