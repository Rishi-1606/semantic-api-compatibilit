package com.semanticapi.repository;

import com.semanticapi.model.SchemaField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SchemaField}.
 */
@Repository
public interface SchemaFieldRepository extends JpaRepository<SchemaField, Long> {

    /**
     * Find all fields for an endpoint+schema type, ordered by field path.
     * Used to retrieve previously-analyzed schema fields.
     */
    List<SchemaField> findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(Long endpointId, String schemaType);

    /**
     * Find all fields for an endpoint (both REQUEST and RESPONSE), ordered by schema type then path.
     */
    List<SchemaField> findByEndpointIdOrderBySchemaTypeAscFieldPathAsc(Long endpointId);

    /**
     * Delete existing fields for an endpoint+schema type before re-analysis.
     * Idempotent re-analysis: calling analyze twice yields the same result.
     */
    void deleteByEndpointIdAndSchemaType(Long endpointId, String schemaType);
}
