package com.semanticapi.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.controller.ApprovalController;
import com.semanticapi.dto.*;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.*;
import com.semanticapi.service.ApprovalService;
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
 * REST layer unit tests for {@link ApprovalController} (Phase 9).
 *
 * <p>Uses {@code @WebMvcTest} — Spring MVC context only, no database.
 * All service calls are mocked.
 *
 * <h2>Covered scenarios</h2>
 * <ul>
 *   <li>POST /api/approval/rules/{id}/approve → 200 approved rule</li>
 *   <li>POST /api/approval/rules/{id}/approve → 422 when INVALID</li>
 *   <li>POST /api/approval/rules/{id}/approve → 422 when low confidence</li>
 *   <li>POST /api/approval/rules/{id}/approve → 404 when not found</li>
 *   <li>POST /api/approval/rules/{id}/reject  → 200 rejected rule</li>
 *   <li>POST /api/approval/rules/{id}/reject  → 404 when not found</li>
 *   <li>POST /api/approval/reports/{id}/auto-approve → 200 with counts</li>
 *   <li>POST /api/approval/reports/{id}/auto-approve → 404 when report not found</li>
 *   <li>GET  /api/approval/rules/{id}/validate → 200 eligible</li>
 *   <li>GET  /api/approval/rules/{id}/validate → 200 not eligible with reasons</li>
 *   <li>GET  /api/approval/rules/{id}/validate → 404 when not found</li>
 * </ul>
 */
@WebMvcTest(ApprovalController.class)
@DisplayName("ApprovalController")
class ApprovalControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ApprovalService approvalService;

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TransformationRule ruleEntity(long id, TransformationRule.RuleStatus status) {
        ApiVersion srcVer = new ApiVersion(); srcVer.setVersion("V1"); setId(srcVer, 1L);
        ApiVersion tgtVer = new ApiVersion(); tgtVer.setVersion("V2"); setId(tgtVer, 2L);
        MatchSession session = new MatchSession();
        session.setSourceVersion(srcVer); session.setTargetVersion(tgtVer); setId(session, 10L);
        CompatibilityReport report = new CompatibilityReport();
        report.setMatchSession(session);
        report.setSourceVersion(srcVer); report.setTargetVersion(tgtVer);
        report.setCompatibilityStatus(CompatibilityReport.CompatibilityStatus.COMPATIBLE);
        report.setOverallScore(0.95); setId(report, 5L);

        Endpoint srcEp = new Endpoint(); srcEp.setHttpMethod("POST"); srcEp.setPath("/v1"); setId(srcEp, 1L);
        Endpoint tgtEp = new Endpoint(); tgtEp.setHttpMethod("POST"); tgtEp.setPath("/v2"); setId(tgtEp, 2L);

        TransformationRule rule = new TransformationRule();
        setId(rule, id);
        rule.setReport(report);
        rule.setSourceEndpoint(srcEp);
        rule.setTargetEndpoint(tgtEp);
        rule.setSourceEndpointPath("POST /v1");
        rule.setTargetEndpointPath("POST /v2");
        rule.setSourcePath("name");
        rule.setTargetPath("fullName");
        rule.setOperation(TransformationRule.Operation.RENAME);
        rule.setSourceType("string");
        rule.setTargetType("string");
        rule.setConfidence(0.95);
        rule.setCandidateStatus("AUTO_APPROVED");
        rule.setRuleStatus(status);
        rule.setParameters("{}");
        return rule;
    }

    private ApprovalValidationReport validationReport(boolean eligible, List<String> reasons) {
        ApprovalValidationReport r = new ApprovalValidationReport();
        r.setRuleId(1L);
        r.setRuleStatus("GENERATED");
        r.setCandidateStatus("AUTO_APPROVED");
        r.setConfidence(0.92);
        r.setOperation("RENAME");
        r.setEligible(eligible);
        r.setBlockingReasons(reasons);
        return r;
    }

    private AutoApprovalResult autoResult(int approved, int reviewNeeded) {
        AutoApprovalResult r = new AutoApprovalResult();
        r.setReportId(5L);
        r.setTotalGenerated(approved + reviewNeeded);
        r.setAutoApproved(approved);
        r.setRequiresHumanReview(reviewNeeded);
        r.setSkippedLowConfidence(0);
        r.setSkippedRequiresReview(0);
        r.setSkippedInvalidOrFailed(0);
        r.setApprovedRules(Collections.emptyList());
        return r;
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/approval/rules/{id}/approve
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /rules/{id}/approve")
    class ApproveTests {

        @Test
        @DisplayName("valid GENERATED rule → 200 with APPROVED status")
        void approve_validRule_returns200() throws Exception {
            TransformationRule approved = ruleEntity(1L, TransformationRule.RuleStatus.APPROVED);
            when(approvalService.approveRule(eq(1L), any())).thenReturn(approved);

            mockMvc.perform(post("/api/approval/rules/1/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Verified\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ruleStatus").value("APPROVED"))
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("approve without body → 200 (reason is optional)")
        void approve_noBody_returns200() throws Exception {
            TransformationRule approved = ruleEntity(1L, TransformationRule.RuleStatus.APPROVED);
            when(approvalService.approveRule(eq(1L), isNull())).thenReturn(approved);

            mockMvc.perform(post("/api/approval/rules/1/approve"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("INVALID rule → 422 Unprocessable Entity")
        void approve_invalidRule_returns422() throws Exception {
            when(approvalService.approveRule(eq(3L), any()))
                    .thenThrow(new IllegalStateException("Rule 3 is INVALID and cannot be approved"));

            mockMvc.perform(post("/api/approval/rules/3/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        @DisplayName("low confidence → 422 Unprocessable Entity")
        void approve_lowConfidence_returns422() throws Exception {
            when(approvalService.approveRule(eq(5L), any()))
                    .thenThrow(new IllegalStateException(
                            "Rule 5 cannot be approved: confidence 0.600 is below minimum 0.70"));

            mockMvc.perform(post("/api/approval/rules/5/approve"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("rule not found → 404")
        void approve_notFound_returns404() throws Exception {
            when(approvalService.approveRule(eq(404L), any()))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(post("/api/approval/rules/404/approve"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/approval/rules/{id}/reject
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /rules/{id}/reject")
    class RejectTests {

        @Test
        @DisplayName("valid rule → 200 with REJECTED status")
        void reject_validRule_returns200() throws Exception {
            TransformationRule rejected = ruleEntity(2L, TransformationRule.RuleStatus.REJECTED);
            when(approvalService.rejectRule(eq(2L), any())).thenReturn(rejected);

            mockMvc.perform(post("/api/approval/rules/2/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Incorrect mapping\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ruleStatus").value("REJECTED"));
        }

        @Test
        @DisplayName("reject without body → 200")
        void reject_noBody_returns200() throws Exception {
            TransformationRule rejected = ruleEntity(2L, TransformationRule.RuleStatus.REJECTED);
            when(approvalService.rejectRule(eq(2L), isNull())).thenReturn(rejected);

            mockMvc.perform(post("/api/approval/rules/2/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("rule not found → 404")
        void reject_notFound_returns404() throws Exception {
            when(approvalService.rejectRule(eq(404L), any()))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(post("/api/approval/rules/404/reject"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════
    // POST /api/approval/reports/{reportId}/auto-approve
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /reports/{reportId}/auto-approve")
    class AutoApproveTests {

        @Test
        @DisplayName("report with eligible rules → 200 with counts")
        void autoApprove_eligibleRules_returns200WithCounts() throws Exception {
            AutoApprovalResult result = autoResult(3, 1);
            when(approvalService.autoApproveForReport(5L)).thenReturn(result);

            mockMvc.perform(post("/api/approval/reports/5/auto-approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(5))
                    .andExpect(jsonPath("$.autoApproved").value(3))
                    .andExpect(jsonPath("$.requiresHumanReview").value(1))
                    .andExpect(jsonPath("$.totalGenerated").value(4));
        }

        @Test
        @DisplayName("report not found → 404")
        void autoApprove_reportNotFound_returns404() throws Exception {
            when(approvalService.autoApproveForReport(999L))
                    .thenThrow(new ResourceNotFoundException("CompatibilityReport", 999L));

            mockMvc.perform(post("/api/approval/reports/999/auto-approve"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("empty report → 200 with zero counts")
        void autoApprove_emptyReport_returns200ZeroCounts() throws Exception {
            AutoApprovalResult result = autoResult(0, 0);
            when(approvalService.autoApproveForReport(5L)).thenReturn(result);

            mockMvc.perform(post("/api/approval/reports/5/auto-approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.autoApproved").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════
    // GET /api/approval/rules/{id}/validate
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /rules/{id}/validate")
    class ValidateTests {

        @Test
        @DisplayName("eligible rule → 200 with eligible=true, empty reasons")
        void validate_eligible_returns200Eligible() throws Exception {
            ApprovalValidationReport report = validationReport(true, Collections.emptyList());
            when(approvalService.validateForApproval(1L)).thenReturn(report);

            mockMvc.perform(get("/api/approval/rules/1/validate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(true))
                    .andExpect(jsonPath("$.ruleId").value(1))
                    .andExpect(jsonPath("$.confidence").value(0.92));
        }

        @Test
        @DisplayName("not eligible → 200 with eligible=false and blocking reasons")
        void validate_notEligible_returns200WithReasons() throws Exception {
            ApprovalValidationReport report = validationReport(false,
                    List.of("Rule is INVALID", "Confidence 0.65 below 0.70"));
            when(approvalService.validateForApproval(2L)).thenReturn(report);

            mockMvc.perform(get("/api/approval/rules/2/validate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eligible").value(false))
                    .andExpect(jsonPath("$.blockingReasons").isArray())
                    .andExpect(jsonPath("$.blockingReasons[0]").value("Rule is INVALID"));
        }

        @Test
        @DisplayName("rule not found → 404")
        void validate_notFound_returns404() throws Exception {
            when(approvalService.validateForApproval(404L))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 404L));

            mockMvc.perform(get("/api/approval/rules/404/validate"))
                    .andExpect(status().isNotFound());
        }
    }
}
