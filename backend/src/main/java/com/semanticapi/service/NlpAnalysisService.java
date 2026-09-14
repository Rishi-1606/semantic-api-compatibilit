package com.semanticapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.NlpFieldResult;
import com.semanticapi.model.SchemaField;
import com.semanticapi.nlp.NlpPipelineService;
import com.semanticapi.nlp.NlpResult;
import com.semanticapi.repository.EndpointRepository;
import com.semanticapi.repository.NlpFieldResultRepository;
import com.semanticapi.repository.SchemaFieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestration service for Phase 5 — NLP Analysis.
 *
 * <p>Reads schema fields produced by Phase 4 ({@link SchemaAnalysisService}),
 * runs each field name through the {@link NlpPipelineService}, and persists
 * the results in the {@code nlp_field_results} table.
 *
 * <p>Re-running is idempotent: existing NLP results for an endpoint are
 * deleted and replaced.
 *
 * <p>Important: Phase 4 schema analysis must be run before Phase 5 NLP
 * analysis.  If no schema fields exist for the endpoint, the NLP analysis
 * returns an empty result with a hint to run schema analysis first.
 */
@Service
public class NlpAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(NlpAnalysisService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EndpointRepository       endpointRepository;
    private final SchemaFieldRepository    schemaFieldRepository;
    private final NlpFieldResultRepository nlpRepository;
    private final NlpPipelineService       pipeline;

    public NlpAnalysisService(EndpointRepository endpointRepository,
                              SchemaFieldRepository schemaFieldRepository,
                              NlpFieldResultRepository nlpRepository,
                              NlpPipelineService pipeline) {
        this.endpointRepository    = endpointRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.nlpRepository         = nlpRepository;
        this.pipeline              = pipeline;
    }

    /**
     * Run NLP preprocessing on all schema fields of an endpoint and persist results.
     *
     * <p>Prerequisites: Phase 4 schema analysis must have been run for the endpoint.
     *
     * <p>Idempotent: if an NLP result already exists for a schema field, it is
     * updated in place (upsert) to avoid unique constraint violations.
     *
     * @param endpointId the ID of the endpoint to process
     * @return list of persisted {@link NlpFieldResult} entities
     * @throws ResourceNotFoundException if the endpoint does not exist
     */
    @Transactional
    public List<NlpFieldResult> analyzeEndpoint(Long endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new ResourceNotFoundException("Endpoint", endpointId);
        }

        log.info("Running NLP analysis on endpoint {}", endpointId);

        // Load schema fields from Phase 4
        List<SchemaField> fields =
                schemaFieldRepository.findByEndpointIdOrderBySchemaTypeAscFieldPathAsc(endpointId);

        if (fields.isEmpty()) {
            log.warn("No schema fields found for endpoint {} — run schema analysis (Phase 4) first", endpointId);
            return List.of();
        }

        // Upsert: find existing NLP result for this schema field or create a new one
        List<NlpFieldResult> results = fields.stream()
                .map(sf -> {
                    NlpResult nlp = pipeline.process(sf.getFieldName(), sf.getFieldPath());
                    NlpFieldResult entity = nlpRepository.findBySchemaFieldId(sf.getId())
                            .orElseGet(NlpFieldResult::new);
                    entity.setSchemaField(sf);
                    entity.setOriginalName(nlp.getOriginalFieldName());
                    entity.setFieldPath(nlp.getFieldPath());
                    entity.setRawTokens(toJson(nlp.getRawTokens()));
                    entity.setExpandedTokens(toJson(nlp.getExpandedTokens()));
                    entity.setNormalized(nlp.getNormalized());
                    return entity;
                })
                .toList();

        List<NlpFieldResult> saved = nlpRepository.saveAll(results);
        log.info("NLP analysis complete for endpoint {} — {} fields processed", endpointId, saved.size());
        return saved;
    }

    /**
     * Retrieve previously persisted NLP results for an endpoint.
     *
     * @param endpointId the ID of the endpoint
     * @return list of NLP results; empty if not yet analyzed
     * @throws ResourceNotFoundException if the endpoint does not exist
     */
    @Transactional(readOnly = true)
    public List<NlpFieldResult> getNlpResults(Long endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new ResourceNotFoundException("Endpoint", endpointId);
        }
        return nlpRepository.findBySchemaFieldEndpointIdOrderBySchemaFieldFieldPathAsc(endpointId);
    }

    // ── Private helpers ───────────────────────────────────────


    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
