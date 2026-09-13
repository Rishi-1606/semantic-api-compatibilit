package com.semanticapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Represents a single HTTP endpoint belonging to an {@link ApiVersion}.
 *
 * <p>Stores the HTTP method, path, and the raw JSON schemas for the
 * request and response bodies.  Schema parsing (field extraction) is
 * handled separately in Phase 4.
 */
@Entity
@Table(name = "endpoints")
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "ApiVersion reference must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_version_id", nullable = false)
    private ApiVersion apiVersion;

    @NotBlank(message = "HTTP method must not be blank")
    @Pattern(
        regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$",
        message = "HTTP method must be one of: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS"
    )
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @NotBlank(message = "Path must not be blank")
    @Size(max = 500, message = "Path must not exceed 500 characters")
    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Raw JSON schema for the request body (stored as text).
     * Parsed into {@code schema_fields} in Phase 4.
     */
    @Column(name = "request_schema", columnDefinition = "TEXT")
    private String requestSchema;

    /**
     * Raw JSON schema for the response body (stored as text).
     * Parsed into {@code schema_fields} in Phase 4.
     */
    @Column(name = "response_schema", columnDefinition = "TEXT")
    private String responseSchema;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Lifecycle ─────────────────────────────────────────────

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

    // ── Constructors ──────────────────────────────────────────

    public Endpoint() {}

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public ApiVersion getApiVersion()                { return apiVersion; }
    public void setApiVersion(ApiVersion v)          { this.apiVersion = v; }

    public String getHttpMethod()                    { return httpMethod; }
    public void setHttpMethod(String httpMethod)     { this.httpMethod = httpMethod.toUpperCase(); }

    public String getPath()                          { return path; }
    public void setPath(String path)                 { this.path = path; }

    public String getDescription()                   { return description; }
    public void setDescription(String description)   { this.description = description; }

    public String getRequestSchema()                 { return requestSchema; }
    public void setRequestSchema(String schema)      { this.requestSchema = schema; }

    public String getResponseSchema()                { return responseSchema; }
    public void setResponseSchema(String schema)     { this.responseSchema = schema; }

    public Instant getCreatedAt()                    { return createdAt; }
    public Instant getUpdatedAt()                    { return updatedAt; }
}
