package com.semanticapi.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single deterministic transformation rule produced by Phase 8.
 *
 * <h2>Rule lifecycle</h2>
 * <pre>
 *   Phase 8 generates: GENERATED (valid) or INVALID (failed safety check)
 *   Phase 9 sets:      APPROVED (human or auto) or REJECTED
 *   Phase 9 audit:     approvedAt / rejectedAt / approvalNote recorded
 *   Phase 10 reads:    APPROVED rules only
 * </pre>
 *
 * <h2>Operation semantics</h2>
 * <ul>
 *   <li>{@code RENAME}    — flat→flat different names, e.g. name → fullName</li>
 *   <li>{@code MOVE}      — flat→nested, e.g. mobile → contact.phone</li>
 *   <li>{@code FLATTEN}   — nested→flat, e.g. contact.phone → phoneNumber</li>
 *   <li>{@code COMBINE}   — N source paths → 1 target, e.g. firstName+lastName → fullName</li>
 *   <li>{@code SPLIT}     — 1 source → N target paths, e.g. fullName → firstName+lastName</li>
 *   <li>{@code TYPE_CONVERSION} — same/different path, different types, e.g. string → integer</li>
 *   <li>{@code ENUM_MAPPING}    — source enum value → target enum value, explicitly validated</li>
 * </ul>
 *
 * <h2>Safety invariants (spec § 7 + Phase 8 user requirements)</h2>
 * <ul>
 *   <li>Rules with identical sourcePath, targetPath, sourceType, and targetType
 *       are never generated (no-op transformation).</li>
 *   <li>ENUM_MAPPING is only generated when the mapping is explicitly derivable
 *       from schema description metadata — never inferred blindly.</li>
 *   <li>COMBINE/SPLIT are only generated when all required paths are present
 *       and the parameters can be represented deterministically.</li>
 *   <li>Every rule must be fully executable by Phase 10 using only its stored
 *       structured parameters — no hidden AI context is required.</li>
 *   <li>LOW_CONFIDENCE and REJECTED candidates never produce rules.</li>
 *   <li>Confidence is preserved from Phase 6 — never inflated.</li>
 * </ul>
 */
@Entity
@Table(name = "transformation_rules")
public class TransformationRule {

    // ── Enums ─────────────────────────────────────────────────

    /**
     * The type of transformation required to map source to target.
     * Must match the SQL constraint values in V5__transformation_rules.sql.
     */
    public enum Operation {
        /** Different field name, same nesting depth (flat→flat or nested→nested). */
        RENAME,
        /** Flat source path → nested target path (e.g. mobile → contact.phone). */
        MOVE,
        /** Nested source path → flat target path (e.g. contact.phone → phoneNumber). */
        FLATTEN,
        /** Multiple source fields concatenated into one target field. */
        COMBINE,
        /** One source field split into multiple target fields. */
        SPLIT,
        /** Same or different path, source and target types differ (safe conversions only). */
        TYPE_CONVERSION,
        /** Enum value mapping — only when explicitly derivable from schema metadata. */
        ENUM_MAPPING
    }

    /**
     * Phase 8 rule lifecycle status.
     * GENERATED and INVALID are set by Phase 8.
     * APPROVED and REJECTED are set by Phase 9 (or user REST actions).
     */
    public enum RuleStatus {
        /** Rule passed validation; awaiting Phase 9 approval decision. */
        GENERATED,
        /** Human or automatic approval granted; eligible for Phase 10 execution. */
        APPROVED,
        /** Explicitly rejected; must not be used by Phase 10. */
        REJECTED,
        /** Failed Phase 8 safety validation; stored for auditability but not executable. */
        INVALID
    }

    // ── Identity ──────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Source context ────────────────────────────────────────

    /** Phase 7 CompatibilityReport that triggered this rule generation batch. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private CompatibilityReport report;

    /**
     * Phase 6 MatchCandidate this rule was derived from.
     * Null for COMBINE rules when additional candidates were merged.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_candidate_id")
    private MatchCandidate matchCandidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_endpoint_id", nullable = false)
    private Endpoint sourceEndpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_endpoint_id", nullable = false)
    private Endpoint targetEndpoint;

    /** Redundant copy: "METHOD /path" for fast display without joins. */
    @Column(name = "source_endpoint_path")
    private String sourceEndpointPath;

    /** Redundant copy: "METHOD /path" for fast display without joins. */
    @Column(name = "target_endpoint_path")
    private String targetEndpointPath;

    // ── Field paths ───────────────────────────────────────────

    /**
     * Primary source field path (dot-notation).
     * For COMBINE: the first/only element of sourcePaths.
     */
    @Column(name = "source_path")
    private String sourcePath;

    /**
     * Primary target field path (dot-notation).
     * For SPLIT: the first/only element of targetPaths.
     */
    @Column(name = "target_path")
    private String targetPath;

    /**
     * JSON array of all source paths for COMBINE operations.
     * Example: ["firstName","lastName"]
     * Null for all other operations.
     */
    @Column(name = "source_paths", columnDefinition = "TEXT")
    private String sourcePaths;

    /**
     * JSON array of all target paths for SPLIT operations.
     * Example: ["firstName","lastName"]
     * Null for all other operations.
     */
    @Column(name = "target_paths", columnDefinition = "TEXT")
    private String targetPaths;

    // ── Operation ─────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 30)
    private Operation operation;

    // ── Type information ──────────────────────────────────────

    /** Data type of the source field as extracted by Phase 4 schema analysis. */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** Data type of the target field as extracted by Phase 4 schema analysis. */
    @Column(name = "target_type", length = 50)
    private String targetType;

    // ── Confidence / approval ─────────────────────────────────

    /**
     * Confidence score preserved from the Phase 6 MatchCandidate.
     * Never increased during Phase 8 rule generation.
     */
    @Column(name = "confidence", nullable = false)
    private double confidence;

    /**
     * The Phase 6 approval status of the source candidate.
     * AUTO_APPROVED → eligible for Phase 9 auto-approval.
     * REQUIRES_REVIEW → must have human approval before Phase 10 can execute.
     */
    @Column(name = "candidate_status", nullable = false, length = 30)
    private String candidateStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_status", nullable = false, length = 20)
    private RuleStatus ruleStatus = RuleStatus.GENERATED;

    // ── Operation parameters ──────────────────────────────────

    /**
     * JSON object containing all parameters needed for deterministic execution.
     *
     * <ul>
     *   <li>RENAME/MOVE/FLATTEN: {} (paths carry all required info)</li>
     *   <li>TYPE_CONVERSION: {"from":"string","to":"integer"}</li>
     *   <li>ENUM_MAPPING: {"mappings":{"male":"M","female":"F"}}</li>
     *   <li>COMBINE: {"separator":" ","order":["firstName","lastName"]}</li>
     *   <li>SPLIT: {"separator":" ","targets":["firstName","lastName"]}</li>
     * </ul>
     *
     * Phase 10 must be able to execute the rule using ONLY the stored parameters
     * without any additional AI reasoning or external context.
     */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    // ── Audit / errors ────────────────────────────────────────

    /** Set when ruleStatus = REJECTED. Human-readable reason. */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Set when ruleStatus = INVALID.
     * Semicolon-separated list of validation errors from Phase 8 safety checks.
     */
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Phase 9 audit ─────────────────────────────────────────

    /** Timestamp set when ruleStatus transitions to APPROVED (Phase 9). */
    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Timestamp set when ruleStatus transitions to REJECTED (Phase 9). */
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    /**
     * Human-supplied approval or rejection note (Phase 9).
     * Distinct from {@code rejectionReason}, which is set by Phase 8 generation failures.
     * This field carries the human reviewer's rationale.
     */
    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

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

    // ── Getters / Setters ─────────────────────────────────────

    public Long                getId()                         { return id; }
    public CompatibilityReport getReport()                     { return report; }
    public void                setReport(CompatibilityReport r){ this.report = r; }
    public MatchCandidate      getMatchCandidate()             { return matchCandidate; }
    public void                setMatchCandidate(MatchCandidate c) { this.matchCandidate = c; }
    public Endpoint            getSourceEndpoint()             { return sourceEndpoint; }
    public void                setSourceEndpoint(Endpoint e)   { this.sourceEndpoint = e; }
    public Endpoint            getTargetEndpoint()             { return targetEndpoint; }
    public void                setTargetEndpoint(Endpoint e)   { this.targetEndpoint = e; }
    public String              getSourceEndpointPath()         { return sourceEndpointPath; }
    public void                setSourceEndpointPath(String p) { this.sourceEndpointPath = p; }
    public String              getTargetEndpointPath()         { return targetEndpointPath; }
    public void                setTargetEndpointPath(String p) { this.targetEndpointPath = p; }
    public String              getSourcePath()                 { return sourcePath; }
    public void                setSourcePath(String p)         { this.sourcePath = p; }
    public String              getTargetPath()                 { return targetPath; }
    public void                setTargetPath(String p)         { this.targetPath = p; }
    public String              getSourcePaths()                { return sourcePaths; }
    public void                setSourcePaths(String p)        { this.sourcePaths = p; }
    public String              getTargetPaths()                { return targetPaths; }
    public void                setTargetPaths(String p)        { this.targetPaths = p; }
    public Operation           getOperation()                  { return operation; }
    public void                setOperation(Operation o)       { this.operation = o; }
    public String              getSourceType()                 { return sourceType; }
    public void                setSourceType(String t)         { this.sourceType = t; }
    public String              getTargetType()                 { return targetType; }
    public void                setTargetType(String t)         { this.targetType = t; }
    public double              getConfidence()                 { return confidence; }
    public void                setConfidence(double c)         { this.confidence = c; }
    public String              getCandidateStatus()            { return candidateStatus; }
    public void                setCandidateStatus(String s)    { this.candidateStatus = s; }
    public RuleStatus          getRuleStatus()                 { return ruleStatus; }
    public void                setRuleStatus(RuleStatus s)     { this.ruleStatus = s; }
    public String              getParameters()                 { return parameters; }
    public void                setParameters(String p)         { this.parameters = p; }
    public String              getRejectionReason()            { return rejectionReason; }
    public void                setRejectionReason(String r)    { this.rejectionReason = r; }
    public String              getValidationErrors()           { return validationErrors; }
    public void                setValidationErrors(String e)   { this.validationErrors = e; }
    public Instant             getCreatedAt()                  { return createdAt; }
    public Instant             getUpdatedAt()                  { return updatedAt; }
    public Instant             getApprovedAt()                 { return approvedAt; }
    public void                setApprovedAt(Instant t)        { this.approvedAt = t; }
    public Instant             getRejectedAt()                 { return rejectedAt; }
    public void                setRejectedAt(Instant t)        { this.rejectedAt = t; }
    public String              getApprovalNote()               { return approvalNote; }
    public void                setApprovalNote(String n)       { this.approvalNote = n; }
}
