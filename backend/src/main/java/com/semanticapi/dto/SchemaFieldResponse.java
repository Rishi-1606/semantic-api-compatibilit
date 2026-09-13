package com.semanticapi.dto;

import com.semanticapi.model.SchemaField;

import java.time.Instant;

/**
 * Response DTO for a single extracted schema field.
 */
public class SchemaFieldResponse {

    private Long    id;
    private Long    endpointId;
    private String  schemaType;
    private String  fieldName;
    private String  fieldPath;
    private String  fieldType;
    private boolean required;
    private String  parentPath;
    private String  description;
    private Instant createdAt;

    public static SchemaFieldResponse from(SchemaField sf) {
        SchemaFieldResponse r = new SchemaFieldResponse();
        r.id         = sf.getId();
        r.endpointId = sf.getEndpoint().getId();
        r.schemaType = sf.getSchemaType();
        r.fieldName  = sf.getFieldName();
        r.fieldPath  = sf.getFieldPath();
        r.fieldType  = sf.getFieldType();
        r.required   = sf.isRequired();
        r.parentPath = sf.getParentPath();
        r.description= sf.getDescription();
        r.createdAt  = sf.getCreatedAt();
        return r;
    }

    public Long    getId()          { return id; }
    public Long    getEndpointId()  { return endpointId; }
    public String  getSchemaType()  { return schemaType; }
    public String  getFieldName()   { return fieldName; }
    public String  getFieldPath()   { return fieldPath; }
    public String  getFieldType()   { return fieldType; }
    public boolean isRequired()     { return required; }
    public String  getParentPath()  { return parentPath; }
    public String  getDescription() { return description; }
    public Instant getCreatedAt()   { return createdAt; }
}
