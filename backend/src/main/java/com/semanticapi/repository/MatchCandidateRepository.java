package com.semanticapi.repository;

import com.semanticapi.model.MatchCandidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, Long> {

    @EntityGraph(attributePaths = {"sourceField", "targetField"})
    List<MatchCandidate> findBySessionIdOrderByConfidenceDesc(Long sessionId);

    /**
     * Load all candidates for a session with source and target field + endpoint data
     * eagerly fetched — used by Phase 7 for endpoint-level grouping.
     */
    @Query("""
        SELECT c FROM MatchCandidate c
        JOIN FETCH c.sourceField sf
        JOIN FETCH sf.endpoint sep
        JOIN FETCH c.targetField tf
        JOIN FETCH tf.endpoint tep
        WHERE c.session.id = :sessionId
        ORDER BY c.confidence DESC
        """)
    List<MatchCandidate> findBySessionIdWithEndpoints(@Param("sessionId") Long sessionId);

    void deleteBySessionId(Long sessionId);
}
