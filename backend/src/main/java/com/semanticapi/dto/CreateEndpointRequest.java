package com.semanticapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for registering an endpoint under an API version.
 */
public class CreateEndpointRequest {

    @NotNull(message = "apiVersionId must not be null")
    private Long apiVersionId;

    @NotBlank(message = "HTTP method must not be blank")
    @Pattern(
        regexp = "^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$",
        message = "HTTP method must be one of: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS"
    )
    private String httpMethod;

    @NotBlank(message = "Path must not be blank")
    @Size(max = 500, message = "Path must not exceed 500 characters")
    private String path;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    /** Raw JSON schema string for the request body. */
    private String requestSchema;

    /** Raw JSON schema string for the response body. */
    private String responseSchema;

    public Long   getApiVersionId()                  { return apiVersionId; }
    public void   setApiVersionId(Long id)           { this.apiVersionId = id; }

    public String getHttpMethod()                    { return httpMethod; }
    public void   setHttpMethod(String m)            { this.httpMethod = m; }

    public String getPath()                          { return path; }
    public void   setPath(String path)               { this.path = path; }

    public String getDescription()                   { return description; }
    public void   setDescription(String d)           { this.description = d; }

    public String getRequestSchema()                 { return requestSchema; }
    public void   setRequestSchema(String schema)    { this.requestSchema = schema; }

    public String getResponseSchema()                { return responseSchema; }
    public void   setResponseSchema(String schema)   { this.responseSchema = schema; }
}
