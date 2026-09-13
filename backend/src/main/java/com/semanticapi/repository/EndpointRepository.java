package com.semanticapi.repository;

import com.semanticapi.model.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Endpoint}.
 */
@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    /** List all endpoints for a given API version, ordered by path. */
    List<Endpoint> findByApiVersionIdOrderByPathAsc(Long apiVersionId);

    /** Check if an endpoint with the same method+path already exists for a version. */
    boolean existsByApiVersionIdAndHttpMethodAndPath(Long apiVersionId, String httpMethod, String path);
}
