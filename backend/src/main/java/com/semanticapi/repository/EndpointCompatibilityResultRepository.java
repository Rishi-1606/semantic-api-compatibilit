package com.semanticapi.repository;

import com.semanticapi.model.EndpointCompatibilityResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointCompatibilityResultRepository
        extends JpaRepository<EndpointCompatibilityResult, Long> {

    @EntityGraph(attributePaths = {"sourceEndpoint", "targetEndpoint"})
    List<EndpointCompatibilityResult> findByReportIdOrderByCompatibilityScoreDesc(Long reportId);

    List<EndpointCompatibilityResult> findBySourceEndpointId(Long sourceEndpointId);
}
