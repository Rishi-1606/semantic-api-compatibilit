package com.semanticapi.dto;

import com.semanticapi.model.MatchCandidate;

import java.time.Instant;

/**
 * Response DTO for a single semantic match candidate.
 *
 * <p>Exposes all scores transparently so the caller can see exactly
 * how the confidence was computed.
 */
public class MatchCandidateResponse {

    private Long   id;
    private Long   sessionId;

    // Source field info
    private Long   sourceFieldId;
    private String sourcePath;
    private String sourceOriginalName;
    private String sourceNormalized;
    private String sourceDataType;
    private String sourceSchemaType;

    // Target field info
    private Long   targetFieldId;
    private String targetPath;
    private String targetOriginalName;
    private String targetNormalized;
    private String targetDataType;
    private String targetSchemaType;

    // Scores (fully transparent)
    private double cosineSimilarity;
    private double typeFactor;
    private double pathFactor;
    private double confidence;

    private String approvalStatus;
    private String notes;
    private Instant createdAt;

    public static MatchCandidateResponse from(MatchCandidate c) {
        MatchCandidateResponse r = new MatchCandidateResponse();
        r.id               = c.getId();
        r.sessionId        = c.getSession().getId();
        r.sourceFieldId    = c.getSourceField().getId();
        r.sourcePath       = c.getSourcePath();
        r.sourceOriginalName = c.getSourceField().getFieldName();
        r.sourceNormalized = c.getSourceNormalized();
        r.sourceDataType   = c.getSourceField().getFieldType();
        r.sourceSchemaType = c.getSourceField().getSchemaType();
        r.targetFieldId    = c.getTargetField().getId();
        r.targetPath       = c.getTargetPath();
        r.targetOriginalName = c.getTargetField().getFieldName();
        r.targetNormalized = c.getTargetNormalized();
        r.targetDataType   = c.getTargetField().getFieldType();
        r.targetSchemaType = c.getTargetField().getSchemaType();
        r.cosineSimilarity = c.getCosineSimilarity();
        r.typeFactor       = c.getTypeFactor();
        r.pathFactor       = c.getPathFactor();
        r.confidence       = c.getConfidence();
        r.approvalStatus   = c.getApprovalStatus().name();
        r.notes            = c.getNotes();
        r.createdAt        = c.getCreatedAt();
        return r;
    }

    // ── Getters (all public for Jackson) ─────────────────────

    public Long    getId()                { return id; }
    public Long    getSessionId()         { return sessionId; }
    public Long    getSourceFieldId()     { return sourceFieldId; }
    public String  getSourcePath()        { return sourcePath; }
    public String  getSourceOriginalName(){ return sourceOriginalName; }
    public String  getSourceNormalized()  { return sourceNormalized; }
    public String  getSourceDataType()    { return sourceDataType; }
    public String  getSourceSchemaType()  { return sourceSchemaType; }
    public Long    getTargetFieldId()     { return targetFieldId; }
    public String  getTargetPath()        { return targetPath; }
    public String  getTargetOriginalName(){ return targetOriginalName; }
    public String  getTargetNormalized()  { return targetNormalized; }
    public String  getTargetDataType()    { return targetDataType; }
    public String  getTargetSchemaType()  { return targetSchemaType; }
    public double  getCosineSimilarity()  { return cosineSimilarity; }
    public double  getTypeFactor()        { return typeFactor; }
    public double  getPathFactor()        { return pathFactor; }
    public double  getConfidence()        { return confidence; }
    public String  getApprovalStatus()    { return approvalStatus; }
    public String  getNotes()             { return notes; }
    public Instant getCreatedAt()         { return createdAt; }
}
