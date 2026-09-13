package com.semanticapi.service;

import com.semanticapi.dto.*;
import com.semanticapi.exception.ConflictException;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.Api;
import com.semanticapi.model.ApiVersion;
import com.semanticapi.model.Endpoint;
import com.semanticapi.repository.ApiRepository;
import com.semanticapi.repository.ApiVersionRepository;
import com.semanticapi.repository.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for the API Registry (Phase 3).
 *
 * <p>Handles:
 * <ul>
 *   <li>API creation and lookup</li>
 *   <li>API version registration and lookup</li>
 *   <li>Endpoint registration and lookup</li>
 * </ul>
 *
 * <p>All business rules and duplicate detection live here,
 * keeping the controllers thin.
 */
@Service
@Transactional
public class ApiRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ApiRegistryService.class);

    private final ApiRepository        apiRepository;
    private final ApiVersionRepository apiVersionRepository;
    private final EndpointRepository   endpointRepository;

    public ApiRegistryService(ApiRepository apiRepository,
                              ApiVersionRepository apiVersionRepository,
                              EndpointRepository endpointRepository) {
        this.apiRepository        = apiRepository;
        this.apiVersionRepository = apiVersionRepository;
        this.endpointRepository   = endpointRepository;
    }

    // ══════════════════════════════════════════════════════════
    // API operations
    // ══════════════════════════════════════════════════════════

    /**
     * Register a new API.
     *
     * @throws ConflictException if an API with the same name already exists
     */
    public ApiResponse createApi(CreateApiRequest request) {
        log.info("Registering API: {}", request.getName());

        if (apiRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException(
                "An API named '" + request.getName() + "' already exists");
        }

        Api api = new Api(request.getName().trim(), request.getDescription());
        api = apiRepository.save(api);

        log.info("API registered — id={}, name={}", api.getId(), api.getName());
        return ApiResponse.from(api);
    }

    /**
     * List all registered APIs.
     */
    @Transactional(readOnly = true)
    public List<ApiResponse> listApis() {
        return apiRepository.findAllByOrderByNameAsc()
                .stream()
                .map(ApiResponse::from)
                .toList();
    }

    /**
     * Get a single API by id (with its versions eagerly resolved for the response).
     *
     * @throws ResourceNotFoundException if the API does not exist
     */
    @Transactional(readOnly = true)
    public ApiResponse getApi(Long id) {
        Api api = findApiById(id);
        return ApiResponse.from(api);
    }

    // ══════════════════════════════════════════════════════════
    // API Version operations
    // ══════════════════════════════════════════════════════════

    /**
     * Add a new version to an existing API.
     *
     * @throws ResourceNotFoundException if the API does not exist
     * @throws ConflictException         if the version already exists for that API
     */
    public ApiVersionResponse createVersion(CreateApiVersionRequest request) {
        log.info("Registering version '{}' for apiId={}", request.getVersion(), request.getApiId());

        Api api = findApiById(request.getApiId());

        if (apiVersionRepository.existsByApiIdAndVersionIgnoreCase(
                request.getApiId(), request.getVersion())) {
            throw new ConflictException(
                "Version '" + request.getVersion() + "' already exists for API '" + api.getName() + "'");
        }

        ApiVersion version = new ApiVersion();
        version.setApi(api);
        version.setVersion(request.getVersion().trim());
        version.setBaseUrl(request.getBaseUrl());
        version.setDescription(request.getDescription());

        version = apiVersionRepository.save(version);

        log.info("Version registered — id={}, api={}, version={}",
                version.getId(), api.getName(), version.getVersion());
        return ApiVersionResponse.from(version);
    }

    /**
     * Get all versions for an API.
     *
     * @throws ResourceNotFoundException if the API does not exist
     */
    @Transactional(readOnly = true)
    public List<ApiVersionResponse> getVersionsForApi(Long apiId) {
        findApiById(apiId); // validates the API exists
        return apiVersionRepository.findByApiIdOrderByVersionAsc(apiId)
                .stream()
                .map(ApiVersionResponse::from)
                .toList();
    }

    /**
     * Get a single version by its id.
     *
     * @throws ResourceNotFoundException if the version does not exist
     */
    @Transactional(readOnly = true)
    public ApiVersionResponse getVersion(Long id) {
        ApiVersion version = findVersionById(id);
        return ApiVersionResponse.from(version);
    }

    // ══════════════════════════════════════════════════════════
    // Endpoint operations
    // ══════════════════════════════════════════════════════════

    /**
     * Register a new endpoint under an API version.
     *
     * @throws ResourceNotFoundException if the API version does not exist
     * @throws ConflictException         if the same method+path already exists for that version
     */
    public EndpointResponse createEndpoint(CreateEndpointRequest request) {
        String method = request.getHttpMethod().toUpperCase();
        log.info("Registering endpoint {} {} for versionId={}",
                method, request.getPath(), request.getApiVersionId());

        ApiVersion version = findVersionById(request.getApiVersionId());

        if (endpointRepository.existsByApiVersionIdAndHttpMethodAndPath(
                request.getApiVersionId(), method, request.getPath())) {
            throw new ConflictException(
                "Endpoint " + method + " " + request.getPath()
                + " already exists for version '" + version.getVersion() + "'");
        }

        Endpoint endpoint = new Endpoint();
        endpoint.setApiVersion(version);
        endpoint.setHttpMethod(method);
        endpoint.setPath(request.getPath().trim());
        endpoint.setDescription(request.getDescription());
        endpoint.setRequestSchema(request.getRequestSchema());
        endpoint.setResponseSchema(request.getResponseSchema());

        endpoint = endpointRepository.save(endpoint);

        log.info("Endpoint registered — id={}, {} {}", endpoint.getId(), method, endpoint.getPath());
        return EndpointResponse.from(endpoint);
    }

    /**
     * Get all endpoints for an API version.
     *
     * @throws ResourceNotFoundException if the version does not exist
     */
    @Transactional(readOnly = true)
    public List<EndpointResponse> getEndpointsForVersion(Long versionId) {
        findVersionById(versionId); // validates version exists
        return endpointRepository.findByApiVersionIdOrderByPathAsc(versionId)
                .stream()
                .map(EndpointResponse::from)
                .toList();
    }

    /**
     * Get a single endpoint by its id.
     *
     * @throws ResourceNotFoundException if the endpoint does not exist
     */
    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(Long id) {
        Endpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint", id));
        return EndpointResponse.from(endpoint);
    }

    // ══════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════

    private Api findApiById(Long id) {
        return apiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API", id));
    }

    private ApiVersion findVersionById(Long id) {
        return apiVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API version", id));
    }
}
