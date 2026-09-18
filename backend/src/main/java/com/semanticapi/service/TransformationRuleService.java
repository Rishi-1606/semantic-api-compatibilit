package com.semanticapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.dto.TransformationRuleGenerationResult;
import com.semanticapi.dto.TransformationRuleResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 8 — Transformation Rule Generator.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Consume accepted ({@code AUTO_APPROVED}, {@code REQUIRES_REVIEW})
 *       {@link MatchCandidate} records from a Phase 7 {@link CompatibilityReport}.</li>
 *   <li>Determine the appropriate {@link TransformationRule.Operation} for each candidate.</li>
 *   <li>Validate each rule for safety before storing it.</li>
 *   <li>Persist rules as structured data — never as executable code.</li>
 *   <li>Provide CRUD + approve/reject operations for the REST layer.</li>
 * </ol>
 *
 * <h2>Operation determination (spec § 6.8)</h2>
 * <pre>
 *   sourcePath == targetPath AND sourceType == targetType  → skip (no-op, no rule)
 *   sourcePath == targetPath AND types differ              → TYPE_CONVERSION
 *   flat source → nested target (target contains '.')      → MOVE
 *   nested source → flat target (source contains '.')      → FLATTEN
 *   types differ AND both paths flat (same depth)          → TYPE_CONVERSION + RENAME
 *   source enum + target enum known from schema desc       → ENUM_MAPPING (explicit only)
 *   default (different names, same depth, same type)       → RENAME
 * </pre>
 *
 * <h2>Safety invariants (spec § 7 + additional user requirements)</h2>
 * <ul>
 *   <li>Identical path+type pairs produce no rule (no meaningless RENAME).</li>
 *   <li>ENUM_MAPPING only when source AND target enum values are explicitly available
 *       in schema description metadata and can be mapped deterministically.</li>
 *   <li>COMBINE and SPLIT are not generated from single-candidate matching alone;
 *       they require all source/target paths to be concretely identified.</li>
 *   <li>Every stored rule must be fully executable by Phase 10 using only its
 *       {@code parameters} JSON — no external AI context required.</li>
 *   <li>{@code LOW_CONFIDENCE} and {@code REJECTED} candidates are never processed.</li>
 *   <li>Confidence is never inflated beyond the Phase 6 value.</li>
 *   <li>Generation is idempotent — re-running for the same report replaces all rules.</li>
 * </ul>
 *
 * <h2>Approval model</h2>
 * <ul>
 *   <li>Phase 8 only produces {@code GENERATED} (valid) or {@code INVALID} (failed check).</li>
 *   <li>Phase 9 sets {@code APPROVED} / {@code REJECTED}.</li>
 *   <li>A {@code REQUIRES_REVIEW} candidate produces a {@code GENERATED} rule that
 *       must NOT be auto-approved here — Phase 9 owns that decision.</li>
 * </ul>
 */
@Service
public class TransformationRuleService {

    private static final Logger log = LoggerFactory.getLogger(TransformationRuleService.class);

    // Safe type-conversion pairs: from → set of safe target types.
    // Only these conversions may produce TYPE_CONVERSION rules.
    private static final Map<String, Set<String>> SAFE_TYPE_CONVERSIONS;

    static {
        SAFE_TYPE_CONVERSIONS = new HashMap<>();
        SAFE_TYPE_CONVERSIONS.put("string",  Set.of("integer", "number", "boolean"));
        SAFE_TYPE_CONVERSIONS.put("integer", Set.of("string", "number"));
        SAFE_TYPE_CONVERSIONS.put("number",  Set.of("string", "integer"));
        SAFE_TYPE_CONVERSIONS.put("boolean", Set.of("string"));
        // object and array conversions are never safe at this level
    }

    private final CompatibilityReportRepository       reportRepository;
    private final MatchSessionRepository              sessionRepository;
    private final MatchCandidateRepository            candidateRepository;
    private final SchemaFieldRepository               schemaFieldRepository;
    private final EndpointRepository                  endpointRepository;
    private final TransformationRuleRepository        ruleRepository;
    private final ObjectMapper                        objectMapper;

    public TransformationRuleService(
            CompatibilityReportRepository reportRepository,
            MatchSessionRepository sessionRepository,
            MatchCandidateRepository candidateRepository,
            SchemaFieldRepository schemaFieldRepository,
            EndpointRepository endpointRepository,
            TransformationRuleRepository ruleRepository,
            ObjectMapper objectMapper) {
        this.reportRepository    = reportRepository;
        this.sessionRepository   = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.endpointRepository  = endpointRepository;
        this.ruleRepository      = ruleRepository;
        this.objectMapper        = objectMapper;
    }

    // ══════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════

    /**
     * Generate transformation rules for all accepted candidates in a Phase 7 report.
     *
     * <p>Idempotent: any previously generated rules for the report are deleted first.
     *
     * @param reportId the {@link CompatibilityReport#getId()} from Phase 7
     * @return generation summary including rule list
     */
    @Transactional
    public TransformationRuleGenerationResult generateRules(Long reportId) {
        CompatibilityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("CompatibilityReport", reportId));

        log.info("Phase 8: generating transformation rules for report {} ({} → {})",
                reportId,
                report.getSourceVersion().getVersion(),
                report.getTargetVersion().getVersion());

        // ── Idempotency: delete existing rules for this report ──
        ruleRepository.deleteByReportId(reportId);

        // ── Load all candidates for the session ─────────────────
        Long sessionId = report.getMatchSession().getId();
        List<MatchCandidate> allCandidates =
                candidateRepository.findBySessionIdWithEndpoints(sessionId);

        log.debug("  loaded {} total candidates for session {}", allCandidates.size(), sessionId);

        // ── Counters ─────────────────────────────────────────────
        int skippedLowConfidence = 0;
        int skippedRejected      = 0;
        int skippedNoOp          = 0;
        int rulesGenerated       = 0;
        int rulesInvalid         = 0;

        List<TransformationRule> savedRules = new ArrayList<>();

        for (MatchCandidate candidate : allCandidates) {
            MatchCandidate.ApprovalStatus status = candidate.getApprovalStatus();

            // ── Exclude LOW_CONFIDENCE and REJECTED ───────────────
            if (status == MatchCandidate.ApprovalStatus.LOW_CONFIDENCE) {
                skippedLowConfidence++;
                log.debug("  skipping candidate {} — LOW_CONFIDENCE", candidate.getId());
                continue;
            }
            if (status == MatchCandidate.ApprovalStatus.REJECTED) {
                skippedRejected++;
                log.debug("  skipping candidate {} — REJECTED", candidate.getId());
                continue;
            }
            if (status == MatchCandidate.ApprovalStatus.PENDING) {
                // PENDING means Phase 6 hasn't classified it yet — skip safely
                log.debug("  skipping candidate {} — PENDING (Phase 6 not complete)", candidate.getId());
                continue;
            }

            // ── Check for no-op (identical path AND identical type) ─
            String srcPath  = normalizeNull(candidate.getSourcePath());
            String tgtPath  = normalizeNull(candidate.getTargetPath());
            String srcType  = normalizeNull(candidate.getSourceField().getFieldType());
            String tgtType  = normalizeNull(candidate.getTargetField().getFieldType());

            if (srcPath.equals(tgtPath) && typesMatch(srcType, tgtType)) {
                skippedNoOp++;
                log.debug("  skipping candidate {} — no-op (identical path '{}' and type '{}')",
                        candidate.getId(), srcPath, srcType);
                continue;
            }

            // ── Determine operation ──────────────────────────────
            TransformationRule.Operation operation = determineOperation(
                    srcPath, tgtPath, srcType, tgtType,
                    candidate.getSourceField(), candidate.getTargetField());

            // ── Build the rule ────────────────────────────────────
            TransformationRule rule = buildRule(report, candidate, operation,
                    srcPath, tgtPath, srcType, tgtType);

            // ── Validate ──────────────────────────────────────────
            List<String> errors = validateRule(rule, candidate);
            if (!errors.isEmpty()) {
                rule.setRuleStatus(TransformationRule.RuleStatus.INVALID);
                rule.setValidationErrors(String.join("; ", errors));
                rulesInvalid++;
                log.warn("  candidate {} produced INVALID rule: {}", candidate.getId(), errors);
            } else {
                rule.setRuleStatus(TransformationRule.RuleStatus.GENERATED);
                rulesGenerated++;
                log.debug("  candidate {} → {} rule: {} → {}",
                        candidate.getId(), operation, srcPath, tgtPath);
            }

            savedRules.add(rule);
        }

        // ── Persist all at once ─────────────────────────────────
        List<TransformationRule> persistedRules = ruleRepository.saveAll(savedRules);

        log.info("Phase 8: report {} → {} rules generated, {} invalid, " +
                "{} skipped (low-conf: {}, rejected: {}, no-op: {})",
                reportId, rulesGenerated, rulesInvalid,
                skippedLowConfidence + skippedRejected + skippedNoOp,
                skippedLowConfidence, skippedRejected, skippedNoOp);

        // ── Build result ─────────────────────────────────────────
        TransformationRuleGenerationResult result = new TransformationRuleGenerationResult();
        result.setReportId(reportId);
        result.setTotalCandidatesProcessed(allCandidates.size());
        result.setRulesGenerated(rulesGenerated);
        result.setRulesInvalid(rulesInvalid);
        result.setSkippedNoOp(skippedNoOp);
        result.setSkippedLowConfidence(skippedLowConfidence);
        result.setSkippedRejected(skippedRejected);
        result.setRules(persistedRules.stream().map(TransformationRuleResponse::from).toList());

        return result;
    }

    /** Retrieve a single rule by ID. */
    @Transactional(readOnly = true)
    public TransformationRule getRuleById(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("TransformationRule", ruleId));
    }

    /** All rules for a compatibility report. */
    @Transactional(readOnly = true)
    public List<TransformationRule> getRulesByReport(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException("CompatibilityReport", reportId);
        }
        return ruleRepository.findByReportId(reportId);
    }

    /**
     * Rules for a specific (source, target) endpoint pair.
     * Callers may filter the result list by ruleStatus as needed.
     */
    @Transactional(readOnly = true)
    public List<TransformationRule> getRulesByEndpointPair(Long srcEndpointId, Long tgtEndpointId) {
        if (!endpointRepository.existsById(srcEndpointId)) {
            throw new ResourceNotFoundException("Endpoint", srcEndpointId);
        }
        if (!endpointRepository.existsById(tgtEndpointId)) {
            throw new ResourceNotFoundException("Endpoint", tgtEndpointId);
        }
        return ruleRepository.findByEndpointPair(srcEndpointId, tgtEndpointId);
    }

    /**
     * Approve a GENERATED rule.
     *
     * <p>Only GENERATED rules may be approved.
     * INVALID rules may not be approved — they must be rejected or re-generated.
     *
     * @param ruleId the rule to approve
     * @param reason optional human-readable approval note
     * @return the updated rule
     */
    @Transactional
    public TransformationRule approveRule(Long ruleId, String reason) {
        TransformationRule rule = getRuleById(ruleId);

        if (rule.getRuleStatus() == TransformationRule.RuleStatus.INVALID) {
            throw new IllegalStateException(
                    "Rule " + ruleId + " is INVALID and cannot be approved. " +
                    "Validation errors: " + rule.getValidationErrors());
        }
        if (rule.getRuleStatus() == TransformationRule.RuleStatus.APPROVED) {
            // Idempotent — already approved, return as-is
            return rule;
        }
        if (rule.getRuleStatus() == TransformationRule.RuleStatus.REJECTED) {
            throw new IllegalStateException(
                    "Rule " + ruleId + " has already been REJECTED and cannot be approved. " +
                    "Re-generate rules for the report to get a fresh GENERATED rule.");
        }

        rule.setRuleStatus(TransformationRule.RuleStatus.APPROVED);
        if (reason != null && !reason.isBlank()) {
            rule.setRejectionReason(null); // clear any previous rejection reason
        }
        log.info("Phase 8: rule {} APPROVED (candidate_status={}, confidence={})",
                ruleId, rule.getCandidateStatus(), rule.getConfidence());
        return ruleRepository.save(rule);
    }

    /**
     * Reject a GENERATED or APPROVED rule.
     *
     * @param ruleId the rule to reject
     * @param reason human-readable rejection reason
     * @return the updated rule
     */
    @Transactional
    public TransformationRule rejectRule(Long ruleId, String reason) {
        TransformationRule rule = getRuleById(ruleId);

        if (rule.getRuleStatus() == TransformationRule.RuleStatus.REJECTED) {
            // Idempotent — already rejected, return as-is
            return rule;
        }

        rule.setRuleStatus(TransformationRule.RuleStatus.REJECTED);
        rule.setRejectionReason(reason != null ? reason : "No reason provided");
        log.info("Phase 8: rule {} REJECTED (reason: {})", ruleId, rule.getRejectionReason());
        return ruleRepository.save(rule);
    }

    // ══════════════════════════════════════════════════════════
    // Operation determination (pure logic — no I/O)
    // ══════════════════════════════════════════════════════════

    /**
     * Determine the appropriate transformation operation for a candidate pair.
     *
     * <p>Decision tree:
     * <ol>
     *   <li>If source and target types differ and the conversion is safe → TYPE_CONVERSION</li>
     *   <li>If source is flat and target is nested (contains '.') → MOVE</li>
     *   <li>If source is nested and target is flat → FLATTEN</li>
     *   <li>If source enum values and target enum values are explicitly available
     *       in schema metadata AND can be mapped 1-to-1 → ENUM_MAPPING</li>
     *   <li>Default: RENAME (different names, same structural depth)</li>
     * </ol>
     *
     * <p>COMBINE and SPLIT are NOT produced by this method because a single
     * MatchCandidate only represents one source→target pair. Multi-field
     * operations require explicit multi-candidate grouping, which Phase 8
     * does not currently perform (per safety requirements — no placeholder rules).
     *
     * @param srcPath   source field path (dot-notation)
     * @param tgtPath   target field path (dot-notation)
     * @param srcType   source field data type from Phase 4
     * @param tgtType   target field data type from Phase 4
     * @param srcField  full source SchemaField for enum inspection
     * @param tgtField  full target SchemaField for enum inspection
     * @return the determined operation
     */
    public TransformationRule.Operation determineOperation(
            String srcPath, String tgtPath,
            String srcType, String tgtType,
            SchemaField srcField, SchemaField tgtField) {

        boolean srcNested = isNested(srcPath);
        boolean tgtNested = isNested(tgtPath);
        boolean typesDiffer = !typesMatch(srcType, tgtType);

        // Priority 1: explicit enum mapping — most specific operation, checked first.
        // Only generate when both source and target enums are available in schema metadata.
        if (hasEnumValues(srcField) && hasEnumValues(tgtField)) {
            Map<String, String> mapping = buildEnumMapping(srcField, tgtField);
            if (!mapping.isEmpty()) {
                return TransformationRule.Operation.ENUM_MAPPING;
            }
        }

        // Priority 2: type conversion — if types differ and conversion is safe.
        // This can coexist with a path change, but we classify by type difference first
        // for flat-to-flat pairs (path change will be reflected in source/target paths).
        if (typesDiffer && isSafeTypeConversion(srcType, tgtType)) {
            return TransformationRule.Operation.TYPE_CONVERSION;
        }

        // Priority 3: structural (nesting) transformation.
        if (!srcNested && tgtNested) {
            return TransformationRule.Operation.MOVE;
        }
        if (srcNested && !tgtNested) {
            return TransformationRule.Operation.FLATTEN;
        }

        // Priority 4: default — RENAME (different names, same structural level).
        return TransformationRule.Operation.RENAME;
    }

    // ══════════════════════════════════════════════════════════
    // Rule building
    // ══════════════════════════════════════════════════════════

    private TransformationRule buildRule(
            CompatibilityReport report,
            MatchCandidate candidate,
            TransformationRule.Operation operation,
            String srcPath, String tgtPath,
            String srcType, String tgtType) {

        Endpoint srcEp = candidate.getSourceField().getEndpoint();
        Endpoint tgtEp = candidate.getTargetField().getEndpoint();

        TransformationRule rule = new TransformationRule();
        rule.setReport(report);
        rule.setMatchCandidate(candidate);
        rule.setSourceEndpoint(srcEp);
        rule.setTargetEndpoint(tgtEp);
        rule.setSourceEndpointPath(srcEp.getHttpMethod() + " " + srcEp.getPath());
        rule.setTargetEndpointPath(tgtEp.getHttpMethod() + " " + tgtEp.getPath());
        rule.setSourcePath(srcPath);
        rule.setTargetPath(tgtPath);
        rule.setOperation(operation);
        rule.setSourceType(srcType);
        rule.setTargetType(tgtType);
        rule.setConfidence(candidate.getConfidence());   // preserved — never inflated
        rule.setCandidateStatus(candidate.getApprovalStatus().name());

        // Build operation-specific parameters JSON
        rule.setParameters(buildParameters(operation, srcType, tgtType,
                candidate.getSourceField(), candidate.getTargetField()));

        return rule;
    }

    /**
     * Build the {@code parameters} JSON object for a rule.
     *
     * <p>Every rule must be executable by Phase 10 using only what is stored here.
     * No external AI context or hidden state may be required.
     *
     * <ul>
     *   <li>RENAME:          {} — paths carry all information</li>
     *   <li>MOVE/FLATTEN:    {} — paths carry all information</li>
     *   <li>TYPE_CONVERSION: {"from":"string","to":"integer"}</li>
     *   <li>ENUM_MAPPING:    {"mappings":{"male":"M","female":"F"}}</li>
     *   <li>COMBINE:         not built here (single-candidate scope)</li>
     *   <li>SPLIT:           not built here (single-candidate scope)</li>
     * </ul>
     */
    private String buildParameters(
            TransformationRule.Operation operation,
            String srcType, String tgtType,
            SchemaField srcField, SchemaField tgtField) {

        try {
            Map<String, Object> params = new LinkedHashMap<>();

            switch (operation) {
                case TYPE_CONVERSION -> {
                    params.put("from", srcType);
                    params.put("to", tgtType);
                }
                case ENUM_MAPPING -> {
                    Map<String, String> mappings = buildEnumMapping(srcField, tgtField);
                    params.put("mappings", mappings);
                }
                // RENAME, MOVE, FLATTEN: no additional parameters needed
                default -> { /* empty params object */ }
            }

            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.warn("Failed to serialize parameters for operation {}: {}", operation, e.getMessage());
            return "{}";
        }
    }

    // ══════════════════════════════════════════════════════════
    // Validation (safety checks)
    // ══════════════════════════════════════════════════════════

    /**
     * Validate a rule before persisting it as GENERATED.
     *
     * <p>Checks (spec § 7):
     * <ol>
     *   <li>Source path must not be null/blank.</li>
     *   <li>Target path must not be null/blank.</li>
     *   <li>Source endpoint must exist (already guaranteed by FK, but verified).</li>
     *   <li>Target endpoint must exist.</li>
     *   <li>Operation must be a supported enum value.</li>
     *   <li>If TYPE_CONVERSION: the conversion must be in the safe matrix.</li>
     *   <li>If TYPE_CONVERSION: object or array types may not be converted.</li>
     *   <li>If ENUM_MAPPING: parameters must contain a non-empty mappings object.</li>
     *   <li>Confidence must be > 0.</li>
     *   <li>candidateStatus must be AUTO_APPROVED or REQUIRES_REVIEW.</li>
     * </ol>
     *
     * @return list of error strings (empty = valid)
     */
    public List<String> validateRule(TransformationRule rule, MatchCandidate candidate) {
        List<String> errors = new ArrayList<>();

        // Source and target paths must be present
        if (isBlank(rule.getSourcePath())) {
            errors.add("Source path is missing or blank");
        }
        if (isBlank(rule.getTargetPath())) {
            errors.add("Target path is missing or blank");
        }

        // Source and target endpoints must be set
        if (rule.getSourceEndpoint() == null) {
            errors.add("Source endpoint is null");
        }
        if (rule.getTargetEndpoint() == null) {
            errors.add("Target endpoint is null");
        }

        // Confidence must be positive
        if (rule.getConfidence() <= 0.0) {
            errors.add("Confidence must be greater than 0.0 (got " + rule.getConfidence() + ")");
        }

        // Candidate status must be an accepted status
        String cs = rule.getCandidateStatus();
        if (!MatchCandidate.ApprovalStatus.AUTO_APPROVED.name().equals(cs) &&
                !MatchCandidate.ApprovalStatus.REQUIRES_REVIEW.name().equals(cs)) {
            errors.add("candidateStatus must be AUTO_APPROVED or REQUIRES_REVIEW (got " + cs + ")");
        }

        // Global check: object and array type fields cannot be transformed by any rule.
        // A RENAME/MOVE/FLATTEN that encounters object→string (etc.) is just as unsafe
        // as a TYPE_CONVERSION. Flag them here so no rule slips through undetected.
        String srcType = normalizeNull(rule.getSourceType());
        String tgtType = normalizeNull(rule.getTargetType());

        // Operation-specific validation — null check first to guard everything below
        if (rule.getOperation() == null) {
            errors.add("Operation is null");
            return errors;
        }

        if (rule.getOperation() != TransformationRule.Operation.TYPE_CONVERSION) {
            // For non-TYPE_CONVERSION rules, the source and target types should match.
            // If they differ AND one is an unsafe type, the rule cannot be executed deterministically.
            if (!typesMatch(srcType, tgtType) && (isUnsafeType(srcType) || isUnsafeType(tgtType))) {
                errors.add("Unsafe: rule involves incompatible types where one side is object/array: "
                        + srcType + " → " + tgtType
                        + ". This transformation cannot be represented deterministically.");
            }
        }

        switch (rule.getOperation()) {
            case TYPE_CONVERSION -> {
                if (isUnsafeType(srcType) || isUnsafeType(tgtType)) {
                    errors.add("TYPE_CONVERSION involving object or array types is unsafe: "
                            + srcType + " → " + tgtType);
                } else if (!isSafeTypeConversion(srcType, tgtType)) {
                    errors.add("Unsupported type conversion: " + srcType + " → " + tgtType);
                }
            }
            case ENUM_MAPPING -> {
                // Verify the parameters contain a non-empty mappings object
                if (isBlank(rule.getParameters()) || "{}".equals(rule.getParameters().trim())) {
                    errors.add("ENUM_MAPPING rule has no mappings in parameters — " +
                            "cannot generate without explicit schema enum metadata");
                } else {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = objectMapper.readValue(rule.getParameters(), Map.class);
                        Object mappings = params.get("mappings");
                        if (!(mappings instanceof Map) || ((Map<?,?>) mappings).isEmpty()) {
                            errors.add("ENUM_MAPPING parameters contain no valid 'mappings' entries");
                        }
                    } catch (Exception e) {
                        errors.add("ENUM_MAPPING parameters JSON is malformed: " + e.getMessage());
                    }
                }
            }
            case COMBINE -> {
                // COMBINE requires sourcePaths to be present
                if (isBlank(rule.getSourcePaths())) {
                    errors.add("COMBINE rule requires sourcePaths but they are missing");
                }
            }
            case SPLIT -> {
                // SPLIT requires targetPaths to be present
                if (isBlank(rule.getTargetPaths())) {
                    errors.add("SPLIT rule requires targetPaths but they are missing");
                }
            }
            default -> { /* RENAME, MOVE, FLATTEN: no extra validation */ }
        }

        return errors;
    }

    // ══════════════════════════════════════════════════════════
    // Enum mapping helpers
    // ══════════════════════════════════════════════════════════

    /**
     * Check whether a SchemaField has enum values available in its description.
     *
     * <p>Phase 4 stores enum values as a comma-separated list in the {@code description}
     * field of a SchemaField if the schema specifies an {@code enum} array.
     * We detect this by looking for common enum value patterns.
     *
     * <p>This is intentionally conservative — it only returns true when there is clear
     * evidence of enumerated values, not when the field merely has a description.
     */
    public boolean hasEnumValues(SchemaField field) {
        String desc = field.getDescription();
        if (desc == null || desc.isBlank()) return false;
        // Enum values stored as comma-separated: "male,female" or "M,F"
        // or in bracket notation: "[male, female]"
        String trimmed = desc.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // JSON-array-like notation from schema parser
            return trimmed.length() > 2;
        }
        // Comma-separated single-token list
        String[] parts = trimmed.split(",");
        if (parts.length < 2) return false;
        // Each part should be a short token (not a long sentence)
        for (String part : parts) {
            if (part.trim().length() > 50) return false; // looks like a description, not an enum
        }
        return true;
    }

    /**
     * Build a deterministic enum value mapping from source to target schema metadata.
     *
     * <p>Only creates entries when both sides have equal-length enum value lists
     * and the values can be paired positionally. This is a conservative approach:
     * if the lists don't align, no mapping is produced (the caller should then
     * fall back to RENAME or not generate ENUM_MAPPING at all).
     *
     * @return map from source enum value → target enum value (empty if not deterministic)
     */
    public Map<String, String> buildEnumMapping(SchemaField srcField, SchemaField tgtField) {
        List<String> srcValues = parseEnumValues(srcField.getDescription());
        List<String> tgtValues = parseEnumValues(tgtField.getDescription());

        if (srcValues.isEmpty() || tgtValues.isEmpty()) return Collections.emptyMap();
        if (srcValues.size() != tgtValues.size()) {
            // Unequal lengths → cannot create a deterministic 1-to-1 mapping
            log.debug("  ENUM_MAPPING skipped — unequal enum list lengths: {} vs {}",
                    srcValues.size(), tgtValues.size());
            return Collections.emptyMap();
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < srcValues.size(); i++) {
            mapping.put(srcValues.get(i), tgtValues.get(i));
        }
        return mapping;
    }

    private List<String> parseEnumValues(String description) {
        if (description == null || description.isBlank()) return Collections.emptyList();
        String trimmed = description.trim();
        // Handle JSON-array notation: ["male", "female"] or [male, female]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("^\"|\"$", "")) // strip surrounding quotes
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════
    // Type helpers
    // ══════════════════════════════════════════════════════════

    /**
     * Returns true if the conversion from srcType to tgtType is in the safe matrix.
     */
    public boolean isSafeTypeConversion(String srcType, String tgtType) {
        if (srcType == null || tgtType == null) return false;
        Set<String> safeTargets = SAFE_TYPE_CONVERSIONS.get(srcType.toLowerCase());
        return safeTargets != null && safeTargets.contains(tgtType.toLowerCase());
    }

    private boolean isUnsafeType(String type) {
        if (type == null) return false;
        return "object".equalsIgnoreCase(type) || "array".equalsIgnoreCase(type);
    }

    private boolean typesMatch(String t1, String t2) {
        if (t1 == null && t2 == null) return true;
        if (t1 == null || t2 == null) return false;
        return t1.equalsIgnoreCase(t2);
    }

    // ══════════════════════════════════════════════════════════
    // Path helpers
    // ══════════════════════════════════════════════════════════

    /** Returns true if the path contains a '.' (i.e., it is a nested field path). */
    public boolean isNested(String path) {
        return path != null && !path.isBlank() && path.contains(".");
    }

    // ══════════════════════════════════════════════════════════
    // General helpers
    // ══════════════════════════════════════════════════════════

    private String normalizeNull(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
