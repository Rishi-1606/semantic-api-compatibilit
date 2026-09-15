package com.semanticapi.repository;

import com.semanticapi.model.MatchCandidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, Long> {

    @EntityGraph(attributePaths = {"sourceField", "targetField"})
    List<MatchCandidate> findBySessionIdOrderByConfidenceDesc(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
