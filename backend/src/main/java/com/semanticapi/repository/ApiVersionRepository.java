package com.semanticapi.repository;

import com.semanticapi.model.ApiVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ApiVersion}.
 */
@Repository
public interface ApiVersionRepository extends JpaRepository<ApiVersion, Long> {

    /** List all versions for a given API, ordered by version label. */
    List<ApiVersion> findByApiIdOrderByVersionAsc(Long apiId);

    /** Find a specific version of a specific API. */
    Optional<ApiVersion> findByApiIdAndVersionIgnoreCase(Long apiId, String version);

    /** Check if a version already exists for an API. */
    boolean existsByApiIdAndVersionIgnoreCase(Long apiId, String version);
}
