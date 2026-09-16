package com.semanticapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 7 — Compatibility Engine.
 *
 * <p>Consumes Phase 6 {@link MatchCandidate} records from a completed
 * {@link MatchSession} and produces a {@link CompatibilityReport} with
 * per-endpoint {@link EndpointCompatibilityResult}s.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Load all candidates for the session with endpoint context.</li>
 *   <li>Group candidates by (source_endpoint_id, target_endpoint_id).</li>
 *   <li>For each endpoint pair:
 *       <ul>
 *         <li>Load all source schema fields for the endpoint.</li>
 *         <li>For each source field, find the best accepted candidate
 *             targeting any field in the target endpoint.</li>
 *         <li>Compute endpoint score using field weights.</li>
 *         <li>Build the matched/unmatched field lists.</li>
 *         <li>Classify the endpoint pair.</li>
 *       </ul>
 *   </li>
 *   <li>Compute overall score = weighted average of endpoint scores (weighted by endpoint field count).</li>
 *   <li>Classify overall compatibility using spec thresholds.</li>
 *   <li>Persist report + endpoint results.</li>
 * </ol>
 *
 * <h2>Score formula (Phase 7 — distinct from Phase 6 cosine)</h2>
 * <pre>
 *   endpoint_score =
 *     Σ( best_accepted_confidence(src_field) × field_weight(src_field) )
 *     ────────────────────────────────────────────────────────────────
 *     Σ( field_weight(src_field) )
 *
 *   field_weight(f) = 2.0 if required else 1.0
 *   accepted        = approval_status IN {AUTO_APPROVED, REQUIRES_REVIEW}
 *   unmatched_field → contributes 0 to numerator, full weight to denominator
 * </pre>
 *
 * <h2>Classification (spec § 6.7, § 7)</h2>
 * <pre>
 *   score ≥ 0.90 → COMPATIBLE
 *   score ≥ 0.70 → PARTIALLY_COMPATIBLE
 *   score  < 0.70 → INCOMPATIBLE
 * </pre>
 *
 * <h2>Safety</h2>
 * <p>LOW_CONFIDENCE and REJECTED candidates are never used as accepted matches.
 * They are counted separately for reporting but never improve the score.
 */
@Service
public class CompatibilityEngineService {

    private static final Logger log = LoggerFactory.getLogger(CompatibilityEngineService.class);

    private static final double REQUIRED_WEIGHT = 2.0;
    private static final double OPTIONAL_WEIGHT = 1.0;

    // Spec § 6.7 / § 7 thresholds (also driven by EmbeddingConfig)
    private static final double COMPATIBLE_THRESHOLD  = 0.90;
    private static final double PARTIAL_THRESHOLD     = 0.70;

    private final MatchSessionRepository              sessionRepository;
    private final MatchCandidateRepository            candidateRepository;
    private final SchemaFieldRepository               schemaFieldRepository;
    private final EndpointRepository                  endpointRepository;
    private final CompatibilityReportRepository       reportRepository;
    private final EndpointCompatibilityResultRepository epResultRepository;
    private final ObjectMapper                        objectMapper;

    public CompatibilityEngineService(
            MatchSessionRepository sessionRepository,
            MatchCandidateRepository candidateRepository,
            SchemaFieldRepository schemaFieldRepository,
            EndpointRepository endpointRepository,
            CompatibilityReportRepository reportRepository,
            EndpointCompatibilityResultRepository epResultRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository   = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.endpointRepository  = endpointRepository;
        this.reportRepository    = reportRepository;
        this.epResultRepository  = epResultRepository;
        this.objectMapper        = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Run compatibility analysis on a completed Phase 6 session.
     *
     * @param sessionId the {@link MatchSession#getId()} produced by Phase 6
     * @return the persisted {@link CompatibilityReport}
     */
    @Transactional
    public CompatibilityReport analyzeSession(Long sessionId) {
        MatchSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchSession", sessionId));

        if (session.getStatus() != MatchSession.Status.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot analyze session " + sessionId + " — status is " + session.getStatus()
                    + ". Phase 6 must complete successfully first.");
        }

        log.info("Compatibility Engine: analyzing session {} ({} → {})",
                sessionId,
                session.getSourceVersion().getVersion(),
                session.getTargetVersion().getVersion());

        // ── 1. Load all candidates with endpoint context ───────
        List<MatchCandidate> allCandidates =
                candidateRepository.findBySessionIdWithEndpoints(sessionId);

        log.debug("  loaded {} candidates", allCandidates.size());

        // ── 2. Group by (src_endpoint_id, tgt_endpoint_id) ───
        Map<String, List<MatchCandidate>> grouped = groupByEndpointPair(allCandidates);

        // ── 3. Create report shell ─────────────────────────────────────────────
        // NOTE: compatibility_status is NOT NULL — set a safe default before first save.
        // It will be overwritten with the real value after all endpoint pairs are processed.
        CompatibilityReport report = new CompatibilityReport();
        report.setMatchSession(session);
        report.setSourceVersion(session.getSourceVersion());
        report.setTargetVersion(session.getTargetVersion());
        report.setCompatibilityStatus(CompatibilityReport.CompatibilityStatus.INCOMPATIBLE); // default; overwritten
        report.setOverallScore(0.0);
        report = reportRepository.save(report);

        // ── 4. Process each endpoint pair ─────────────────────
        List<EndpointCompatibilityResult> endpointResults = new ArrayList<>();
        int totalFields         = 0;
        int totalMatched        = 0;
        int totalUnmatchedReq   = 0;
        int totalAutoApproved   = 0;
        int totalReqReview      = 0;
        int totalLowConf        = 0;

        // Track cumulative weighted score components
        double weightedScoreSum = 0.0;
        double totalWeightSum   = 0.0;

        for (Map.Entry<String, List<MatchCandidate>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long srcEpId = Long.parseLong(parts[0]);
            Long tgtEpId = Long.parseLong(parts[1]);

            Endpoint srcEp = endpointRepository.findById(srcEpId).orElse(null);
            Endpoint tgtEp = endpointRepository.findById(tgtEpId).orElse(null);
            if (srcEp == null || tgtEp == null) continue;

            EndpointCompatibilityResult epResult = analyzeEndpointPair(
                    srcEp, tgtEp, entry.getValue(), report);
            endpointResults.add(epResult);

            // Accumulate for overall score (weighted by number of source fields)
            int epFields = epResult.getTotalSourceFields();
            if (epFields > 0) {
                weightedScoreSum += epResult.getCompatibilityScore() * epFields;
                totalWeightSum   += epFields;
            }

            totalFields       += epResult.getTotalSourceFields();
            totalMatched      += epResult.getMatchedRequiredFields() + epResult.getMatchedOptionalFields();
            totalUnmatchedReq += epResult.getUnmatchedRequiredFields();

            // Count candidate statuses
            for (MatchCandidate c : entry.getValue()) {
                switch (c.getApprovalStatus()) {
                    case AUTO_APPROVED  -> totalAutoApproved++;
                    case REQUIRES_REVIEW -> totalReqReview++;
                    case LOW_CONFIDENCE  -> totalLowConf++;
                    default -> {}
                }
            }
        }

        epResultRepository.saveAll(endpointResults);

        // ── 5. Compute overall score & classify ───────────────
        double overallScore = totalWeightSum > 0 ? weightedScoreSum / totalWeightSum : 0.0;
        overallScore = round3(overallScore);

        CompatibilityReport.CompatibilityStatus overallStatus = classify(overallScore);
        String explanation = buildOverallExplanation(overallScore, overallStatus,
                totalFields, totalMatched, totalUnmatchedReq,
                totalAutoApproved, totalReqReview, totalLowConf,
                endpointResults.size());

        report.setCompatibilityStatus(overallStatus);
        report.setOverallScore(overallScore);
        report.setTotalSourceFields(totalFields);
        report.setMatchedFields(totalMatched);
        report.setUnmatchedRequiredFields(totalUnmatchedReq);
        report.setAutoApprovedCount(totalAutoApproved);
        report.setRequiresReviewCount(totalReqReview);
        report.setLowConfidenceCount(totalLowConf);
        report.setExplanation(explanation);
        report = reportRepository.save(report);

        log.info("Compatibility Engine: report {} → status={} score={}",
                report.getId(), report.getCompatibilityStatus(), report.getOverallScore());

        return report;
    }

    @Transactional(readOnly = true)
    public CompatibilityReport getReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("CompatibilityReport", reportId));
    }

    @Transactional(readOnly = true)
    public List<CompatibilityReport> getReportsForVersion(Long versionId) {
        return reportRepository.findByVersionId(versionId);
    }

    @Transactional(readOnly = true)
    public List<CompatibilityReport> getReportsForSession(Long sessionId) {
        return reportRepository.findBySessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public List<EndpointCompatibilityResult> getEndpointResults(Long reportId) {
        // Verify report exists
        if (!reportRepository.existsById(reportId)) {
            throw new ResourceNotFoundException("CompatibilityReport", reportId);
        }
        return epResultRepository.findByReportIdOrderByCompatibilityScoreDesc(reportId);
    }

    // ── Core endpoint-pair analysis ───────────────────────────

    private EndpointCompatibilityResult analyzeEndpointPair(
            Endpoint srcEp, Endpoint tgtEp,
            List<MatchCandidate> candidates,
            CompatibilityReport report) {

        // All REQUEST schema fields from the source endpoint
        List<SchemaField> srcRequestFields =
                schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(srcEp.getId(), "REQUEST");

        // All target field IDs in this pair (for unmatched target detection)
        Set<Long> tgtFieldIdsInCandidates = candidates.stream()
                .map(c -> c.getTargetField().getId())
                .collect(Collectors.toSet());
        Set<Long> allTargetFieldIds = schemaFieldRepository
                .findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(tgtEp.getId(), "REQUEST")
                .stream().map(SchemaField::getId).collect(Collectors.toSet());

        // Build lookup: source_field_id → best accepted candidate
        Map<Long, MatchCandidate> bestBySourceField = new HashMap<>();
        for (MatchCandidate c : candidates) {
            if (!isAccepted(c)) continue;
            Long srcFieldId = c.getSourceField().getId();
            MatchCandidate existing = bestBySourceField.get(srcFieldId);
            if (existing == null || c.getConfidence() > existing.getConfidence()) {
                bestBySourceField.put(srcFieldId, c);
            }
        }

        // Compute score using field weights
        double numerator   = 0.0;
        double denominator = 0.0;

        List<String> matchedPaths        = new ArrayList<>();
        List<String> unmatchedSrcPaths   = new ArrayList<>();
        Set<Long> matchedTargetFieldIds  = new HashSet<>();

        int matchedRequired   = 0;
        int matchedOptional   = 0;
        int unmatchedRequired = 0;
        int unmatchedOptional = 0;

        for (SchemaField srcField : srcRequestFields) {
            double weight = srcField.isRequired() ? REQUIRED_WEIGHT : OPTIONAL_WEIGHT;
            denominator += weight;

            MatchCandidate best = bestBySourceField.get(srcField.getId());
            if (best != null) {
                numerator += best.getConfidence() * weight;
                matchedPaths.add(srcField.getFieldPath() + " → " + best.getTargetPath());
                matchedTargetFieldIds.add(best.getTargetField().getId());
                if (srcField.isRequired()) matchedRequired++; else matchedOptional++;
            } else {
                unmatchedSrcPaths.add(srcField.getFieldPath());
                if (srcField.isRequired()) unmatchedRequired++; else unmatchedOptional++;
            }
        }

        // Unmatched target paths: target fields not covered by any accepted match
        List<String> unmatchedTgtPaths = schemaFieldRepository
                .findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(tgtEp.getId(), "REQUEST")
                .stream()
                .filter(f -> !matchedTargetFieldIds.contains(f.getId()))
                .map(SchemaField::getFieldPath)
                .toList();

        double score = denominator > 0 ? round3(numerator / denominator) : 0.0;
        EndpointCompatibilityResult.CompatibilityStatus epStatus = classifyEndpoint(score);

        String explanation = buildEndpointExplanation(epStatus, score, srcEp, tgtEp,
                srcRequestFields.size(), matchedRequired, unmatchedRequired,
                matchedOptional, unmatchedOptional, matchedPaths, unmatchedSrcPaths);

        EndpointCompatibilityResult result = new EndpointCompatibilityResult();
        result.setReport(report);
        result.setSourceEndpoint(srcEp);
        result.setTargetEndpoint(tgtEp);
        result.setSourceEndpointPath(srcEp.getHttpMethod() + " " + srcEp.getPath());
        result.setTargetEndpointPath(tgtEp.getHttpMethod() + " " + tgtEp.getPath());
        result.setCompatibilityStatus(epStatus);
        result.setCompatibilityScore(score);
        result.setTotalSourceFields(srcRequestFields.size());
        result.setMatchedRequiredFields(matchedRequired);
        result.setUnmatchedRequiredFields(unmatchedRequired);
        result.setMatchedOptionalFields(matchedOptional);
        result.setUnmatchedOptionalFields(unmatchedOptional);
        result.setMatchedFieldPaths(toJson(matchedPaths));
        result.setUnmatchedSourcePaths(toJson(unmatchedSrcPaths));
        result.setUnmatchedTargetPaths(toJson(unmatchedTgtPaths));
        result.setExplanation(explanation);

        return result;
    }

    // ── Grouping ──────────────────────────────────────────────

    /**
     * Group candidates by "srcEndpointId:tgtEndpointId" key.
     * Uses the endpoint FK on each candidate's source/target SchemaField.
     */
    private Map<String, List<MatchCandidate>> groupByEndpointPair(List<MatchCandidate> candidates) {
        Map<String, List<MatchCandidate>> grouped = new LinkedHashMap<>();
        for (MatchCandidate c : candidates) {
            Long srcEpId = c.getSourceField().getEndpoint().getId();
            Long tgtEpId = c.getTargetField().getEndpoint().getId();
            String key = srcEpId + ":" + tgtEpId;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        return grouped;
    }

    // ── Helpers ───────────────────────────────────────────────

    private boolean isAccepted(MatchCandidate c) {
        return c.getApprovalStatus() == MatchCandidate.ApprovalStatus.AUTO_APPROVED
                || c.getApprovalStatus() == MatchCandidate.ApprovalStatus.REQUIRES_REVIEW;
    }

    private CompatibilityReport.CompatibilityStatus classify(double score) {
        if (score >= COMPATIBLE_THRESHOLD) return CompatibilityReport.CompatibilityStatus.COMPATIBLE;
        if (score >= PARTIAL_THRESHOLD)    return CompatibilityReport.CompatibilityStatus.PARTIALLY_COMPATIBLE;
        return CompatibilityReport.CompatibilityStatus.INCOMPATIBLE;
    }

    private EndpointCompatibilityResult.CompatibilityStatus classifyEndpoint(double score) {
        if (score >= COMPATIBLE_THRESHOLD) return EndpointCompatibilityResult.CompatibilityStatus.COMPATIBLE;
        if (score >= PARTIAL_THRESHOLD)    return EndpointCompatibilityResult.CompatibilityStatus.PARTIALLY_COMPATIBLE;
        return EndpointCompatibilityResult.CompatibilityStatus.INCOMPATIBLE;
    }

    private String buildEndpointExplanation(
            EndpointCompatibilityResult.CompatibilityStatus status, double score,
            Endpoint src, Endpoint tgt,
            int total, int matchedReq, int unmatchedReq, int matchedOpt, int unmatchedOpt,
            List<String> matched, List<String> unmatched) {
        StringBuilder sb = new StringBuilder();
        sb.append(src.getHttpMethod()).append(" ").append(src.getPath())
          .append(" → ").append(tgt.getHttpMethod()).append(" ").append(tgt.getPath())
          .append(": ").append(status.name())
          .append(" (score=").append(score).append(")");
        sb.append(". Total source fields: ").append(total);
        sb.append(". Required — matched: ").append(matchedReq)
          .append(", unmatched: ").append(unmatchedReq);
        sb.append(". Optional — matched: ").append(matchedOpt)
          .append(", unmatched: ").append(unmatchedOpt);
        if (!unmatched.isEmpty()) {
            sb.append(". Unmatched source fields: ").append(String.join(", ", unmatched));
        }
        if (unmatchedReq > 0) {
            sb.append(". WARNING: ").append(unmatchedReq)
              .append(" required field(s) have no accepted target mapping.");
        }
        return sb.toString();
    }

    private String buildOverallExplanation(
            double score, CompatibilityReport.CompatibilityStatus status,
            int totalFields, int matched, int unmatchedRequired,
            int autoApproved, int reqReview, int lowConf, int endpointCount) {
        return String.format(
            "Overall compatibility: %.1f%%. Status: %s. " +
            "Analyzed %d endpoint pair(s) covering %d source field(s). " +
            "Matched: %d. Unmatched required: %d. " +
            "Candidates — AUTO_APPROVED: %d, REQUIRES_REVIEW: %d, LOW_CONFIDENCE: %d. " +
            "Score = weighted average of per-endpoint scores (required fields weighted 2×). " +
            "Thresholds: COMPATIBLE ≥ 90%%, PARTIALLY_COMPATIBLE ≥ 70%%.",
            score * 100, status.name(),
            endpointCount, totalFields,
            matched, unmatchedRequired,
            autoApproved, reqReview, lowConf
        );
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
