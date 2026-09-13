package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Represents a single extracted field from a parsed JSON schema.
 *
 * <p>Fields are extracted from the {@code request_schema} and
 * {@code response_schema} stored on an {@link Endpoint} and
 * persisted here for downstream use by the NLP pipeline (Phase 5)
 * and compatibility engine (Phase 7).
 *
 * <p>Schema type is either {@code REQUEST} or {@code RESPONSE}.
 *
 * <p>Field paths use dot-notation for nested fields, e.g.:
 * <ul>
 *   <li>{@code address.city}  — nested object field</li>
 *   <li>{@code tags[*]}       — array element</li>
 *   <li>{@code tags[*].name}  — field inside an array item</li>
 * </ul>
 */
@Entity
@Table(name = "schema_fields")
public class SchemaField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    /**
     * Whether this field comes from the request or response schema.
     * Values: "REQUEST" or "RESPONSE"
     */
    @Column(name = "schema_type", nullable = false, length = 20)
    private String schemaType;

    /** The simple field name (leaf name), e.g. "city" for "address.city". */
    @Column(name = "field_name", nullable = false)
    private String fieldName;

    /** Dot-notation full path, e.g. "address.city" or "tags[*].name". */
    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    /**
     * Inferred data type: string, integer, number, boolean, object, array, null.
     * "unknown" if the schema did not specify a type.
     */
    @Column(name = "field_type", length = 50)
    private String fieldType;

    /** Whether this field is in the "required" list of its parent object. */
    @Column(name = "is_required", nullable = false)
    private boolean isRequired = false;

    /** Dot-notation path of the parent object. Null for root-level fields. */
    @Column(name = "parent_path")
    private String parentPath;

    /**
     * Human-readable description from the schema, if available.
     * May also store enum values as a comma-separated string.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // ── Constructors ─────────────────────────────────────────

    public SchemaField() {}

    // ── Getters / Setters ─────────────────────────────────────

    public Long     getId()                        { return id; }
    public void     setId(Long id)                 { this.id = id; }

    public Endpoint getEndpoint()                  { return endpoint; }
    public void     setEndpoint(Endpoint e)        { this.endpoint = e; }

    public String   getSchemaType()                { return schemaType; }
    public void     setSchemaType(String t)        { this.schemaType = t; }

    public String   getFieldName()                 { return fieldName; }
    public void     setFieldName(String n)         { this.fieldName = n; }

    public String   getFieldPath()                 { return fieldPath; }
    public void     setFieldPath(String p)         { this.fieldPath = p; }

    public String   getFieldType()                 { return fieldType; }
    public void     setFieldType(String t)         { this.fieldType = t; }

    public boolean  isRequired()                   { return isRequired; }
    public void     setRequired(boolean r)         { this.isRequired = r; }

    public String   getParentPath()                { return parentPath; }
    public void     setParentPath(String p)        { this.parentPath = p; }

    public String   getDescription()               { return description; }
    public void     setDescription(String d)       { this.description = d; }

    public Instant  getCreatedAt()                 { return createdAt; }
}
