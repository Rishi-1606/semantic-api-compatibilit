package com.semanticapi.service;

import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.Endpoint;
import com.semanticapi.model.SchemaField;
import com.semanticapi.repository.EndpointRepository;
import com.semanticapi.repository.SchemaFieldRepository;
import com.semanticapi.schema.ParsedField;
import com.semanticapi.schema.SchemaAnalyzerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestration service for Phase 4 — Schema Analysis.
 *
 * <p>Coordinates between:
 * <ul>
 *   <li>{@link SchemaAnalyzerService} — pure JSON parsing (no DB)</li>
 *   <li>{@link SchemaFieldRepository}  — persistence layer</li>
 *   <li>{@link EndpointRepository}     — endpoint lookup</li>
 * </ul>
 *
 * <p>Re-analysis is idempotent: existing fields for the same endpoint+schema
 * type are deleted and replaced.
 */
@Service
public class SchemaAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SchemaAnalysisService.class);

    private static final String REQUEST  = "REQUEST";
    private static final String RESPONSE = "RESPONSE";

    private final EndpointRepository    endpointRepository;
    private final SchemaFieldRepository schemaFieldRepository;
    private final SchemaAnalyzerService analyzerService;

    public SchemaAnalysisService(EndpointRepository endpointRepository,
                                 SchemaFieldRepository schemaFieldRepository,
                                 SchemaAnalyzerService analyzerService) {
        this.endpointRepository    = endpointRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.analyzerService       = analyzerService;
    }

    /**
     * Analyze and persist the schemas for a single endpoint.
     *
     * <p>Both the request schema and response schema (if present) are parsed
     * and their fields persisted.  Calling this method a second time replaces
     * any previously stored fields (idempotent).
     *
     * @param endpointId the ID of the endpoint to analyze
     * @return {@link AnalysisResult} containing all extracted fields
     * @throws ResourceNotFoundException if no endpoint with that ID exists
     */
    @Transactional
    public AnalysisResult analyzeEndpoint(Long endpointId) {
        Endpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint", endpointId));

        log.info("Analyzing schemas for endpoint {} ({} {})",
                endpointId, endpoint.getHttpMethod(), endpoint.getPath());

        List<SchemaField> requestFields  = analyzeAndPersist(endpoint, REQUEST,  endpoint.getRequestSchema());
        List<SchemaField> responseFields = analyzeAndPersist(endpoint, RESPONSE, endpoint.getResponseSchema());

        log.info("Endpoint {} analysis complete — {} request fields, {} response fields",
                endpointId, requestFields.size(), responseFields.size());

        return new AnalysisResult(endpointId, requestFields, responseFields);
    }

    /**
     * Retrieve previously persisted schema fields for an endpoint.
     *
     * @param endpointId the ID of the endpoint
     * @return all stored fields; empty list if not yet analyzed
     * @throws ResourceNotFoundException if no endpoint with that ID exists
     */
    @Transactional(readOnly = true)
    public List<SchemaField> getSchemaFields(Long endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new ResourceNotFoundException("Endpoint", endpointId);
        }
        return schemaFieldRepository.findByEndpointIdOrderBySchemaTypeAscFieldPathAsc(endpointId);
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Parse schema JSON, delete old fields, persist new ones, return the saved list.
     */
    private List<SchemaField> analyzeAndPersist(Endpoint endpoint, String schemaType, String schemaJson) {
        // Always clear stale data first (idempotent)
        schemaFieldRepository.deleteByEndpointIdAndSchemaType(endpoint.getId(), schemaType);

        List<ParsedField> parsed = analyzerService.parse(schemaJson);
        if (parsed.isEmpty()) {
            log.debug("No fields extracted for {} schema of endpoint {}", schemaType, endpoint.getId());
            return List.of();
        }

        List<SchemaField> entities = parsed.stream()
                .map(pf -> toEntity(endpoint, schemaType, pf))
                .toList();

        return schemaFieldRepository.saveAll(entities);
    }

    /** Map a {@link ParsedField} to a {@link SchemaField} JPA entity. */
    private SchemaField toEntity(Endpoint endpoint, String schemaType, ParsedField pf) {
        SchemaField sf = new SchemaField();
        sf.setEndpoint(endpoint);
        sf.setSchemaType(schemaType);
        sf.setFieldName(pf.getFieldName());
        sf.setFieldPath(pf.getFieldPath());
        sf.setFieldType(pf.getFieldType());
        sf.setRequired(pf.isRequired());
        sf.setParentPath(pf.getParentPath());

        // Store enum values as description suffix if present
        String desc = pf.getDescription();
        String enumStr = pf.enumValuesAsString();
        if (enumStr != null) {
            desc = (desc != null ? desc + " " : "") + "enum:[" + enumStr + "]";
        }
        sf.setDescription(desc);
        return sf;
    }

    // ── Result record ─────────────────────────────────────────

    /**
     * Holds the result of a full endpoint schema analysis.
     */
    public record AnalysisResult(
        Long             endpointId,
        List<SchemaField> requestFields,
        List<SchemaField> responseFields
    ) {
        public int totalFields() {
            return requestFields.size() + responseFields.size();
        }
    }
}
