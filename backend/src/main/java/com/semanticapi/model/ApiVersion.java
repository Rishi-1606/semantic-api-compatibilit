package com.semanticapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a specific version of an API (e.g., V1, V2).
 *
 * <p>Each version belongs to one {@link Api} and has its own
 * base URL and a list of {@link Endpoint}s.
 */
@Entity
@Table(
    name = "api_versions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_api_version",
        columnNames = {"api_id", "version"}
    )
)
public class ApiVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "API reference must not be null")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private Api api;

    @NotBlank(message = "Version label must not be blank")
    @Size(max = 50, message = "Version label must not exceed 50 characters")
    @Column(name = "version", nullable = false)
    private String version;

    @Size(max = 500, message = "Base URL must not exceed 500 characters")
    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "apiVersion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Endpoint> endpoints = new ArrayList<>();

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

    public ApiVersion() {}

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public Api getApi()                          { return api; }
    public void setApi(Api api)                  { this.api = api; }

    public String getVersion()                   { return version; }
    public void setVersion(String version)       { this.version = version; }

    public String getBaseUrl()                   { return baseUrl; }
    public void setBaseUrl(String baseUrl)       { this.baseUrl = baseUrl; }

    public String getDescription()               { return description; }
    public void setDescription(String desc)      { this.description = desc; }

    public boolean isActive()                    { return isActive; }
    public void setActive(boolean active)        { this.isActive = active; }

    public Instant getCreatedAt()                { return createdAt; }
    public Instant getUpdatedAt()                { return updatedAt; }

    public List<Endpoint> getEndpoints()         { return endpoints; }
    public void setEndpoints(List<Endpoint> e)   { this.endpoints = e; }
}
