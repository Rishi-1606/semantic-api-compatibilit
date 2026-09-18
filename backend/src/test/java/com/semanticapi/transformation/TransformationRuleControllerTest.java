package com.semanticapi.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.controller.TransformationRuleController;
import com.semanticapi.dto.*;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.service.TransformationRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST layer unit tests for {@link TransformationRuleController}.
 *
 * <p>Uses {@code @WebMvcTest} — Spring MVC context only, no database.
 * All service calls are mocked with Mockito.
 *
 * <h2>Covered scenarios</h2>
 * <ul>
 *   <li>POST /generate → 201 with generation result</li>
 *   <li>POST /generate with invalid body → 400</li>
 *   <li>POST /generate → 404 when report not found</li>
 *   <li>GET /report/{id} → 200 list of rules</li>
 *   <li>GET /report/{id} → 404 when report not found</li>
 *   <li>GET /{id} → 200 single rule</li>
 *   <li>GET /{id} → 404 when not found</li>
 *   <li>GET /endpoint-pair → 200 list by endpoint pair</li>
 *   <li>POST /{id}/approve → 200 approved rule</li>
 *   <li>POST /{id}/approve → 422 when rule is INVALID</li>
 *   <li>POST /{id}/reject → 200 rejected rule</li>
 * </ul>
 */
@WebMvcTest(TransformationRuleController.class)
@DisplayName("TransformationRuleController")
class TransformationRuleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransformationRuleService ruleService;

    // ── Helpers ───────────────────────────────────────────────

    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TransformationRule ruleEntity(long id, TransformationRule.Operation op,
                                           TransformationRule.RuleStatus status) {
        ApiVersion srcVer = new ApiVersion(); srcVer.setVersion("V1"); setId(srcVer, 1L);
        ApiVersion tgtVer = new ApiVersion(); tgtVer.setVersion("V2"); setId(tgtVer, 2L);
        MatchSession session = new MatchSession();
        session.setSourceVersion(srcVer); session.setTargetVersion(tgtVer); setId(session, 10L);
        CompatibilityReport report = new CompatibilityReport();
        report.setMatchSession(session); report.setSourceVersion(srcVer); report.setTargetVersion(tgtVer);
        report.setCompatibilityStatus(CompatibilityReport.CompatibilityStatus.COMPATIBLE);
        report.setOverallScore(0.95); setId(report, 5L);

        Endpoint srcEp = new Endpoint(); srcEp.setHttpMethod("POST"); srcEp.setPath("/customer"); setId(srcEp, 1L);
        Endpoint tgtEp = new Endpoint(); tgtEp.setHttpMethod("POST"); tgtEp.setPath("/customer"); setId(tgtEp, 2L);

        TransformationRule rule = new TransformationRule();
        setId(rule, id);
        rule.setReport(report);
        rule.setSourceEndpoint(srcEp);
        rule.setTargetEndpoint(tgtEp);
        rule.setSourceEndpointPath("POST /customer");
        rule.setTargetEndpointPath("POST /customer");
        rule.setSourcePath("name");
        rule.setTargetPath("fullName");
        rule.setOperation(op);
        rule.setSourceType("string");
        rule.setTargetType("string");
        rule.setConfidence(0.96);
        rule.setCandidateStatus("AUTO_APPROVED");
        rule.setRuleStatus(status);
        rule.setParameters("{}");
        return rule;
    }

    private TransformationRuleGenerationResult generationResult(int generated, int invalid) {
        TransformationRuleGenerationResult r = new TransformationRuleGenerationResult();
        r.setReportId(5L);
        r.setTotalCandidatesProcessed(generated + invalid);
        r.setRulesGenerated(generated);
        r.setRulesInvalid(invalid);
        r.setSkippedNoOp(0);
        r.setSkippedLowConfidence(0);
        r.setSkippedRejected(0);
        r.setRules(Collections.emptyList());
        return r;
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/transformation-rules/generate
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /generate")
    class GenerateTests {

        @Test
        @DisplayName("valid request → 201 Created with generation result")
        void generate_validRequest_returns201() throws Exception {
            when(ruleService.generateRules(5L)).thenReturn(generationResult(3, 0));

            mockMvc.perform(post("/api/transformation-rules/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reportId\":5}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reportId").value(5))
                    .andExpect(jsonPath("$.rulesGenerated").value(3))
                    .andExpect(jsonPath("$.rulesInvalid").value(0));
        }

        @Test
        @DisplayName("missing reportId → 400 Bad Request")
        void generate_missingReportId_returns400() throws Exception {
            mockMvc.perform(post("/api/transformation-rules/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("report not found → 404 Not Found")
        void generate_reportNotFound_returns404() throws Exception {
            when(ruleService.generateRules(999L))
                    .thenThrow(new ResourceNotFoundException("CompatibilityReport", 999L));

            mockMvc.perform(post("/api/transformation-rules/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reportId\":999}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("generation with some invalid rules → 201 with counts")
        void generate_someInvalidRules_returns201WithCounts() throws Exception {
            TransformationRuleGenerationResult result = generationResult(2, 1);
            when(ruleService.generateRules(5L)).thenReturn(result);

            mockMvc.perform(post("/api/transformation-rules/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reportId\":5}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rulesGenerated").value(2))
                    .andExpect(jsonPath("$.rulesInvalid").value(1));
        }
    }

    // ══════════════════════════════════════════════════════════
    // GET /api/transformation-rules/report/{reportId}
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /report/{reportId}")
    class GetByReportTests {

        @Test
        @DisplayName("existing report → 200 with rule list")
        void getByReport_exists_returns200() throws Exception {
            TransformationRule rule = ruleEntity(1L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.GENERATED);
            when(ruleService.getRulesByReport(5L)).thenReturn(List.of(rule));

            mockMvc.perform(get("/api/transformation-rules/report/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].operation").value("RENAME"))
                    .andExpect(jsonPath("$[0].ruleStatus").value("GENERATED"))
                    .andExpect(jsonPath("$[0].sourcePath").value("name"))
                    .andExpect(jsonPath("$[0].targetPath").value("fullName"));
        }

        @Test
        @DisplayName("empty report → 200 with empty list")
        void getByReport_empty_returns200EmptyList() throws Exception {
            when(ruleService.getRulesByReport(5L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/transformation-rules/report/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("report not found → 404")
        void getByReport_notFound_returns404() throws Exception {
            when(ruleService.getRulesByReport(999L))
                    .thenThrow(new ResourceNotFoundException("CompatibilityReport", 999L));

            mockMvc.perform(get("/api/transformation-rules/report/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════
    // GET /api/transformation-rules/{ruleId}
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /{ruleId}")
    class GetByIdTests {

        @Test
        @DisplayName("existing rule → 200 with rule details")
        void getById_exists_returns200() throws Exception {
            TransformationRule rule = ruleEntity(7L, TransformationRule.Operation.MOVE,
                    TransformationRule.RuleStatus.APPROVED);
            when(ruleService.getRuleById(7L)).thenReturn(rule);

            mockMvc.perform(get("/api/transformation-rules/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.operation").value("MOVE"))
                    .andExpect(jsonPath("$.ruleStatus").value("APPROVED"));
        }

        @Test
        @DisplayName("non-existent rule → 404")
        void getById_notFound_returns404() throws Exception {
            when(ruleService.getRuleById(404L))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(get("/api/transformation-rules/404"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════
    // GET /api/transformation-rules/endpoint-pair
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /endpoint-pair")
    class GetByEndpointPairTests {

        @Test
        @DisplayName("valid pair → 200 with rules for that pair")
        void getByEndpointPair_valid_returns200() throws Exception {
            TransformationRule rule = ruleEntity(1L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.APPROVED);
            when(ruleService.getRulesByEndpointPair(1L, 2L)).thenReturn(List.of(rule));

            mockMvc.perform(get("/api/transformation-rules/endpoint-pair")
                            .param("srcEndpointId", "1")
                            .param("tgtEndpointId", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].sourceEndpointId").value(1))
                    .andExpect(jsonPath("$[0].targetEndpointId").value(2));
        }

        @Test
        @DisplayName("endpoint not found → 404")
        void getByEndpointPair_endpointNotFound_returns404() throws Exception {
            when(ruleService.getRulesByEndpointPair(99L, 100L))
                    .thenThrow(new ResourceNotFoundException("Endpoint", 99L));

            mockMvc.perform(get("/api/transformation-rules/endpoint-pair")
                            .param("srcEndpointId", "99")
                            .param("tgtEndpointId", "100"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("missing query param → 400")
        void getByEndpointPair_missingParam_returns400() throws Exception {
            mockMvc.perform(get("/api/transformation-rules/endpoint-pair")
                            .param("srcEndpointId", "1"))
                    // missing tgtEndpointId
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/transformation-rules/{id}/approve
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /{id}/approve")
    class ApproveTests {

        @Test
        @DisplayName("approve GENERATED rule → 200 with APPROVED status")
        void approve_generated_returns200Approved() throws Exception {
            TransformationRule approved = ruleEntity(1L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.APPROVED);
            when(ruleService.approveRule(eq(1L), any())).thenReturn(approved);

            mockMvc.perform(post("/api/transformation-rules/1/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Verified correct\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ruleStatus").value("APPROVED"));
        }

        @Test
        @DisplayName("approve without body → 200 (reason is optional)")
        void approve_noBody_returns200() throws Exception {
            TransformationRule approved = ruleEntity(1L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.APPROVED);
            when(ruleService.approveRule(eq(1L), isNull())).thenReturn(approved);

            mockMvc.perform(post("/api/transformation-rules/1/approve"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("approve INVALID rule → 422 Unprocessable Entity")
        void approve_invalidRule_returns422() throws Exception {
            when(ruleService.approveRule(eq(3L), any()))
                    .thenThrow(new IllegalStateException("Rule 3 is INVALID and cannot be approved"));

            mockMvc.perform(post("/api/transformation-rules/3/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("approve rule not found → 404")
        void approve_notFound_returns404() throws Exception {
            when(ruleService.approveRule(eq(404L), any()))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(post("/api/transformation-rules/404/approve"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/transformation-rules/{id}/reject
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /{id}/reject")
    class RejectTests {

        @Test
        @DisplayName("reject GENERATED rule → 200 with REJECTED status")
        void reject_generated_returns200Rejected() throws Exception {
            TransformationRule rejected = ruleEntity(2L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.REJECTED);
            when(ruleService.rejectRule(eq(2L), any())).thenReturn(rejected);

            mockMvc.perform(post("/api/transformation-rules/2/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Incorrect mapping\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ruleStatus").value("REJECTED"));
        }

        @Test
        @DisplayName("reject without body → 200 (reason is optional)")
        void reject_noBody_returns200() throws Exception {
            TransformationRule rejected = ruleEntity(2L, TransformationRule.Operation.RENAME,
                    TransformationRule.RuleStatus.REJECTED);
            when(ruleService.rejectRule(eq(2L), isNull())).thenReturn(rejected);

            mockMvc.perform(post("/api/transformation-rules/2/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("reject rule not found → 404")
        void reject_notFound_returns404() throws Exception {
            when(ruleService.rejectRule(eq(404L), any()))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(post("/api/transformation-rules/404/reject"))
                    .andExpect(status().isNotFound());
        }
    }
}
