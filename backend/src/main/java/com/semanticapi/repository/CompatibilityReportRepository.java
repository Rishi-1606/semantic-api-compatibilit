package com.semanticapi.repository;

import com.semanticapi.model.CompatibilityReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompatibilityReportRepository extends JpaRepository<CompatibilityReport, Long> {

    @EntityGraph(attributePaths = {"sourceVersion", "targetVersion", "matchSession"})
    Optional<CompatibilityReport> findById(Long id);

    @Query("""
        SELECT r FROM CompatibilityReport r
        JOIN FETCH r.sourceVersion
        JOIN FETCH r.targetVersion
        JOIN FETCH r.matchSession
        WHERE r.sourceVersion.id = :versionId OR r.targetVersion.id = :versionId
        ORDER BY r.createdAt DESC
        """)
    List<CompatibilityReport> findByVersionId(@Param("versionId") Long versionId);

    @Query("""
        SELECT r FROM CompatibilityReport r
        JOIN FETCH r.sourceVersion
        JOIN FETCH r.targetVersion
        JOIN FETCH r.matchSession
        WHERE r.matchSession.id = :sessionId
        ORDER BY r.createdAt DESC
        """)
    List<CompatibilityReport> findBySessionId(@Param("sessionId") Long sessionId);
}
