package com.semanticapi.dto;

import com.semanticapi.model.ApiVersion;

import java.time.Instant;

/**
 * Response DTO for ApiVersion resources.
 */
public class ApiVersionResponse {

    private Long    id;
    private Long    apiId;
    private String  apiName;
    private String  version;
    private String  baseUrl;
    private String  description;
    private boolean isActive;
    private int     endpointCount;
    private Instant createdAt;
    private Instant updatedAt;

    /** Build from a JPA entity. */
    public static ApiVersionResponse from(ApiVersion v) {
        ApiVersionResponse r = new ApiVersionResponse();
        r.id            = v.getId();
        r.apiId         = v.getApi().getId();
        r.apiName       = v.getApi().getName();
        r.version       = v.getVersion();
        r.baseUrl       = v.getBaseUrl();
        r.description   = v.getDescription();
        r.isActive      = v.isActive();
        r.endpointCount = v.getEndpoints().size();
        r.createdAt     = v.getCreatedAt();
        r.updatedAt     = v.getUpdatedAt();
        return r;
    }

    public Long    getId()             { return id; }
    public Long    getApiId()          { return apiId; }
    public String  getApiName()        { return apiName; }
    public String  getVersion()        { return version; }
    public String  getBaseUrl()        { return baseUrl; }
    public String  getDescription()    { return description; }
    public boolean isActive()          { return isActive; }
    public int     getEndpointCount()  { return endpointCount; }
    public Instant getCreatedAt()      { return createdAt; }
    public Instant getUpdatedAt()      { return updatedAt; }
}
