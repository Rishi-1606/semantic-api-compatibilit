package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Overall compatibility report for a (source_version, target_version) pair.
 *
 * <p>Created by the Phase 7 Compatibility Engine after consuming Phase 6
 * {@link MatchCandidate} records.  Aggregates all
 * {@link EndpointCompatibilityResult} rows into a single classification.
 *
 * <h2>Score</h2>
 * <pre>
 *   overall_score = weighted average of per-endpoint compatibility scores
 *
 *   per-endpoint score:
 *     = Σ( accepted_candidate.confidence × field_weight ) / Σ( source_field_weight )
 *
 *   field_weight:
 *     2.0 for required fields
 *     1.0 for optional fields
 *
 *   accepted = AUTO_APPROVED or REQUIRES_REVIEW
 *   (LOW_CONFIDENCE and REJECTED are never counted as accepted)
 *
 *   Unmatched required fields contribute 0 to the numerator but 2.0 to the denominator,
 *   which maximally penalises the score.
 * </pre>
 *
 * <h2>Classification thresholds (spec § 6.7, § 7)</h2>
 * <pre>
 *   score ≥ 0.90 → COMPATIBLE
 *   score ≥ 0.70 → PARTIALLY_COMPATIBLE
 *   score  < 0.70 → INCOMPATIBLE
 * </pre>
 *
 * <p>Phase 8 (Transformation Generation) reads accepted {@link MatchCandidate}
 * records linked to this report's session to produce transformation rules.
 */
@Entity
@Table(name = "compatibility_reports")
public class CompatibilityReport {

    public enum CompatibilityStatus { COMPATIBLE, PARTIALLY_COMPATIBLE, INCOMPATIBLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_session_id", nullable = false)
    private MatchSession matchSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_version_id", nullable = false)
    private ApiVersion sourceVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_version_id", nullable = false)
    private ApiVersion targetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "compatibility_status", nullable = false, length = 30)
    private CompatibilityStatus compatibilityStatus;

    @Column(name = "overall_score", nullable = false)
    private double overallScore;

    // ── Field-level counts for explainability ─────────────────

    @Column(name = "total_source_fields", nullable = false)
    private int totalSourceFields;

    @Column(name = "matched_fields", nullable = false)
    private int matchedFields;

    @Column(name = "unmatched_required_fields", nullable = false)
    private int unmatchedRequiredFields;

    @Column(name = "auto_approved_count", nullable = false)
    private int autoApprovedCount;

    @Column(name = "requires_review_count", nullable = false)
    private int requiresReviewCount;

    @Column(name = "low_confidence_count", nullable = false)
    private int lowConfidenceCount;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────

    public Long                getId()                       { return id; }
    public MatchSession        getMatchSession()             { return matchSession; }
    public void                setMatchSession(MatchSession s) { this.matchSession = s; }
    public ApiVersion          getSourceVersion()            { return sourceVersion; }
    public void                setSourceVersion(ApiVersion v) { this.sourceVersion = v; }
    public ApiVersion          getTargetVersion()            { return targetVersion; }
    public void                setTargetVersion(ApiVersion v) { this.targetVersion = v; }
    public CompatibilityStatus getCompatibilityStatus()      { return compatibilityStatus; }
    public void                setCompatibilityStatus(CompatibilityStatus s) { this.compatibilityStatus = s; }
    public double              getOverallScore()             { return overallScore; }
    public void                setOverallScore(double s)     { this.overallScore = s; }
    public int                 getTotalSourceFields()        { return totalSourceFields; }
    public void                setTotalSourceFields(int n)   { this.totalSourceFields = n; }
    public int                 getMatchedFields()            { return matchedFields; }
    public void                setMatchedFields(int n)       { this.matchedFields = n; }
    public int                 getUnmatchedRequiredFields()  { return unmatchedRequiredFields; }
    public void                setUnmatchedRequiredFields(int n) { this.unmatchedRequiredFields = n; }
    public int                 getAutoApprovedCount()        { return autoApprovedCount; }
    public void                setAutoApprovedCount(int n)   { this.autoApprovedCount = n; }
    public int                 getRequiresReviewCount()      { return requiresReviewCount; }
    public void                setRequiresReviewCount(int n) { this.requiresReviewCount = n; }
    public int                 getLowConfidenceCount()       { return lowConfidenceCount; }
    public void                setLowConfidenceCount(int n)  { this.lowConfidenceCount = n; }
    public String              getExplanation()              { return explanation; }
    public void                setExplanation(String e)      { this.explanation = e; }
    public Instant             getCreatedAt()                { return createdAt; }
}
