package com.semanticapi.repository;

import com.semanticapi.model.TransformationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Phase 8 {@link TransformationRule} persistence.
 *
 * <h2>Key query patterns</h2>
 * <ul>
 *   <li>By report — used after generation to return results to the caller.</li>
 *   <li>By endpoint pair — used by Phase 9/10 to retrieve active rules at runtime.</li>
 *   <li>Delete by report — used for idempotent re-generation.</li>
 * </ul>
 */
@Repository
public interface TransformationRuleRepository extends JpaRepository<TransformationRule, Long> {

    /**
     * All rules for a given compatibility report, ordered by confidence descending.
     * Used immediately after generation and by the GET /report/{id} endpoint.
     */
    @Query("""
        SELECT r FROM TransformationRule r
        JOIN FETCH r.sourceEndpoint
        JOIN FETCH r.targetEndpoint
        WHERE r.report.id = :reportId
        ORDER BY r.confidence DESC
        """)
    List<TransformationRule> findByReportId(@Param("reportId") Long reportId);

    /**
     * Rules for a report filtered by rule status (e.g. GENERATED, APPROVED).
     */
    @Query("""
        SELECT r FROM TransformationRule r
        WHERE r.report.id = :reportId
          AND r.ruleStatus = :status
        ORDER BY r.confidence DESC
        """)
    List<TransformationRule> findByReportIdAndRuleStatus(
            @Param("reportId") Long reportId,
            @Param("status") TransformationRule.RuleStatus status);

    /**
     * Rules for a specific endpoint pair — primary Phase 9/10 runtime query.
     * Returns all rules regardless of status; callers must filter by ruleStatus.
     */
    @Query("""
        SELECT r FROM TransformationRule r
        WHERE r.sourceEndpoint.id = :srcId
          AND r.targetEndpoint.id = :tgtId
        ORDER BY r.confidence DESC
        """)
    List<TransformationRule> findByEndpointPair(
            @Param("srcId") Long srcEndpointId,
            @Param("tgtId") Long tgtEndpointId);

    /**
     * Rules for a specific endpoint pair filtered by status.
     * Phase 10 will use this with status=APPROVED to get only executable rules.
     */
    @Query("""
        SELECT r FROM TransformationRule r
        WHERE r.sourceEndpoint.id = :srcId
          AND r.targetEndpoint.id = :tgtId
          AND r.ruleStatus = :status
        ORDER BY r.confidence DESC
        """)
    List<TransformationRule> findByEndpointPairAndStatus(
            @Param("srcId") Long srcEndpointId,
            @Param("tgtId") Long tgtEndpointId,
            @Param("status") TransformationRule.RuleStatus status);

    /**
     * Delete all rules for a report before re-generating (idempotency).
     * Called at the start of generateRules() to ensure a clean slate.
     */
    @Modifying
    @Query("DELETE FROM TransformationRule r WHERE r.report.id = :reportId")
    void deleteByReportId(@Param("reportId") Long reportId);

    /**
     * Check whether a rule already exists for a given match candidate.
     * Used for idempotency guard when generating rules one-at-a-time.
     */
    boolean existsByMatchCandidateId(Long matchCandidateId);

    /** Count valid (non-INVALID) rules for a report. */
    @Query("""
        SELECT COUNT(r) FROM TransformationRule r
        WHERE r.report.id = :reportId
          AND r.ruleStatus <> com.semanticapi.model.TransformationRule.RuleStatus.INVALID
        """)
    long countValidByReportId(@Param("reportId") Long reportId);

    /**
     * Find any previously APPROVED rule with the same sourcePath, targetPath,
     * and operation from a different report. Used by generateRules() to
     * auto-approve re-generated rules that were already human-approved before.
     */
    @Query("""
        SELECT r FROM TransformationRule r
        WHERE r.sourcePath  = :sourcePath
          AND r.targetPath  = :targetPath
          AND r.operation   = :operation
          AND r.ruleStatus  = com.semanticapi.model.TransformationRule.RuleStatus.APPROVED
          AND r.report.id  <> :currentReportId
        ORDER BY r.approvedAt DESC
        """)
    List<TransformationRule> findPreviouslyApproved(
            @Param("sourcePath")      String sourcePath,
            @Param("targetPath")      String targetPath,
            @Param("operation")       TransformationRule.Operation operation,
            @Param("currentReportId") Long currentReportId);
}
