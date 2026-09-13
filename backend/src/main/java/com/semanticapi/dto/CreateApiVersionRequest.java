package com.semanticapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a new version to an existing API.
 */
public class CreateApiVersionRequest {

    @NotNull(message = "apiId must not be null")
    private Long apiId;

    @NotBlank(message = "Version label must not be blank")
    @Size(max = 50, message = "Version label must not exceed 50 characters")
    private String version;

    @Size(max = 500, message = "Base URL must not exceed 500 characters")
    private String baseUrl;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    public Long   getApiId()                 { return apiId; }
    public void   setApiId(Long apiId)       { this.apiId = apiId; }

    public String getVersion()               { return version; }
    public void   setVersion(String version) { this.version = version; }

    public String getBaseUrl()               { return baseUrl; }
    public void   setBaseUrl(String url)     { this.baseUrl = url; }

    public String getDescription()           { return description; }
    public void   setDescription(String d)   { this.description = d; }
}
