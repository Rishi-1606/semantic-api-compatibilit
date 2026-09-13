package com.semanticapi.dto;

import com.semanticapi.model.Api;

import java.time.Instant;

/**
 * Response DTO for API resources.
 * Decouples the wire format from the JPA entity.
 */
public class ApiResponse {

    private Long    id;
    private String  name;
    private String  description;
    private int     versionCount;
    private Instant createdAt;
    private Instant updatedAt;

    /** Build from a JPA entity. */
    public static ApiResponse from(Api api) {
        ApiResponse r = new ApiResponse();
        r.id           = api.getId();
        r.name         = api.getName();
        r.description  = api.getDescription();
        r.versionCount = api.getVersions().size();
        r.createdAt    = api.getCreatedAt();
        r.updatedAt    = api.getUpdatedAt();
        return r;
    }

    public Long    getId()            { return id; }
    public String  getName()          { return name; }
    public String  getDescription()   { return description; }
    public int     getVersionCount()  { return versionCount; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
}
