package com.semanticapi.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.repository.*;
import com.semanticapi.service.CompatibilityEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompatibilityEngineService}.
 *
 * <p>All JPA repositories are mocked; no database or Spring context needed.
 * Tests verify:
 * <ul>
 *   <li>Score calculation (weighted field coverage)</li>
 *   <li>COMPATIBLE / PARTIALLY_COMPATIBLE / INCOMPATIBLE classification</li>
 *   <li>LOW_CONFIDENCE candidates never count as accepted</li>
 *   <li>Required fields have 2× weight</li>
 *   <li>Unmatched required fields penalise score</li>
 *   <li>Error cases (session not completed, session not found)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompatibilityEngineService")
class CompatibilityEngineServiceTest {

    @Mock MatchSessionRepository              sessionRepository;
    @Mock MatchCandidateRepository            candidateRepository;
    @Mock SchemaFieldRepository               schemaFieldRepository;
    @Mock EndpointRepository                  endpointRepository;
    @Mock CompatibilityReportRepository       reportRepository;
    @Mock EndpointCompatibilityResultRepository epResultRepository;

    private CompatibilityEngineService service;

    @BeforeEach
    void setUp() {
        service = new CompatibilityEngineService(
                sessionRepository, candidateRepository, schemaFieldRepository,
                endpointRepository, reportRepository, epResultRepository, new ObjectMapper());
    }

    // ── Helpers ───────────────────────────────────────────────

    private MatchSession completedSession(long srcVerId, long tgtVerId) {
        ApiVersion srcVer = new ApiVersion(); srcVer.setVersion("V1");
        ApiVersion tgtVer = new ApiVersion(); tgtVer.setVersion("V2");
        setId(srcVer, srcVerId); setId(tgtVer, tgtVerId);

        MatchSession s = new MatchSession();
        s.setSourceVersion(srcVer);
        s.setTargetVersion(tgtVer);
        s.setStatus(MatchSession.Status.COMPLETED);
        setId(s, 1L);
        return s;
    }

    private Endpoint endpoint(long id, long versionId, String method, String path) {
        ApiVersion ver = new ApiVersion(); setId(ver, versionId);
        Endpoint ep = new Endpoint(); ep.setHttpMethod(method); ep.setPath(path);
        ep.setApiVersion(ver); setId(ep, id);
        return ep;
    }

    private SchemaField field(long id, long endpointId, String name, String path,
                               String type, boolean required) {
        Endpoint ep = new Endpoint(); setId(ep, endpointId);
        SchemaField sf = new SchemaField();
        sf.setFieldName(name); sf.setFieldPath(path);
        sf.setFieldType(type); sf.setRequired(required);
        sf.setEndpoint(ep); sf.setSchemaType("REQUEST");
        setId(sf, id);
        return sf;
    }

    private MatchCandidate candidate(SchemaField src, SchemaField tgt,
                                     double confidence,
                                     MatchCandidate.ApprovalStatus status) {
        MatchSession session = new MatchSession(); setId(session, 1L);
        MatchCandidate c = new MatchCandidate();
        c.setSession(session); c.setSourceField(src); c.setTargetField(tgt);
        c.setConfidence(confidence); c.setApprovalStatus(status);
        c.setSourcePath(src.getFieldPath()); c.setTargetPath(tgt.getFieldPath());
        return c;
    }

    /** Reflectively sets the id field (avoids needing setId in entities). */
    private void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CompatibilityReport savedReport(long id) {
        CompatibilityReport r = new CompatibilityReport();
        setId(r, id);
        return r;
    }

    // ── Error cases ───────────────────────────────────────────

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Session not found → ResourceNotFoundException")
        void sessionNotFound() {
            when(sessionRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.analyzeSession(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Session not COMPLETED → IllegalStateException")
        void sessionNotCompleted() {
            MatchSession s = new MatchSession();
            s.setStatus(MatchSession.Status.RUNNING);
            setId(s, 1L);
            // Need version stubs for toString in error message
            ApiVersion v = new ApiVersion(); v.setVersion("V1"); setId(v, 1L);
            s.setSourceVersion(v); s.setTargetVersion(v);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(s));
            assertThatThrownBy(() -> service.analyzeSession(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RUNNING");
        }
    }

    // ── Score calculation ─────────────────────────────────────

    @Nested
    @DisplayName("Score and classification")
    class ScoreCalculation {

        /**
         * Setup: 2 source fields (both matched with confidence=0.95)
         * → score = (0.95×1 + 0.95×1) / (1+1) = 0.95 → COMPATIBLE
         */
        @Test
        @DisplayName("All optional fields matched high confidence → COMPATIBLE")
        void allMatched_compatible() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1/item");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2/item");

            SchemaField sf1 = field(101L, 10L, "name",  "name",  "string", false);
            SchemaField sf2 = field(102L, 10L, "price", "price", "number", false);
            SchemaField tf1 = field(201L, 20L, "title", "title", "string", false);
            SchemaField tf2 = field(202L, 20L, "cost",  "cost",  "number", false);

            MatchCandidate c1 = candidate(sf1, tf1, 0.95, MatchCandidate.ApprovalStatus.AUTO_APPROVED);
            MatchCandidate c2 = candidate(sf2, tf2, 0.95, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            stubSession(1L, 10L, 20L, List.of(c1, c2));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sf1, sf2));
            stubTargetFields(20L, List.of(tf1, tf2));

            CompatibilityReport report = savedReport(1L);
            when(reportRepository.save(any())).thenReturn(report);

            service.analyzeSession(1L);

            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                EndpointCompatibilityResult r = list.get(0);
                return r.getCompatibilityScore() >= 0.90
                       && r.getCompatibilityStatus() == EndpointCompatibilityResult.CompatibilityStatus.COMPATIBLE;
            }));
        }

        /**
         * Setup: 2 required fields, one matched (confidence=0.80), one unmatched
         * → numerator=0.80×2=1.60, denominator=2×2=4 → score=0.40 → INCOMPATIBLE
         */
        @Test
        @DisplayName("One required field unmatched → penalizes score → INCOMPATIBLE")
        void unmatchedRequired_incompatible() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1/item");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2/item");

            SchemaField sf1 = field(101L, 10L, "id",   "id",   "integer", true);  // required
            SchemaField sf2 = field(102L, 10L, "name", "name", "string",  true);  // required
            SchemaField tf1 = field(201L, 20L, "id",   "id",   "integer", true);

            // sf1 matched, sf2 unmatched
            MatchCandidate c1 = candidate(sf1, tf1, 0.80, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            stubSession(1L, 10L, 20L, List.of(c1));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sf1, sf2));
            stubTargetFields(20L, List.of(tf1));

            CompatibilityReport report = savedReport(1L);
            when(reportRepository.save(any())).thenReturn(report);

            service.analyzeSession(1L);

            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                EndpointCompatibilityResult r = list.get(0);
                // score = (0.80×2 + 0×2) / (2+2) = 1.6/4 = 0.40
                return r.getCompatibilityScore() < 0.70
                       && r.getCompatibilityStatus() == EndpointCompatibilityResult.CompatibilityStatus.INCOMPATIBLE
                       && r.getUnmatchedRequiredFields() == 1;
            }));
        }

        /**
         * Setup: 2 optional fields matched at confidence=0.75
         * → score = (0.75+0.75)/2 = 0.75 → PARTIALLY_COMPATIBLE
         */
        @Test
        @DisplayName("All optional fields matched at moderate confidence → PARTIALLY_COMPATIBLE")
        void moderateMatch_partial() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2");

            SchemaField sf1 = field(101L, 10L, "a", "a", "string", false);
            SchemaField sf2 = field(102L, 10L, "b", "b", "string", false);
            SchemaField tf1 = field(201L, 20L, "x", "x", "string", false);
            SchemaField tf2 = field(202L, 20L, "y", "y", "string", false);

            MatchCandidate c1 = candidate(sf1, tf1, 0.75, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);
            MatchCandidate c2 = candidate(sf2, tf2, 0.75, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            stubSession(1L, 10L, 20L, List.of(c1, c2));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sf1, sf2));
            stubTargetFields(20L, List.of(tf1, tf2));

            when(reportRepository.save(any())).thenReturn(savedReport(1L));

            service.analyzeSession(1L);

            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                EndpointCompatibilityResult r = list.get(0);
                return r.getCompatibilityScore() >= 0.70 && r.getCompatibilityScore() < 0.90
                       && r.getCompatibilityStatus() == EndpointCompatibilityResult.CompatibilityStatus.PARTIALLY_COMPATIBLE;
            }));
        }
    }

    // ── LOW_CONFIDENCE safety ─────────────────────────────────

    @Nested
    @DisplayName("LOW_CONFIDENCE candidates are excluded")
    class LowConfidenceSafety {

        @Test
        @DisplayName("LOW_CONFIDENCE candidate does NOT count as a match")
        void lowConfidence_notAccepted() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2");

            SchemaField sf = field(101L, 10L, "name", "name", "string", false);
            SchemaField tf = field(201L, 20L, "x",    "x",    "string", false);

            // LOW_CONFIDENCE — must NOT be accepted
            MatchCandidate c = candidate(sf, tf, 0.50, MatchCandidate.ApprovalStatus.LOW_CONFIDENCE);

            stubSession(1L, 10L, 20L, List.of(c));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sf));
            stubTargetFields(20L, List.of(tf));

            when(reportRepository.save(any())).thenReturn(savedReport(1L));

            service.analyzeSession(1L);

            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                EndpointCompatibilityResult r = list.get(0);
                // LOW_CONFIDENCE → unmatched → score=0
                return r.getCompatibilityScore() == 0.0
                       && r.getMatchedOptionalFields() == 0;
            }));
        }

        @Test
        @DisplayName("REJECTED candidate does NOT count as a match")
        void rejected_notAccepted() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2");

            SchemaField sf = field(101L, 10L, "email", "email", "string", false);
            SchemaField tf = field(201L, 20L, "email", "email", "string", false);

            MatchCandidate c = candidate(sf, tf, 0.99, MatchCandidate.ApprovalStatus.REJECTED);

            stubSession(1L, 10L, 20L, List.of(c));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sf));
            stubTargetFields(20L, List.of(tf));

            when(reportRepository.save(any())).thenReturn(savedReport(1L));

            service.analyzeSession(1L);

            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                return list.get(0).getCompatibilityScore() == 0.0;
            }));
        }
    }

    // ── Required vs optional weight ───────────────────────────

    @Nested
    @DisplayName("Required fields have 2× weight")
    class RequiredWeight {

        @Test
        @DisplayName("Required field weight=2 produces lower score when unmatched vs optional")
        void requiredWeightHigher() {
            Endpoint srcEp = endpoint(10L, 1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(20L, 2L, "POST", "/v2");

            // 1 required matched + 1 optional unmatched
            SchemaField sfReq = field(101L, 10L, "id",   "id",   "integer", true);   // weight=2
            SchemaField sfOpt = field(102L, 10L, "note", "note", "string",  false);  // weight=1
            SchemaField tf    = field(201L, 20L, "id",   "id",   "integer", true);

            MatchCandidate c = candidate(sfReq, tf, 1.0, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            stubSession(1L, 10L, 20L, List.of(c));
            stubEndpoints(10L, srcEp, 20L, tgtEp);
            stubSourceFields(10L, List.of(sfReq, sfOpt));
            stubTargetFields(20L, List.of(tf));

            when(reportRepository.save(any())).thenReturn(savedReport(1L));
            service.analyzeSession(1L);

            // score = (1.0×2 + 0×1) / (2+1) = 2/3 ≈ 0.667 → INCOMPATIBLE
            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) -> {
                if (list.isEmpty()) return false;
                double score = list.get(0).getCompatibilityScore();
                return score > 0.60 && score < 0.70;
            }));
        }
    }

    // ── Multiple endpoints ────────────────────────────────────

    @Nested
    @DisplayName("Multiple endpoint pairs")
    class MultipleEndpoints {

        @Test
        @DisplayName("Multiple endpoint pairs are analyzed independently")
        void multipleEndpoints_analyzedIndependently() {
            Endpoint srcEp1 = endpoint(10L, 1L, "POST", "/v1/a");
            Endpoint tgtEp1 = endpoint(20L, 2L, "POST", "/v2/a");
            Endpoint srcEp2 = endpoint(11L, 1L, "GET",  "/v1/b");
            Endpoint tgtEp2 = endpoint(21L, 2L, "GET",  "/v2/b");

            SchemaField sf1 = field(101L, 10L, "x", "x", "string", false);
            SchemaField tf1 = field(201L, 20L, "x", "x", "string", false);
            SchemaField sf2 = field(102L, 11L, "y", "y", "string", false);
            SchemaField tf2 = field(202L, 21L, "y", "y", "string", false);

            MatchCandidate c1 = candidate(sf1, tf1, 0.95, MatchCandidate.ApprovalStatus.AUTO_APPROVED);
            MatchCandidate c2 = candidate(sf2, tf2, 0.75, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            // Session mock
            MatchSession session = completedSession(1L, 2L);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(candidateRepository.findBySessionIdWithEndpoints(1L))
                    .thenReturn(List.of(c1, c2));
            when(endpointRepository.findById(10L)).thenReturn(Optional.of(srcEp1));
            when(endpointRepository.findById(20L)).thenReturn(Optional.of(tgtEp1));
            when(endpointRepository.findById(11L)).thenReturn(Optional.of(srcEp2));
            when(endpointRepository.findById(21L)).thenReturn(Optional.of(tgtEp2));
            when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(10L, "REQUEST"))
                    .thenReturn(List.of(sf1));
            when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(20L, "REQUEST"))
                    .thenReturn(List.of(tf1));
            when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(11L, "REQUEST"))
                    .thenReturn(List.of(sf2));
            when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(21L, "REQUEST"))
                    .thenReturn(List.of(tf2));
            when(reportRepository.save(any())).thenReturn(savedReport(1L));

            service.analyzeSession(1L);

            // Should have produced 2 endpoint results
            verify(epResultRepository).saveAll(argThat((List<EndpointCompatibilityResult> list) ->
                    list.size() == 2
            ));
        }
    }

    // ── Stub helpers ──────────────────────────────────────────

    private void stubSession(Long sessionId, Long srcEpId, Long tgtEpId, List<MatchCandidate> candidates) {
        MatchSession session = completedSession(srcEpId, tgtEpId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(candidateRepository.findBySessionIdWithEndpoints(sessionId)).thenReturn(candidates);
    }

    private void stubEndpoints(Long srcId, Endpoint srcEp, Long tgtId, Endpoint tgtEp) {
        when(endpointRepository.findById(srcId)).thenReturn(Optional.of(srcEp));
        when(endpointRepository.findById(tgtId)).thenReturn(Optional.of(tgtEp));
    }

    private void stubSourceFields(Long endpointId, List<SchemaField> fields) {
        when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(endpointId, "REQUEST"))
                .thenReturn(fields);
    }

    private void stubTargetFields(Long endpointId, List<SchemaField> fields) {
        when(schemaFieldRepository.findByEndpointIdAndSchemaTypeOrderByFieldPathAsc(endpointId, "REQUEST"))
                .thenReturn(fields);
    }
}
