package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single candidate mapping between one source schema field and one target schema field,
 * produced by the Phase 6 semantic matching engine.
 *
 * <h2>Score breakdown</h2>
 * <ul>
 *   <li>{@code cosine_similarity} — raw cosine similarity of embedding vectors [0,1]</li>
 *   <li>{@code type_factor} — penalty when data types are incompatible (1.0 = same, 0.6 = incompatible)</li>
 *   <li>{@code path_factor} — bonus/penalty for structural context similarity [0.8,1.0]</li>
 *   <li>{@code confidence} = cosine_similarity × type_factor × path_factor ∈ [0,1]</li>
 * </ul>
 *
 * <h2>Approval lifecycle</h2>
 * PENDING → AUTO_APPROVED (confidence ≥ threshold) | REQUIRES_REVIEW | REJECTED | LOW_CONFIDENCE
 *
 * <p>Phase 7 (Compatibility Engine) reads these candidates to classify overall API compatibility.
 * Phase 9 (Transformation Generator) reads APPROVED candidates to generate transformation rules.
 */
@Entity
@Table(name = "semantic_match_candidates")
public class MatchCandidate {

    public enum ApprovalStatus {
        PENDING,
        AUTO_APPROVED,
        REQUIRES_REVIEW,
        REJECTED,
        LOW_CONFIDENCE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MatchSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_field_id", nullable = false)
    private SchemaField sourceField;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_field_id", nullable = false)
    private SchemaField targetField;

    /** Redundant copies for fast reads without joins. */
    @Column(name = "source_path")
    private String sourcePath;

    @Column(name = "target_path")
    private String targetPath;

    @Column(name = "source_normalized")
    private String sourceNormalized;

    @Column(name = "target_normalized")
    private String targetNormalized;

    // ── Scores ────────────────────────────────────────────────

    @Column(name = "cosine_similarity", nullable = false)
    private double cosineSimilarity;

    @Column(name = "type_factor", nullable = false)
    private double typeFactor = 1.0;

    @Column(name = "path_factor", nullable = false)
    private double pathFactor = 1.0;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Getters/Setters ───────────────────────────────────────

    public Long           getId()                { return id; }
    public MatchSession   getSession()           { return session; }
    public void           setSession(MatchSession s) { this.session = s; }
    public SchemaField    getSourceField()       { return sourceField; }
    public void           setSourceField(SchemaField f) { this.sourceField = f; }
    public SchemaField    getTargetField()       { return targetField; }
    public void           setTargetField(SchemaField f) { this.targetField = f; }
    public String         getSourcePath()        { return sourcePath; }
    public void           setSourcePath(String p) { this.sourcePath = p; }
    public String         getTargetPath()        { return targetPath; }
    public void           setTargetPath(String p) { this.targetPath = p; }
    public String         getSourceNormalized()  { return sourceNormalized; }
    public void           setSourceNormalized(String n) { this.sourceNormalized = n; }
    public String         getTargetNormalized()  { return targetNormalized; }
    public void           setTargetNormalized(String n) { this.targetNormalized = n; }
    public double         getCosineSimilarity()  { return cosineSimilarity; }
    public void           setCosineSimilarity(double s) { this.cosineSimilarity = s; }
    public double         getTypeFactor()        { return typeFactor; }
    public void           setTypeFactor(double f) { this.typeFactor = f; }
    public double         getPathFactor()        { return pathFactor; }
    public void           setPathFactor(double f) { this.pathFactor = f; }
    public double         getConfidence()        { return confidence; }
    public void           setConfidence(double c) { this.confidence = c; }
    public ApprovalStatus getApprovalStatus()    { return approvalStatus; }
    public void           setApprovalStatus(ApprovalStatus s) { this.approvalStatus = s; }
    public String         getNotes()             { return notes; }
    public void           setNotes(String n)     { this.notes = n; }
    public Instant        getCreatedAt()         { return createdAt; }
}
