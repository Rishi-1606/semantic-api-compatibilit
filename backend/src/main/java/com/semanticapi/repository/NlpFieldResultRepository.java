package com.semanticapi.repository;

import com.semanticapi.model.NlpFieldResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link NlpFieldResult}.
 */
@Repository
public interface NlpFieldResultRepository extends JpaRepository<NlpFieldResult, Long> {

    /**
     * Find all NLP results for schema fields belonging to a given endpoint.
     * Eagerly fetch schemaField to prevent LazyInitializationException.
     */
    @EntityGraph(attributePaths = "schemaField")
    List<NlpFieldResult> findBySchemaFieldEndpointIdOrderBySchemaFieldFieldPathAsc(Long endpointId);

    /**
     * Find the NLP result for a specific schema field (if already analyzed).
     */
    @EntityGraph(attributePaths = "schemaField")
    Optional<NlpFieldResult> findBySchemaFieldId(Long schemaFieldId);

    /**
     * Delete all NLP results for schema fields of a given endpoint.
     * @Modifying + clearAutomatically ensures the persistence context is cleared
     * after deletion, preventing unique constraint violations on re-insertion.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM NlpFieldResult n WHERE n.schemaField.endpoint.id = :endpointId")
    void deleteBySchemaFieldEndpointId(Long endpointId);
}
