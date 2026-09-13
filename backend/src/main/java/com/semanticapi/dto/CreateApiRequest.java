package com.semanticapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for registering a new API.
 */
public class CreateApiRequest {

    @NotBlank(message = "API name must not be blank")
    @Size(max = 255, message = "API name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    public String getName()                  { return name; }
    public void setName(String name)         { this.name = name; }

    public String getDescription()           { return description; }
    public void setDescription(String desc)  { this.description = desc; }
}
