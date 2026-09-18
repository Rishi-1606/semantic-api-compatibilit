package com.semanticapi.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.dto.TransformationRuleGenerationResult;
import com.semanticapi.dto.TransformationRuleResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.repository.*;
import com.semanticapi.service.TransformationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransformationRuleService}.
 *
 * <p>All JPA repositories are mocked. No database or Spring context needed.
 *
 * <h2>Covered scenarios</h2>
 * <ol>
 *   <li>RENAME — name → fullName</li>
 *   <li>MOVE/NEST — mobile → contact.phone</li>
 *   <li>FLATTEN — contact.phone → phoneNumber</li>
 *   <li>COMBINE — firstName + lastName → fullName (not generated from single candidate)</li>
 *   <li>SPLIT — fullName → firstName + lastName (not generated from single candidate)</li>
 *   <li>TYPE_CONVERSION — string → integer</li>
 *   <li>ENUM_MAPPING — with explicit schema metadata</li>
 *   <li>Missing source path</li>
 *   <li>Missing target path</li>
 *   <li>Unsupported type conversion (object → string)</li>
 *   <li>Unsafe type conversion (array → integer)</li>
 *   <li>LOW_CONFIDENCE candidate exclusion</li>
 *   <li>REJECTED candidate exclusion</li>
 *   <li>REQUIRES_REVIEW approval handling (stays GENERATED, not auto-approved)</li>
 *   <li>Multiple endpoints</li>
 *   <li>Ambiguous enum mapping (unequal lengths)</li>
 *   <li>Idempotent rule generation (delete + re-create)</li>
 *   <li>No-op exclusion (identical path AND type)</li>
 *   <li>Approve / reject lifecycle</li>
 *   <li>Approve INVALID rule throws</li>
 *   <li>Regression: report not found</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransformationRuleService")
class TransformationRuleServiceTest {

    @Mock CompatibilityReportRepository  reportRepository;
    @Mock MatchSessionRepository         sessionRepository;
    @Mock MatchCandidateRepository       candidateRepository;
    @Mock SchemaFieldRepository          schemaFieldRepository;
    @Mock EndpointRepository             endpointRepository;
    @Mock TransformationRuleRepository   ruleRepository;

    private TransformationRuleService service;

    @BeforeEach
    void setUp() {
        service = new TransformationRuleService(
                reportRepository, sessionRepository, candidateRepository,
                schemaFieldRepository, endpointRepository, ruleRepository,
                new ObjectMapper());
    }

    // ══════════════════════════════════════════════════════════
    // Test helpers
    // ══════════════════════════════════════════════════════════

    /** Reflectively set an @Id field (no setter in entities). */
    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CompatibilityReport report(long id, long sessionId) {
        ApiVersion srcVer = new ApiVersion(); srcVer.setVersion("V1"); setId(srcVer, 1L);
        ApiVersion tgtVer = new ApiVersion(); tgtVer.setVersion("V2"); setId(tgtVer, 2L);

        MatchSession session = new MatchSession();
        session.setSourceVersion(srcVer);
        session.setTargetVersion(tgtVer);
        session.setStatus(MatchSession.Status.COMPLETED);
        setId(session, sessionId);

        CompatibilityReport r = new CompatibilityReport();
        r.setMatchSession(session);
        r.setSourceVersion(srcVer);
        r.setTargetVersion(tgtVer);
        r.setCompatibilityStatus(CompatibilityReport.CompatibilityStatus.COMPATIBLE);
        r.setOverallScore(0.95);
        setId(r, id);
        return r;
    }

    private Endpoint endpoint(long id, String method, String path) {
        Endpoint ep = new Endpoint();
        ep.setHttpMethod(method);
        ep.setPath(path);
        setId(ep, id);
        return ep;
    }

    private SchemaField field(long id, Endpoint endpoint, String fieldPath, String type) {
        return field(id, endpoint, fieldPath, type, null);
    }

    private SchemaField field(long id, Endpoint endpoint, String fieldPath, String type, String description) {
        SchemaField f = new SchemaField();
        f.setEndpoint(endpoint);
        if (fieldPath != null) {
            f.setFieldName(fieldPath.contains(".") ? fieldPath.substring(fieldPath.lastIndexOf('.') + 1) : fieldPath);
            f.setFieldPath(fieldPath);
        }
        f.setFieldType(type);
        f.setDescription(description);
        setId(f, id);
        return f;
    }

    private MatchCandidate candidate(long id, SchemaField src, SchemaField tgt,
                                     double confidence, MatchCandidate.ApprovalStatus status) {
        MatchSession session = new MatchSession();
        setId(session, 10L);

        MatchCandidate c = new MatchCandidate();
        c.setSourceField(src);
        c.setTargetField(tgt);
        c.setSourcePath(src.getFieldPath());
        c.setTargetPath(tgt.getFieldPath());
        c.setConfidence(confidence);
        c.setApprovalStatus(status);
        c.setSession(session);
        setId(c, id);
        return c;
    }

    /** Stub report lookup + idempotency delete + candidate load + saveAll passthrough. */
    private void stubForGeneration(CompatibilityReport report, List<MatchCandidate> candidates) {
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        doNothing().when(ruleRepository).deleteByReportId(report.getId());
        when(candidateRepository.findBySessionIdWithEndpoints(report.getMatchSession().getId()))
                .thenReturn(candidates);
        when(ruleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ══════════════════════════════════════════════════════════
    // 1. RENAME
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. RENAME — name → fullName")
    class RenameTests {

        @Test
        @DisplayName("flat-to-flat different names → RENAME operation")
        void rename_flatToFlat_differentNames() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "name", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            assertThat(result.getRulesInvalid()).isEqualTo(0);
            assertThat(result.getSkippedNoOp()).isEqualTo(0);

            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isEqualTo("RENAME");
            assertThat(rule.getSourcePath()).isEqualTo("name");
            assertThat(rule.getTargetPath()).isEqualTo("fullName");
            assertThat(rule.getConfidence()).isEqualTo(0.96);
            assertThat(rule.getCandidateStatus()).isEqualTo("AUTO_APPROVED");
            assertThat(rule.getRuleStatus()).isEqualTo("GENERATED");
        }

        @Test
        @DisplayName("determineOperation returns RENAME for flat→flat different names")
        void determineOperation_rename() {
            SchemaField src = field(1L, endpoint(1L, "POST", "/v1"), "name", "string");
            SchemaField tgt = field(2L, endpoint(2L, "POST", "/v2"), "fullName", "string");
            TransformationRule.Operation op = service.determineOperation(
                    "name", "fullName", "string", "string", src, tgt);
            assertThat(op).isEqualTo(TransformationRule.Operation.RENAME);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 2. MOVE / NEST
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. MOVE/NEST — mobile → contact.phone")
    class MoveTests {

        @Test
        @DisplayName("flat source → nested target → MOVE operation")
        void move_flatToNested() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "mobile", "string");
            SchemaField tgt = field(2L, tgtEp, "contact.phone", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.93, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isEqualTo("MOVE");
            assertThat(rule.getSourcePath()).isEqualTo("mobile");
            assertThat(rule.getTargetPath()).isEqualTo("contact.phone");
        }

        @Test
        @DisplayName("determineOperation returns MOVE for flat→nested")
        void determineOperation_move() {
            SchemaField src = field(1L, endpoint(1L, "POST", "/v1"), "mobile", "string");
            SchemaField tgt = field(2L, endpoint(2L, "POST", "/v2"), "contact.phone", "string");
            TransformationRule.Operation op = service.determineOperation(
                    "mobile", "contact.phone", "string", "string", src, tgt);
            assertThat(op).isEqualTo(TransformationRule.Operation.MOVE);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 3. FLATTEN
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. FLATTEN — contact.phone → phoneNumber")
    class FlattenTests {

        @Test
        @DisplayName("nested source → flat target → FLATTEN operation")
        void flatten_nestedToFlat() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "contact.phone", "string");
            SchemaField tgt = field(2L, tgtEp, "phoneNumber", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.91, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isEqualTo("FLATTEN");
            assertThat(rule.getSourcePath()).isEqualTo("contact.phone");
            assertThat(rule.getTargetPath()).isEqualTo("phoneNumber");
        }

        @Test
        @DisplayName("determineOperation returns FLATTEN for nested→flat")
        void determineOperation_flatten() {
            SchemaField src = field(1L, endpoint(1L, "POST", "/v1"), "contact.phone", "string");
            SchemaField tgt = field(2L, endpoint(2L, "POST", "/v2"), "phoneNumber", "string");
            TransformationRule.Operation op = service.determineOperation(
                    "contact.phone", "phoneNumber", "string", "string", src, tgt);
            assertThat(op).isEqualTo(TransformationRule.Operation.FLATTEN);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 4. COMBINE — not generated from a single candidate
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. COMBINE — single candidate does not generate a COMBINE rule")
    class CombineTests {

        @Test
        @DisplayName("a single firstName → fullName candidate generates RENAME, not COMBINE")
        void combine_notGeneratedFromSingleCandidate_firstName() {
            // Phase 8 cannot know that lastName also maps to fullName from a single candidate.
            // It must not generate a placeholder COMBINE rule.
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "firstName", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.88, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            // A rule IS generated for the candidate, but it's a RENAME (the only safe
            // operation we can determine from a single source→target pair).
            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isNotEqualTo("COMBINE");
            assertThat(rule.getOperation()).isEqualTo("RENAME");
        }

        @Test
        @DisplayName("validateRule returns error for COMBINE with missing sourcePaths")
        void combine_invalidWithoutSourcePaths() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "firstName", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.88, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("firstName");
            rule.setTargetPath("fullName");
            rule.setOperation(TransformationRule.Operation.COMBINE);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.88);
            rule.setCandidateStatus("AUTO_APPROVED");
            // sourcePaths is null — COMBINE without sourcePaths is invalid
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.contains("sourcePaths"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 5. SPLIT — not generated from a single candidate
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. SPLIT — single candidate does not generate a SPLIT rule")
    class SplitTests {

        @Test
        @DisplayName("fullName → firstName single candidate generates RENAME, not SPLIT")
        void split_notGeneratedFromSingleCandidate() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "fullName", "string");
            SchemaField tgt = field(2L, tgtEp, "firstName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.85, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isNotEqualTo("SPLIT");
            assertThat(rule.getOperation()).isEqualTo("RENAME");
        }

        @Test
        @DisplayName("validateRule returns error for SPLIT with missing targetPaths")
        void split_invalidWithoutTargetPaths() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "fullName", "string");
            SchemaField tgt = field(2L, tgtEp, "firstName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.85, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("fullName");
            rule.setTargetPath("firstName");
            rule.setOperation(TransformationRule.Operation.SPLIT);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.85);
            rule.setCandidateStatus("AUTO_APPROVED");
            // targetPaths is null — SPLIT without targetPaths is invalid
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.contains("targetPaths"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 6. TYPE_CONVERSION
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. TYPE_CONVERSION — string → integer")
    class TypeConversionTests {

        @Test
        @DisplayName("different types on same path → TYPE_CONVERSION")
        void typeConversion_samePath_differentTypes() {
            Endpoint srcEp = endpoint(1L, "POST", "/order");
            Endpoint tgtEp = endpoint(2L, "POST", "/order");
            SchemaField src = field(1L, srcEp, "age", "string");
            SchemaField tgt = field(2L, tgtEp, "age", "integer");
            // paths are the same BUT types differ → TYPE_CONVERSION
            MatchCandidate c = candidate(1L, src, tgt, 0.92, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isEqualTo("TYPE_CONVERSION");
            assertThat(rule.getSourceType()).isEqualTo("string");
            assertThat(rule.getTargetType()).isEqualTo("integer");
            // Parameters must contain from/to
            assertThat(rule.getParameters()).contains("\"from\"");
            assertThat(rule.getParameters()).contains("string");
            assertThat(rule.getParameters()).contains("integer");
        }

        @Test
        @DisplayName("isSafeTypeConversion: string→integer is safe")
        void isSafeTypeConversion_stringToInteger() {
            assertThat(service.isSafeTypeConversion("string", "integer")).isTrue();
        }

        @Test
        @DisplayName("isSafeTypeConversion: integer→string is safe")
        void isSafeTypeConversion_integerToString() {
            assertThat(service.isSafeTypeConversion("integer", "string")).isTrue();
        }

        @Test
        @DisplayName("isSafeTypeConversion: string→number is safe")
        void isSafeTypeConversion_stringToNumber() {
            assertThat(service.isSafeTypeConversion("string", "number")).isTrue();
        }

        @Test
        @DisplayName("isSafeTypeConversion: boolean→string is safe")
        void isSafeTypeConversion_booleanToString() {
            assertThat(service.isSafeTypeConversion("boolean", "string")).isTrue();
        }

        @Test
        @DisplayName("isSafeTypeConversion: object→string is NOT safe")
        void isSafeTypeConversion_objectToString_unsafe() {
            assertThat(service.isSafeTypeConversion("object", "string")).isFalse();
        }

        @Test
        @DisplayName("isSafeTypeConversion: array→integer is NOT safe")
        void isSafeTypeConversion_arrayToInteger_unsafe() {
            assertThat(service.isSafeTypeConversion("array", "integer")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════
    // 7. ENUM_MAPPING
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("7. ENUM_MAPPING")
    class EnumMappingTests {

        @Test
        @DisplayName("explicit enum metadata on both sides → ENUM_MAPPING")
        void enumMapping_explicitMetadata_bothSides() {
            Endpoint srcEp = endpoint(1L, "POST", "/user");
            Endpoint tgtEp = endpoint(2L, "POST", "/user");
            SchemaField src = field(1L, srcEp, "gender", "string", "male,female");
            // Different target path ("sex") so this is NOT a no-op — triggers ENUM_MAPPING
            SchemaField tgt = field(2L, tgtEp, "sex", "string", "M,F");
            MatchCandidate c = candidate(1L, src, tgt, 0.95, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            assertThat(result.getSkippedNoOp()).isEqualTo(0);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getOperation()).isEqualTo("ENUM_MAPPING");
            assertThat(rule.getParameters()).contains("mappings");
            assertThat(rule.getParameters()).contains("male");
            assertThat(rule.getParameters()).contains("M");
        }

        @Test
        @DisplayName("no enum metadata on source → falls back to RENAME, not ENUM_MAPPING")
        void enumMapping_noSourceMetadata_fallsBackToRename() {
            Endpoint srcEp = endpoint(1L, "POST", "/user");
            Endpoint tgtEp = endpoint(2L, "POST", "/user");
            SchemaField src = field(1L, srcEp, "gender", "string", null); // no enum description
            SchemaField tgt = field(2L, tgtEp, "sex", "string", "M,F");
            MatchCandidate c = candidate(1L, src, tgt, 0.90, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule.Operation op = service.determineOperation(
                    "gender", "sex", "string", "string", src, tgt);
            assertThat(op).isEqualTo(TransformationRule.Operation.RENAME);
        }

        @Test
        @DisplayName("unequal enum list lengths → no ENUM_MAPPING produced (ambiguous)")
        void enumMapping_unequalLengths_ambiguous() {
            Endpoint srcEp = endpoint(1L, "POST", "/user");
            Endpoint tgtEp = endpoint(2L, "POST", "/user");
            SchemaField src = field(1L, srcEp, "status", "string", "active,inactive,pending");
            SchemaField tgt = field(2L, tgtEp, "status", "string", "A,I");
            // 3 source values vs 2 target values — cannot map deterministically
            Map<String, String> mapping = service.buildEnumMapping(src, tgt);
            assertThat(mapping).isEmpty();

            // determineOperation falls back to RENAME (not ENUM_MAPPING)
            TransformationRule.Operation op = service.determineOperation(
                    "status", "status", "string", "string", src, tgt);
            assertThat(op).isEqualTo(TransformationRule.Operation.RENAME);
        }

        @Test
        @DisplayName("hasEnumValues: comma-separated short tokens → true")
        void hasEnumValues_commaSeparated_true() {
            Endpoint ep = endpoint(1L, "POST", "/v1");
            SchemaField f = field(1L, ep, "gender", "string", "male,female");
            assertThat(service.hasEnumValues(f)).isTrue();
        }

        @Test
        @DisplayName("hasEnumValues: long description string → false (not enum values)")
        void hasEnumValues_longDescription_false() {
            Endpoint ep = endpoint(1L, "POST", "/v1");
            SchemaField f = field(1L, ep, "desc", "string",
                    "This is a very long description that exceeds fifty characters and is not an enum list");
            assertThat(service.hasEnumValues(f)).isFalse();
        }

        @Test
        @DisplayName("hasEnumValues: null description → false")
        void hasEnumValues_null_false() {
            Endpoint ep = endpoint(1L, "POST", "/v1");
            SchemaField f = field(1L, ep, "field", "string", null);
            assertThat(service.hasEnumValues(f)).isFalse();
        }

        @Test
        @DisplayName("ENUM_MAPPING validation fails when parameters have empty mappings")
        void enumMapping_validation_emptyMappings_isInvalid() {
            Endpoint srcEp = endpoint(1L, "POST", "/user");
            Endpoint tgtEp = endpoint(2L, "POST", "/user");
            SchemaField src = field(1L, srcEp, "gender", "string", "male,female");
            SchemaField tgt = field(2L, tgtEp, "sex", "string", "M,F");
            MatchCandidate c = candidate(1L, src, tgt, 0.90, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("gender");
            rule.setTargetPath("sex");
            rule.setOperation(TransformationRule.Operation.ENUM_MAPPING);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.90);
            rule.setCandidateStatus("AUTO_APPROVED");
            rule.setParameters("{}"); // empty mappings — invalid
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.toLowerCase().contains("enum"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 8. Missing source path
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("8. Missing source path → INVALID rule")
    class MissingSourcePathTests {

        @Test
        @DisplayName("null source path fails validation")
        void missingSourcePath_nullPath_invalid() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, null, "string"); // null fieldPath
            // Force null by not setting fieldPath
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.92, MatchCandidate.ApprovalStatus.AUTO_APPROVED);
            c.setSourcePath(null); // override

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath(null);
            rule.setTargetPath("fullName");
            rule.setOperation(TransformationRule.Operation.RENAME);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.92);
            rule.setCandidateStatus("AUTO_APPROVED");
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.toLowerCase().contains("source path"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 9. Missing target path
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("9. Missing target path → INVALID rule")
    class MissingTargetPathTests {

        @Test
        @DisplayName("blank target path fails validation")
        void missingTargetPath_blankPath_invalid() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "name", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("name");
            rule.setTargetPath("  "); // blank
            rule.setOperation(TransformationRule.Operation.RENAME);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.96);
            rule.setCandidateStatus("AUTO_APPROVED");
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.toLowerCase().contains("target path"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 10 & 11. Unsupported / unsafe type conversion
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("10 & 11. Unsupported / unsafe type conversions")
    class UnsafeTypeConversionTests {

        @Test
        @DisplayName("object → string conversion produces INVALID rule")
        void typeConversion_objectToString_invalid() {
            Endpoint srcEp = endpoint(1L, "POST", "/order");
            Endpoint tgtEp = endpoint(2L, "POST", "/order");
            SchemaField src = field(1L, srcEp, "address", "object");
            SchemaField tgt = field(2L, tgtEp, "address", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.80, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesInvalid()).isEqualTo(1);
            assertThat(result.getRulesGenerated()).isEqualTo(0);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getRuleStatus()).isEqualTo("INVALID");
            assertThat(rule.getValidationErrors()).containsIgnoringCase("unsafe");
        }

        @Test
        @DisplayName("array → integer conversion produces INVALID rule")
        void typeConversion_arrayToInteger_invalid() {
            Endpoint srcEp = endpoint(1L, "POST", "/order");
            Endpoint tgtEp = endpoint(2L, "POST", "/order");
            SchemaField src = field(1L, srcEp, "tags", "array");
            SchemaField tgt = field(2L, tgtEp, "tagCount", "integer");
            MatchCandidate c = candidate(1L, src, tgt, 0.75, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesInvalid()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getRuleStatus()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("validateRule: TYPE_CONVERSION with object type returns error")
        void validateRule_typeConversion_objectType_returnsError() {
            Endpoint srcEp = endpoint(1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(2L, "POST", "/v2");
            SchemaField src = field(1L, srcEp, "data", "object");
            SchemaField tgt = field(2L, tgtEp, "data", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.80, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("data");
            rule.setTargetPath("data");
            rule.setOperation(TransformationRule.Operation.TYPE_CONVERSION);
            rule.setSourceType("object");
            rule.setTargetType("string");
            rule.setConfidence(0.80);
            rule.setCandidateStatus("AUTO_APPROVED");
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.toLowerCase().contains("unsafe"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 12. LOW_CONFIDENCE exclusion
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("12. LOW_CONFIDENCE candidate exclusion")
    class LowConfidenceExclusionTests {

        @Test
        @DisplayName("LOW_CONFIDENCE candidates are skipped — no rule generated")
        void lowConfidence_skipped() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "id", "integer");
            SchemaField tgt = field(2L, tgtEp, "userId", "integer");
            MatchCandidate c = candidate(1L, src, tgt, 0.55, MatchCandidate.ApprovalStatus.LOW_CONFIDENCE);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(0);
            assertThat(result.getRulesInvalid()).isEqualTo(0);
            assertThat(result.getSkippedLowConfidence()).isEqualTo(1);
            assertThat(result.getRules()).isEmpty();
        }

        @Test
        @DisplayName("mixed: LOW_CONFIDENCE skipped, AUTO_APPROVED processed")
        void mixed_lowConfidence_and_autoApproved() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src1 = field(1L, srcEp, "name", "string");
            SchemaField tgt1 = field(2L, tgtEp, "fullName", "string");
            SchemaField src2 = field(3L, srcEp, "x", "string");
            SchemaField tgt2 = field(4L, tgtEp, "y", "string");

            MatchCandidate approved = candidate(1L, src1, tgt1, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);
            MatchCandidate lowConf  = candidate(2L, src2, tgt2, 0.50, MatchCandidate.ApprovalStatus.LOW_CONFIDENCE);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(approved, lowConf));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            assertThat(result.getSkippedLowConfidence()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 13. REJECTED candidate exclusion
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("13. REJECTED candidate exclusion")
    class RejectedExclusionTests {

        @Test
        @DisplayName("REJECTED candidates are skipped — no rule generated")
        void rejected_skipped() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "price", "number");
            SchemaField tgt = field(2L, tgtEp, "quantity", "integer");
            MatchCandidate c = candidate(1L, src, tgt, 0.40, MatchCandidate.ApprovalStatus.REJECTED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(0);
            assertThat(result.getSkippedRejected()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 14. REQUIRES_REVIEW approval handling
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("14. REQUIRES_REVIEW candidate → GENERATED rule (not auto-approved)")
    class RequiresReviewTests {

        @Test
        @DisplayName("REQUIRES_REVIEW candidate produces GENERATED rule that stays GENERATED")
        void requiresReview_producesGeneratedRule() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "mobile", "string");
            SchemaField tgt = field(2L, tgtEp, "phoneNumber", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.82, MatchCandidate.ApprovalStatus.REQUIRES_REVIEW);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(1);
            TransformationRuleResponse rule = result.getRules().get(0);
            assertThat(rule.getCandidateStatus()).isEqualTo("REQUIRES_REVIEW");
            // Phase 8 must NOT auto-approve a REQUIRES_REVIEW candidate's rule
            assertThat(rule.getRuleStatus()).isEqualTo("GENERATED");
        }

        @Test
        @DisplayName("validateRule rejects a rule whose candidateStatus is an excluded status")
        void validateRule_rejectedCandidateStatus_returnsError() {
            Endpoint srcEp = endpoint(1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(2L, "POST", "/v2");
            SchemaField src = field(1L, srcEp, "name", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.60, MatchCandidate.ApprovalStatus.LOW_CONFIDENCE);

            TransformationRule rule = new TransformationRule();
            rule.setSourcePath("name");
            rule.setTargetPath("fullName");
            rule.setOperation(TransformationRule.Operation.RENAME);
            rule.setSourceType("string");
            rule.setTargetType("string");
            rule.setConfidence(0.60);
            rule.setCandidateStatus("LOW_CONFIDENCE"); // must not be allowed
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);

            List<String> errors = service.validateRule(rule, c);
            assertThat(errors).anyMatch(e -> e.contains("candidateStatus"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 15. Multiple endpoints
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("15. Multiple endpoints — rules grouped by endpoint pair")
    class MultipleEndpointsTests {

        @Test
        @DisplayName("candidates from two endpoint pairs → rules preserve endpoint context")
        void multipleEndpoints_rulesPreserveEndpointContext() {
            Endpoint srcEp1 = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp1 = endpoint(2L, "POST", "/customer");
            Endpoint srcEp2 = endpoint(3L, "GET", "/product");
            Endpoint tgtEp2 = endpoint(4L, "GET", "/product");

            SchemaField src1 = field(1L, srcEp1, "name", "string");
            SchemaField tgt1 = field(2L, tgtEp1, "fullName", "string");
            SchemaField src2 = field(3L, srcEp2, "title", "string");
            SchemaField tgt2 = field(4L, tgtEp2, "productName", "string");

            MatchCandidate c1 = candidate(1L, src1, tgt1, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);
            MatchCandidate c2 = candidate(2L, src2, tgt2, 0.91, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c1, c2));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getRulesGenerated()).isEqualTo(2);
            // Verify endpoint paths are preserved for Phase 10 lookup
            assertThat(result.getRules()).anyMatch(r ->
                    r.getSourceEndpointPath().contains("/customer"));
            assertThat(result.getRules()).anyMatch(r ->
                    r.getSourceEndpointPath().contains("/product"));
        }
    }

    // ══════════════════════════════════════════════════════════
    // 16. Ambiguous mappings
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("16. Ambiguous mappings")
    class AmbiguousMappingTests {

        @Test
        @DisplayName("ENUM_MAPPING with unequal enum sizes is ambiguous — falls back to RENAME")
        void ambiguous_unequalEnumSizes_fallBackToRename() {
            Endpoint srcEp = endpoint(1L, "POST", "/user");
            Endpoint tgtEp = endpoint(2L, "POST", "/user");
            SchemaField src = field(1L, srcEp, "status", "string", "active,inactive,pending");
            SchemaField tgt = field(2L, tgtEp, "state", "string", "A,I"); // only 2 values vs 3

            TransformationRule.Operation op = service.determineOperation(
                    "status", "state", "string", "string", src, tgt);
            assertThat(op).isNotEqualTo(TransformationRule.Operation.ENUM_MAPPING);
        }
    }

    // ══════════════════════════════════════════════════════════
    // 17. Idempotent rule generation
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("17. Idempotent rule generation")
    class IdempotencyTests {

        @Test
        @DisplayName("generateRules deletes existing rules before re-generating")
        void idempotent_deletesBeforeCreating() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "name", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            // Call generate twice
            service.generateRules(5L);
            service.generateRules(5L);

            // deleteByReportId must have been called exactly twice (once per call)
            verify(ruleRepository, times(2)).deleteByReportId(5L);
        }

        @Test
        @DisplayName("generateRules produces same rules on second call")
        void idempotent_sameRulesOnSecondCall() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "name", "string");
            SchemaField tgt = field(2L, tgtEp, "fullName", "string");
            MatchCandidate c = candidate(1L, src, tgt, 0.96, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult r1 = service.generateRules(5L);
            TransformationRuleGenerationResult r2 = service.generateRules(5L);

            assertThat(r1.getRulesGenerated()).isEqualTo(r2.getRulesGenerated());
            assertThat(r1.getRules().get(0).getOperation())
                    .isEqualTo(r2.getRules().get(0).getOperation());
        }
    }

    // ══════════════════════════════════════════════════════════
    // No-op exclusion (identical path + type)
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("18. No-op exclusion — identical path AND identical type")
    class NoOpExclusionTests {

        @Test
        @DisplayName("same path and same type → skipped as no-op, no rule generated")
        void noOp_samePathAndType_skipped() {
            Endpoint srcEp = endpoint(1L, "POST", "/customer");
            Endpoint tgtEp = endpoint(2L, "POST", "/customer");
            SchemaField src = field(1L, srcEp, "email", "string");
            SchemaField tgt = field(2L, tgtEp, "email", "string");
            // same path "email", same type "string" → no transformation needed
            MatchCandidate c = candidate(1L, src, tgt, 0.99, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getSkippedNoOp()).isEqualTo(1);
            assertThat(result.getRulesGenerated()).isEqualTo(0);
            assertThat(result.getRules()).isEmpty();
        }

        @Test
        @DisplayName("same path but DIFFERENT types → TYPE_CONVERSION rule IS generated")
        void noOp_samePath_differentType_typeConversionGenerated() {
            Endpoint srcEp = endpoint(1L, "POST", "/order");
            Endpoint tgtEp = endpoint(2L, "POST", "/order");
            SchemaField src = field(1L, srcEp, "amount", "string");
            SchemaField tgt = field(2L, tgtEp, "amount", "number");
            MatchCandidate c = candidate(1L, src, tgt, 0.94, MatchCandidate.ApprovalStatus.AUTO_APPROVED);

            CompatibilityReport report = report(5L, 10L);
            stubForGeneration(report, List.of(c));

            TransformationRuleGenerationResult result = service.generateRules(5L);

            assertThat(result.getSkippedNoOp()).isEqualTo(0);
            assertThat(result.getRulesGenerated()).isEqualTo(1);
            assertThat(result.getRules().get(0).getOperation()).isEqualTo("TYPE_CONVERSION");
        }
    }

    // ══════════════════════════════════════════════════════════
    // Approve / reject lifecycle
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("19. Approve / reject lifecycle")
    class ApproveRejectTests {

        private TransformationRule generatedRule(long id) {
            Endpoint srcEp = endpoint(1L, "POST", "/v1");
            Endpoint tgtEp = endpoint(2L, "POST", "/v2");
            CompatibilityReport r = report(5L, 10L);

            TransformationRule rule = new TransformationRule();
            setId(rule, id);
            rule.setReport(r);
            rule.setSourceEndpoint(srcEp);
            rule.setTargetEndpoint(tgtEp);
            rule.setSourcePath("name");
            rule.setTargetPath("fullName");
            rule.setOperation(TransformationRule.Operation.RENAME);
            rule.setConfidence(0.96);
            rule.setCandidateStatus("AUTO_APPROVED");
            rule.setRuleStatus(TransformationRule.RuleStatus.GENERATED);
            return rule;
        }

        @Test
        @DisplayName("approveRule transitions GENERATED → APPROVED")
        void approve_generatedToApproved() {
            TransformationRule rule = generatedRule(1L);
            when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransformationRule approved = service.approveRule(1L, "Looks good");
            assertThat(approved.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
        }

        @Test
        @DisplayName("rejectRule transitions GENERATED → REJECTED with reason")
        void reject_generatedToRejected() {
            TransformationRule rule = generatedRule(2L);
            when(ruleRepository.findById(2L)).thenReturn(Optional.of(rule));
            when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransformationRule rejected = service.rejectRule(2L, "Wrong mapping");
            assertThat(rejected.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("Wrong mapping");
        }

        @Test
        @DisplayName("approveRule on INVALID rule throws IllegalStateException")
        void approve_invalidRule_throws() {
            TransformationRule rule = generatedRule(3L);
            rule.setRuleStatus(TransformationRule.RuleStatus.INVALID);
            rule.setValidationErrors("unsafe type conversion");
            when(ruleRepository.findById(3L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.approveRule(3L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("approveRule on REJECTED rule throws IllegalStateException")
        void approve_rejectedRule_throws() {
            TransformationRule rule = generatedRule(4L);
            rule.setRuleStatus(TransformationRule.RuleStatus.REJECTED);
            when(ruleRepository.findById(4L)).thenReturn(Optional.of(rule));

            assertThatThrownBy(() -> service.approveRule(4L, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("REJECTED");
        }

        @Test
        @DisplayName("approveRule is idempotent — already-APPROVED returns without error")
        void approve_alreadyApproved_idempotent() {
            TransformationRule rule = generatedRule(5L);
            rule.setRuleStatus(TransformationRule.RuleStatus.APPROVED);
            when(ruleRepository.findById(5L)).thenReturn(Optional.of(rule));

            // Should not throw
            TransformationRule result = service.approveRule(5L, null);
            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.APPROVED);
            verify(ruleRepository, never()).save(any()); // no save needed
        }

        @Test
        @DisplayName("rejectRule is idempotent — already-REJECTED returns without error")
        void reject_alreadyRejected_idempotent() {
            TransformationRule rule = generatedRule(6L);
            rule.setRuleStatus(TransformationRule.RuleStatus.REJECTED);
            rule.setRejectionReason("original reason");
            when(ruleRepository.findById(6L)).thenReturn(Optional.of(rule));

            TransformationRule result = service.rejectRule(6L, "new reason");
            assertThat(result.getRuleStatus()).isEqualTo(TransformationRule.RuleStatus.REJECTED);
            verify(ruleRepository, never()).save(any()); // no save needed for idempotent
        }
    }

    // ══════════════════════════════════════════════════════════
    // Regression: report not found
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Regression — Phases 1–7 contract: report not found")
    class RegressionTests {

        @Test
        @DisplayName("generateRules throws ResourceNotFoundException when report does not exist")
        void generateRules_reportNotFound_throws() {
            when(reportRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateRules(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("getRuleById throws ResourceNotFoundException when rule does not exist")
        void getRuleById_notFound_throws() {
            when(ruleRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getRuleById(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getRulesByReport throws ResourceNotFoundException when report does not exist")
        void getRulesByReport_reportNotFound_throws() {
            when(reportRepository.existsById(888L)).thenReturn(false);

            assertThatThrownBy(() -> service.getRulesByReport(888L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getRulesByEndpointPair throws ResourceNotFoundException when endpoint does not exist")
        void getRulesByEndpointPair_endpointNotFound_throws() {
            when(endpointRepository.existsById(10L)).thenReturn(false);

            assertThatThrownBy(() -> service.getRulesByEndpointPair(10L, 20L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("isNested: flat path returns false")
        void isNested_flatPath_false() {
            assertThat(service.isNested("mobile")).isFalse();
        }

        @Test
        @DisplayName("isNested: nested path returns true")
        void isNested_nestedPath_true() {
            assertThat(service.isNested("contact.phone")).isTrue();
        }

        @Test
        @DisplayName("isNested: deeply nested path returns true")
        void isNested_deeplyNested_true() {
            assertThat(service.isNested("a.b.c")).isTrue();
        }

        @Test
        @DisplayName("isNested: null returns false")
        void isNested_null_false() {
            assertThat(service.isNested(null)).isFalse();
        }
    }
}
