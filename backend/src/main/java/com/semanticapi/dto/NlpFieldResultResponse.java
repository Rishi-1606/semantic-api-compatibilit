package com.semanticapi.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.model.NlpFieldResult;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for NLP field results.
 * Deserializes the JSON-array token strings back into proper Java lists.
 */
public class NlpFieldResultResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long        id;
    private Long        schemaFieldId;
    private String      schemaType;
    private String      originalName;
    private String      fieldPath;
    private List<String> rawTokens;
    private List<String> expandedTokens;
    private String      normalized;
    private Instant     createdAt;
    private Instant     updatedAt;

    public static NlpFieldResultResponse from(NlpFieldResult r) {
        NlpFieldResultResponse dto = new NlpFieldResultResponse();
        dto.id            = r.getId();
        dto.schemaFieldId = r.getSchemaField().getId();
        dto.schemaType    = r.getSchemaField().getSchemaType();
        dto.originalName  = r.getOriginalName();
        dto.fieldPath     = r.getFieldPath();
        dto.rawTokens     = parseJsonArray(r.getRawTokens());
        dto.expandedTokens= parseJsonArray(r.getExpandedTokens());
        dto.normalized    = r.getNormalized();
        dto.createdAt     = r.getCreatedAt();
        dto.updatedAt     = r.getUpdatedAt();
        return dto;
    }

    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public Long         getId()             { return id; }
    public Long         getSchemaFieldId()  { return schemaFieldId; }
    public String       getSchemaType()     { return schemaType; }
    public String       getOriginalName()   { return originalName; }
    public String       getFieldPath()      { return fieldPath; }
    public List<String> getRawTokens()      { return rawTokens; }
    public List<String> getExpandedTokens() { return expandedTokens; }
    public String       getNormalized()     { return normalized; }
    public Instant      getCreatedAt()      { return createdAt; }
    public Instant      getUpdatedAt()      { return updatedAt; }
}
