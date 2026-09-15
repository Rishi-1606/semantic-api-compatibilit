package com.semanticapi.service;

import com.semanticapi.dto.CompatibilityAnalysisRequest;
import com.semanticapi.dto.MatchCandidateResponse;
import com.semanticapi.dto.MatchSessionResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.matching.*;
import com.semanticapi.model.*;
import com.semanticapi.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Orchestration service for Phase 6 — Semantic Matching.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Validate source and target API versions exist.</li>
 *   <li>Load Phase 4 schema fields + Phase 5 NLP results for each version's endpoints.</li>
 *   <li>Obtain the active {@link EmbeddingProvider} from {@link EmbeddingProviderFactory}.</li>
 *   <li>Embed all normalized field names and compute pairwise cosine similarity.</li>
 *   <li>Apply type_factor and path_factor context penalties.</li>
 *   <li>Compute confidence = cosine × type_factor × path_factor.</li>
 *   <li>Assign approval status based on configured thresholds.</li>
 *   <li>Persist the session and all candidates.</li>
 *   <li>Compute overall_score and compatibility classification.</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <p>Low-confidence candidates (below review_threshold) are marked LOW_CONFIDENCE,
 * not automatically approved.  Phase 7 and Phase 9 must check approval_status before
 * acting on any candidate.
 */
@Service
public class SemanticMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatchingService.class);

    private final ApiVersionRepository       versionRepository;
    private final EndpointRepository         endpointRepository;
    private final SchemaFieldRepository      schemaFieldRepository;
    private final NlpFieldResultRepository   nlpRepository;
    private final MatchSessionRepository     sessionRepository;
    private final MatchCandidateRepository   candidateRepository;
    private final EmbeddingProviderFactory   providerFactory;
    private final SemanticMatcher            matcher;
    private final EmbeddingConfig            config;

    public SemanticMatchingService(ApiVersionRepository versionRepository,
                                   EndpointRepository endpointRepository,
                                   SchemaFieldRepository schemaFieldRepository,
                                   NlpFieldResultRepository nlpRepository,
                                   MatchSessionRepository sessionRepository,
                                   MatchCandidateRepository candidateRepository,
                                   EmbeddingProviderFactory providerFactory,
                                   SemanticMatcher matcher,
                                   EmbeddingConfig config) {
        this.versionRepository   = versionRepository;
        this.endpointRepository  = endpointRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.nlpRepository       = nlpRepository;
        this.sessionRepository   = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.providerFactory     = providerFactory;
        this.matcher             = matcher;
        this.config              = config;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Start a new semantic matching session between two API versions.
     *
     * @param request contains sourceVersionId and targetVersionId
     * @return the completed (or failed) session response
     */
    @Transactional
    public MatchSessionResponse analyze(CompatibilityAnalysisRequest request) {
        ApiVersion srcVersion = versionRepository.findById(request.getSourceVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("ApiVersion", request.getSourceVersionId()));
        ApiVersion tgtVersion = versionRepository.findById(request.getTargetVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("ApiVersion", request.getTargetVersionId()));

        // Create session record
        MatchSession session = new MatchSession();
        session.setSourceVersion(srcVersion);
        session.setTargetVersion(tgtVersion);
        session.setStatus(MatchSession.Status.RUNNING);
        session = sessionRepository.save(session);

        try {
            return runAnalysis(session, request);
        } catch (Exception e) {
            session.setStatus(MatchSession.Status.FAILED);
            session.setErrorMessage(e.getMessage());
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            log.error("Semantic matching failed for session {}: {}", session.getId(), e.getMessage(), e);
            return buildResponse(session, List.of());
        }
    }

    /**
     * Retrieve a previously completed session by ID.
     */
    @Transactional(readOnly = true)
    public MatchSessionResponse getSession(Long sessionId) {
        MatchSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchSession", sessionId));
        List<MatchCandidate> candidates = candidateRepository.findBySessionIdOrderByConfidenceDesc(sessionId);
        List<MatchCandidateResponse> candidateDtos = candidates.stream()
                .map(MatchCandidateResponse::from).toList();
        return buildResponse(session, candidateDtos);
    }

    /**
     * List all sessions involving a specific API version (as source or target).
     */
    @Transactional(readOnly = true)
    public List<MatchSessionResponse> getSessionsForVersion(Long versionId) {
        return sessionRepository
                .findBySourceVersionIdOrTargetVersionIdOrderByCreatedAtDesc(versionId, versionId)
                .stream()
                .map(s -> buildResponse(s, List.of()))
                .toList();
    }

    // ── Core Analysis ─────────────────────────────────────────

    private MatchSessionResponse runAnalysis(MatchSession session,
                                              CompatibilityAnalysisRequest request) {
        Long srcVersionId = session.getSourceVersion().getId();
        Long tgtVersionId = session.getTargetVersion().getId();

        // ── Load NLP results for both versions ────────────────
        List<NlpFieldResult> srcNlp = loadNlpForVersion(srcVersionId, request.getSourceEndpointId());
        List<NlpFieldResult> tgtNlp = loadNlpForVersion(tgtVersionId, request.getTargetEndpointId());

        log.info("Session {}: {} source fields × {} target fields",
                session.getId(), srcNlp.size(), tgtNlp.size());

        if (srcNlp.isEmpty() || tgtNlp.isEmpty()) {
            session.setStatus(MatchSession.Status.FAILED);
            session.setErrorMessage(
                    "NLP results not found. Run schema analysis (Phase 4) and NLP analysis (Phase 5) first.");
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            return buildResponse(session, List.of());
        }

        // ── Get embedding provider ────────────────────────────
        EmbeddingProvider provider = providerFactory.getProvider();
        session.setEmbeddingProvider(provider.providerName());
        session.setEmbeddingModel(provider.modelName());

        // ── Run matching ──────────────────────────────────────
        List<SemanticMatcher.CandidatePair> pairs = matcher.match(srcNlp, tgtNlp, provider);

        // Detect if fallback was actually used (provider name changes)
        // If first embed call used fallback, update the session provider name
        if (!pairs.isEmpty()) {
            // Use the actual provider that was used (check if it differs from configured)
            boolean usedFallback = "lexical-fallback".equals(provider.providerName())
                    || "lexical-fallback".equalsIgnoreCase(config.getProvider());
            if (usedFallback) {
                session.setEmbeddingProvider("lexical-fallback");
                session.setEmbeddingModel("token-hash-bucket-512");
            }
        }

        // ── Persist candidates ───────────────────────────────
        List<MatchCandidate> candidates = new ArrayList<>(pairs.size());
        for (SemanticMatcher.CandidatePair pair : pairs) {
            MatchCandidate c = new MatchCandidate();
            c.setSession(session);
            c.setSourceField(pair.sourceField());
            c.setTargetField(pair.targetField());
            c.setSourcePath(pair.sourceField().getFieldPath());
            c.setTargetPath(pair.targetField().getFieldPath());
            c.setSourceNormalized(pair.sourceNormalized());
            c.setTargetNormalized(pair.targetNormalized());
            c.setCosineSimilarity(pair.cosineSimilarity());
            c.setTypeFactor(pair.typeFactor());
            c.setPathFactor(pair.pathFactor());
            c.setConfidence(pair.confidence());
            c.setApprovalStatus(resolveApprovalStatus(pair.confidence()));
            if ("lexical-fallback".equals(session.getEmbeddingProvider())) {
                c.setNotes("Computed by lexical-fallback provider (token overlap). " +
                           "Run with Ollama for true semantic similarity.");
            }
            candidates.add(c);
        }
        candidateRepository.saveAll(candidates);

        // ── Overall score & compatibility classification ───────
        // Overall score = average of the BEST target-match confidence per source field
        // (avoids penalizing for low-scoring pairs that are expected to be dissimilar)
        double overallScore = computeOverallScore(pairs, srcNlp.size());

        session.setOverallScore(Math.round(overallScore * 1000.0) / 1000.0);
        session.setCompatibilityStatus(classify(overallScore));
        session.setFieldPairCount(pairs.size());
        session.setStatus(MatchSession.Status.COMPLETED);
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);

        log.info("Session {} COMPLETED: {} pairs, overallScore={}, status={}",
                session.getId(), pairs.size(), session.getOverallScore(), session.getCompatibilityStatus());

        List<MatchCandidateResponse> candidateDtos = candidates.stream()
                .map(MatchCandidateResponse::from).toList();
        return buildResponse(session, candidateDtos);
    }

    /**
     * Load NLP results for all endpoints of a version (or a single endpoint).
     * Filters to REQUEST schema type only — we match request fields by default.
     * Both REQUEST and RESPONSE are included.
     */
    private List<NlpFieldResult> loadNlpForVersion(Long versionId, Long specificEndpointId) {
        List<Long> endpointIds;
        if (specificEndpointId != null) {
            endpointIds = List.of(specificEndpointId);
        } else {
            endpointIds = endpointRepository.findByApiVersionIdOrderByPathAsc(versionId)
                    .stream().map(e -> e.getId()).toList();
        }

        List<NlpFieldResult> all = new ArrayList<>();
        for (Long epId : endpointIds) {
            all.addAll(nlpRepository.findBySchemaFieldEndpointIdOrderBySchemaFieldFieldPathAsc(epId));
        }
        return all;
    }

    /**
     * Best-match average: for each source field, take the highest-confidence
     * target match; average those.  This avoids punishing the overall score
     * for the many low-similarity cross-product pairs.
     */
    private double computeOverallScore(List<SemanticMatcher.CandidatePair> pairs, int srcFieldCount) {
        if (pairs.isEmpty() || srcFieldCount == 0) return 0.0;

        // Group by source field id and take max per source
        java.util.Map<Long, Double> bestBySource = new java.util.HashMap<>();
        for (SemanticMatcher.CandidatePair p : pairs) {
            Long srcId = p.sourceField().getId();
            bestBySource.merge(srcId, p.confidence(), Math::max);
        }

        OptionalDouble avg = bestBySource.values().stream()
                .mapToDouble(Double::doubleValue).average();
        return avg.orElse(0.0);
    }

    private MatchSession.CompatibilityStatus classify(double score) {
        if (score >= config.getAutoApproveThreshold()) return MatchSession.CompatibilityStatus.COMPATIBLE;
        if (score >= config.getReviewThreshold())      return MatchSession.CompatibilityStatus.PARTIALLY_COMPATIBLE;
        return MatchSession.CompatibilityStatus.INCOMPATIBLE;
    }

    private MatchCandidate.ApprovalStatus resolveApprovalStatus(double confidence) {
        if (confidence >= config.getAutoApproveThreshold()) return MatchCandidate.ApprovalStatus.AUTO_APPROVED;
        if (confidence >= config.getReviewThreshold())      return MatchCandidate.ApprovalStatus.REQUIRES_REVIEW;
        return MatchCandidate.ApprovalStatus.LOW_CONFIDENCE;
    }

    private MatchSessionResponse buildResponse(MatchSession session, List<MatchCandidateResponse> candidates) {
        return MatchSessionResponse.from(session, candidates);
    }
}
