package com.semanticapi.dto;

import com.semanticapi.model.Endpoint;

import java.time.Instant;

/**
 * Response DTO for Endpoint resources.
 */
public class EndpointResponse {

    private Long    id;
    private Long    apiVersionId;
    private String  apiVersionLabel;
    private String  apiName;
    private String  httpMethod;
    private String  path;
    private String  description;
    private String  requestSchema;
    private String  responseSchema;
    private Instant createdAt;
    private Instant updatedAt;

    /** Build from a JPA entity. */
    public static EndpointResponse from(Endpoint e) {
        EndpointResponse r = new EndpointResponse();
        r.id               = e.getId();
        r.apiVersionId     = e.getApiVersion().getId();
        r.apiVersionLabel  = e.getApiVersion().getVersion();
        r.apiName          = e.getApiVersion().getApi().getName();
        r.httpMethod       = e.getHttpMethod();
        r.path             = e.getPath();
        r.description      = e.getDescription();
        r.requestSchema    = e.getRequestSchema();
        r.responseSchema   = e.getResponseSchema();
        r.createdAt        = e.getCreatedAt();
        r.updatedAt        = e.getUpdatedAt();
        return r;
    }

    public Long    getId()               { return id; }
    public Long    getApiVersionId()     { return apiVersionId; }
    public String  getApiVersionLabel()  { return apiVersionLabel; }
    public String  getApiName()          { return apiName; }
    public String  getHttpMethod()       { return httpMethod; }
    public String  getPath()             { return path; }
    public String  getDescription()      { return description; }
    public String  getRequestSchema()    { return requestSchema; }
    public String  getResponseSchema()   { return responseSchema; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }
}
