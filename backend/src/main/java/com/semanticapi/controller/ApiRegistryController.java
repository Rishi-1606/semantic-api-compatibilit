package com.semanticapi.controller;

import com.semanticapi.dto.ApiResponse;
import com.semanticapi.dto.ApiVersionResponse;
import com.semanticapi.dto.CreateApiRequest;
import com.semanticapi.service.ApiRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for API management.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST   /api/apis           — Register a new API</li>
 *   <li>GET    /api/apis           — List all registered APIs</li>
 *   <li>GET    /api/apis/{id}      — Get a specific API by ID</li>
 *   <li>GET    /api/apis/{id}/versions — List all versions of an API</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/apis")
public class ApiRegistryController {

    private final ApiRegistryService registryService;

    public ApiRegistryController(ApiRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * POST /api/apis
     * Register a new API.
     */
    @PostMapping
    public ResponseEntity<ApiResponse> createApi(
            @Valid @RequestBody CreateApiRequest request) {
        ApiResponse response = registryService.createApi(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/apis
     * List all registered APIs, ordered by name.
     */
    @GetMapping
    public ResponseEntity<List<ApiResponse>> listApis() {
        return ResponseEntity.ok(registryService.listApis());
    }

    /**
     * GET /api/apis/{id}
     * Get a specific API by its database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getApi(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getApi(id));
    }

    /**
     * GET /api/apis/{id}/versions
     * List all versions for a specific API.
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<ApiVersionResponse>> getVersionsForApi(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getVersionsForApi(id));
    }
}
