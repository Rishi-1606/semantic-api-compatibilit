package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Per-endpoint compatibility result within a {@link CompatibilityReport}.
 *
 * <p>Captures the compatibility analysis for a single
 * (source_endpoint, target_endpoint) pair, including:
 * <ul>
 *   <li>Classification (COMPATIBLE / PARTIALLY_COMPATIBLE / INCOMPATIBLE)</li>
 *   <li>Compatibility score for this endpoint pair</li>
 *   <li>Field-level breakdown (matched, unmatched required, unmatched optional)</li>
 *   <li>Human-readable explanation</li>
 * </ul>
 *
 * <h2>Score formula</h2>
 * <pre>
 *   score = Σ( accepted_candidate.confidence × field_weight )
 *           ─────────────────────────────────────────────────
 *           Σ( source_field_weight )
 *
 *   where:
 *     field_weight = 2.0 for required fields, 1.0 for optional
 *     accepted     = approval_status IN (AUTO_APPROVED, REQUIRES_REVIEW)
 *     unmatched    → contributes 0 to numerator, full weight to denominator
 * </pre>
 *
 * <h2>This is distinct from Phase 6 confidence</h2>
 * <p>The Phase 6 {@link MatchCandidate#getConfidence()} is an AI-computed
 * per-field-pair similarity score.
 * This score aggregates multiple candidates and weights required vs optional fields.
 */
@Entity
@Table(name = "endpoint_compatibility_results")
public class EndpointCompatibilityResult {

    public enum CompatibilityStatus { COMPATIBLE, PARTIALLY_COMPATIBLE, INCOMPATIBLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private CompatibilityReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_endpoint_id", nullable = false)
    private Endpoint sourceEndpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_endpoint_id", nullable = false)
    private Endpoint targetEndpoint;

    @Column(name = "source_endpoint_path")
    private String sourceEndpointPath;

    @Column(name = "target_endpoint_path")
    private String targetEndpointPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "compatibility_status", nullable = false, length = 30)
    private CompatibilityStatus compatibilityStatus;

    @Column(name = "compatibility_score", nullable = false)
    private double compatibilityScore;

    // ── Field counts ─────────────────────────────────────────

    @Column(name = "total_source_fields", nullable = false)
    private int totalSourceFields;

    @Column(name = "matched_required_fields", nullable = false)
    private int matchedRequiredFields;

    @Column(name = "unmatched_required_fields", nullable = false)
    private int unmatchedRequiredFields;

    @Column(name = "matched_optional_fields", nullable = false)
    private int matchedOptionalFields;

    @Column(name = "unmatched_optional_fields", nullable = false)
    private int unmatchedOptionalFields;

    /** JSON array of source field paths that were matched. */
    @Column(name = "matched_field_paths", columnDefinition = "TEXT")
    private String matchedFieldPaths;

    /** JSON array of source field paths with no accepted target match. */
    @Column(name = "unmatched_source_paths", columnDefinition = "TEXT")
    private String unmatchedSourcePaths;

    /** JSON array of target field paths not covered by any source field match. */
    @Column(name = "unmatched_target_paths", columnDefinition = "TEXT")
    private String unmatchedTargetPaths;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters / Setters ─────────────────────────────────────

    public Long                  getId()                          { return id; }
    public CompatibilityReport   getReport()                      { return report; }
    public void                  setReport(CompatibilityReport r) { this.report = r; }
    public Endpoint              getSourceEndpoint()              { return sourceEndpoint; }
    public void                  setSourceEndpoint(Endpoint e)    { this.sourceEndpoint = e; }
    public Endpoint              getTargetEndpoint()              { return targetEndpoint; }
    public void                  setTargetEndpoint(Endpoint e)    { this.targetEndpoint = e; }
    public String                getSourceEndpointPath()          { return sourceEndpointPath; }
    public void                  setSourceEndpointPath(String p)  { this.sourceEndpointPath = p; }
    public String                getTargetEndpointPath()          { return targetEndpointPath; }
    public void                  setTargetEndpointPath(String p)  { this.targetEndpointPath = p; }
    public CompatibilityStatus   getCompatibilityStatus()         { return compatibilityStatus; }
    public void                  setCompatibilityStatus(CompatibilityStatus s) { this.compatibilityStatus = s; }
    public double                getCompatibilityScore()          { return compatibilityScore; }
    public void                  setCompatibilityScore(double s)  { this.compatibilityScore = s; }
    public int                   getTotalSourceFields()           { return totalSourceFields; }
    public void                  setTotalSourceFields(int n)      { this.totalSourceFields = n; }
    public int                   getMatchedRequiredFields()       { return matchedRequiredFields; }
    public void                  setMatchedRequiredFields(int n)  { this.matchedRequiredFields = n; }
    public int                   getUnmatchedRequiredFields()     { return unmatchedRequiredFields; }
    public void                  setUnmatchedRequiredFields(int n){ this.unmatchedRequiredFields = n; }
    public int                   getMatchedOptionalFields()       { return matchedOptionalFields; }
    public void                  setMatchedOptionalFields(int n)  { this.matchedOptionalFields = n; }
    public int                   getUnmatchedOptionalFields()     { return unmatchedOptionalFields; }
    public void                  setUnmatchedOptionalFields(int n){ this.unmatchedOptionalFields = n; }
    public String                getMatchedFieldPaths()           { return matchedFieldPaths; }
    public void                  setMatchedFieldPaths(String p)   { this.matchedFieldPaths = p; }
    public String                getUnmatchedSourcePaths()        { return unmatchedSourcePaths; }
    public void                  setUnmatchedSourcePaths(String p){ this.unmatchedSourcePaths = p; }
    public String                getUnmatchedTargetPaths()        { return unmatchedTargetPaths; }
    public void                  setUnmatchedTargetPaths(String p){ this.unmatchedTargetPaths = p; }
    public String                getExplanation()                 { return explanation; }
    public void                  setExplanation(String e)         { this.explanation = e; }
    public Instant               getCreatedAt()                   { return createdAt; }
}
