package com.semanticapi.repository;

import com.semanticapi.model.Api;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Api}.
 */
@Repository
public interface ApiRepository extends JpaRepository<Api, Long> {

    /** Find by exact name (case-insensitive). */
    Optional<Api> findByNameIgnoreCase(String name);

    /** Check if an API with the given name already exists. */
    boolean existsByNameIgnoreCase(String name);

    /** List all APIs ordered by name. */
    List<Api> findAllByOrderByNameAsc();
}
