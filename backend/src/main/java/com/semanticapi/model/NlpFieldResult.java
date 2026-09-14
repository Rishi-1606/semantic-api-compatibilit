package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Persisted result of the NLP preprocessing pipeline for a single schema field.
 *
 * <p>Each {@link SchemaField} from Phase 4 gets exactly one {@link NlpFieldResult}
 * (enforced by a UNIQUE constraint on {@code schema_field_id}).
 *
 * <p>Tokens are stored as JSON array strings (e.g. {@code ["phone","number"]})
 * so they can be deserialized by Phase 6 without requiring a separate join table.
 *
 * <p>Re-running NLP analysis is idempotent: the existing row is updated
 * (not duplicated) via the service layer.
 */
@Entity
@Table(name = "nlp_field_results")
public class NlpFieldResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The Phase 4 schema field this NLP result belongs to.
     * One-to-one enforced by a UNIQUE constraint on the FK column.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schema_field_id", nullable = false, unique = true)
    private SchemaField schemaField;

    /** Original field name exactly as stored in the schema. */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** Full dot-notation field path from Phase 4. */
    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    /**
     * Raw tokens from {@link com.semanticapi.nlp.FieldNameTokenizer},
     * stored as a JSON array string.
     * Example: {@code ["phone","number"]}
     */
    @Column(name = "raw_tokens", columnDefinition = "TEXT")
    private String rawTokens;

    /**
     * Tokens after abbreviation expansion,
     * stored as a JSON array string.
     * Example: {@code ["mobile","number"]}
     */
    @Column(name = "expanded_tokens", columnDefinition = "TEXT")
    private String expandedTokens;

    /**
     * Final normalized string — expanded tokens joined by spaces.
     * This is the primary input for Phase 6 semantic comparison.
     * Example: {@code "mobile number"}
     */
    @Column(name = "normalized")
    private String normalized;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Constructors ─────────────────────────────────────────

    public NlpFieldResult() {}

    // ── Getters / Setters ─────────────────────────────────────

    public Long        getId()                         { return id; }
    public void        setId(Long id)                  { this.id = id; }

    public SchemaField getSchemaField()                { return schemaField; }
    public void        setSchemaField(SchemaField sf)  { this.schemaField = sf; }

    public String      getOriginalName()               { return originalName; }
    public void        setOriginalName(String n)       { this.originalName = n; }

    public String      getFieldPath()                  { return fieldPath; }
    public void        setFieldPath(String p)          { this.fieldPath = p; }

    public String      getRawTokens()                  { return rawTokens; }
    public void        setRawTokens(String t)          { this.rawTokens = t; }

    public String      getExpandedTokens()             { return expandedTokens; }
    public void        setExpandedTokens(String t)     { this.expandedTokens = t; }

    public String      getNormalized()                 { return normalized; }
    public void        setNormalized(String n)         { this.normalized = n; }

    public Instant     getCreatedAt()                  { return createdAt; }
    public Instant     getUpdatedAt()                  { return updatedAt; }
}
