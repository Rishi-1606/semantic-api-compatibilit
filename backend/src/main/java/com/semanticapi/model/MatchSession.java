package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Represents one semantic analysis session between a source and target API version.
 *
 * <p>A session is created when a user requests compatibility analysis between two
 * {@link ApiVersion} records.  It stores the overall compatibility classification,
 * the embedding model used, and links to all {@link MatchCandidate} pairs.
 *
 * <p>Status lifecycle: PENDING → RUNNING → COMPLETED | FAILED
 */
@Entity
@Table(name = "semantic_match_sessions")
public class MatchSession {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }
    public enum CompatibilityStatus { COMPATIBLE, PARTIALLY_COMPATIBLE, INCOMPATIBLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_version_id", nullable = false)
    private ApiVersion sourceVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_version_id", nullable = false)
    private ApiVersion targetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "compatibility_status", length = 30)
    private CompatibilityStatus compatibilityStatus;

    @Column(name = "overall_score")
    private Double overallScore;

    /** The embedding provider used (e.g. "ollama", "lexical-fallback"). */
    @Column(name = "embedding_provider")
    private String embeddingProvider;

    /** The embedding model used (e.g. "nomic-embed-text"). */
    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "field_pair_count")
    private int fieldPairCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters/Setters ───────────────────────────────────────

    public Long                 getId()                    { return id; }
    public ApiVersion           getSourceVersion()         { return sourceVersion; }
    public void                 setSourceVersion(ApiVersion v) { this.sourceVersion = v; }
    public ApiVersion           getTargetVersion()         { return targetVersion; }
    public void                 setTargetVersion(ApiVersion v) { this.targetVersion = v; }
    public Status               getStatus()                { return status; }
    public void                 setStatus(Status s)        { this.status = s; }
    public CompatibilityStatus  getCompatibilityStatus()   { return compatibilityStatus; }
    public void                 setCompatibilityStatus(CompatibilityStatus c) { this.compatibilityStatus = c; }
    public Double               getOverallScore()          { return overallScore; }
    public void                 setOverallScore(Double s)  { this.overallScore = s; }
    public String               getEmbeddingProvider()     { return embeddingProvider; }
    public void                 setEmbeddingProvider(String p) { this.embeddingProvider = p; }
    public String               getEmbeddingModel()        { return embeddingModel; }
    public void                 setEmbeddingModel(String m)  { this.embeddingModel = m; }
    public int                  getFieldPairCount()        { return fieldPairCount; }
    public void                 setFieldPairCount(int n)   { this.fieldPairCount = n; }
    public String               getErrorMessage()          { return errorMessage; }
    public void                 setErrorMessage(String e)  { this.errorMessage = e; }
    public Instant              getCreatedAt()             { return createdAt; }
    public Instant              getCompletedAt()           { return completedAt; }
    public void                 setCompletedAt(Instant t)  { this.completedAt = t; }
}
