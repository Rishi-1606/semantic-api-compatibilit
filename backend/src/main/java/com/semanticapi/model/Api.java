package com.semanticapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a named API (e.g., "Customer API").
 *
 * <p>An API can have multiple versions (ApiVersion), each of which
 * can have multiple endpoints (Endpoint).
 */
@Entity
@Table(name = "apis")
public class Api {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "API name must not be blank")
    @Size(max = 255, message = "API name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "api", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ApiVersion> versions = new ArrayList<>();

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

    public Api() {}

    public Api(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }

    public String getName()                    { return name; }
    public void setName(String name)           { this.name = name; }

    public String getDescription()             { return description; }
    public void setDescription(String desc)    { this.description = desc; }

    public Instant getCreatedAt()              { return createdAt; }
    public Instant getUpdatedAt()              { return updatedAt; }

    public List<ApiVersion> getVersions()      { return versions; }
    public void setVersions(List<ApiVersion> v){ this.versions = v; }
}
