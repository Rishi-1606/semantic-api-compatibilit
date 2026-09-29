package com.semanticapi.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semanticapi.controller.TransformTestController;
import com.semanticapi.dto.TransformTestRequest;
import com.semanticapi.dto.TransformTestResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.service.TransformTestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST layer unit tests for {@link TransformTestController}.
 *
 * <p>Uses {@code @WebMvcTest} — Spring MVC context only, no database.
 * All service calls are mocked.
 *
 * <h2>Covered scenarios</h2>
 * <ul>
 *   <li>POST /api/transform/test → 200 success with transformed JSON</li>
 *   <li>POST /api/transform/test → 200 with success=false for unsupported ops</li>
 *   <li>POST /api/transform/test → 400 when ruleId is null</li>
 *   <li>POST /api/transform/test → 400 when inputJson is blank</li>
 *   <li>POST /api/transform/test → 404 when rule not found</li>
 *   <li>POST /api/transform/test → 400 when inputJson is invalid JSON</li>
 * </ul>
 */
@WebMvcTest(TransformTestController.class)
@DisplayName("TransformTestController")
class TransformTestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransformTestService transformTestService;

    private static final String URL = "/api/transform/test";

    // ── Helper to build a valid success response ───────────────

    private TransformTestResponse buildSuccess() {
        return TransformTestResponse.success(
                5L, "RENAME", "name", "fullName",
                "{\"name\":\"Rishi\"}",
                "{\n  \"fullName\" : \"Rishi\"\n}");
    }

    private String body(Long ruleId, String inputJson) throws Exception {
        TransformTestRequest req = new TransformTestRequest();
        req.setRuleId(ruleId);
        req.setInputJson(inputJson);
        return objectMapper.writeValueAsString(req);
    }

    // ── Happy path ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/transform/test — success")
    class SuccessTests {

        @Test
        @DisplayName("valid rule + valid JSON → 200 with success=true and outputJson")
        void testRename_returnsSuccess() throws Exception {
            when(transformTestService.test(any())).thenReturn(buildSuccess());

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(5L, "{\"name\":\"Rishi\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.ruleId").value(5))
                    .andExpect(jsonPath("$.operation").value("RENAME"))
                    .andExpect(jsonPath("$.sourcePath").value("name"))
                    .andExpect(jsonPath("$.targetPath").value("fullName"))
                    .andExpect(jsonPath("$.outputJson").isNotEmpty())
                    .andExpect(jsonPath("$.errorMessage").doesNotExist());
        }

        @Test
        @DisplayName("unsupported operation (COMBINE) → 200 with success=false and errorMessage")
        void testUnsupportedOperation_returnsFailureInBody() throws Exception {
            TransformTestResponse failResponse = TransformTestResponse.failure(
                    3L, "COMBINE", "firstName", "fullName",
                    "{\"firstName\":\"Rishi\"}",
                    "COMBINE requires multiple source/target paths and cannot be previewed as a single-field test.");

            when(transformTestService.test(any())).thenReturn(failResponse);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(3L, "{\"firstName\":\"Rishi\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorMessage").isNotEmpty());
        }
    }

    // ── Validation errors → 400 ───────────────────────────────

    @Nested
    @DisplayName("POST /api/transform/test — 400 validation")
    class ValidationTests {

        @Test
        @DisplayName("null ruleId → 400 validation error")
        void nullRuleId_returns400() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ruleId\":null,\"inputJson\":\"{}\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("blank inputJson → 400 validation error")
        void blankInputJson_returns400() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ruleId\":1,\"inputJson\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("invalid JSON in inputJson → service throws IllegalArgumentException → 400")
        void invalidInputJson_returns400() throws Exception {
            when(transformTestService.test(any()))
                    .thenThrow(new IllegalArgumentException("inputJson is not valid JSON: unexpected char"));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(1L, "not-json")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    // ── Not found → 404 ──────────────────────────────────────

    @Nested
    @DisplayName("POST /api/transform/test — 404 not found")
    class NotFoundTests {

        @Test
        @DisplayName("non-existent ruleId → 404")
        void unknownRuleId_returns404() throws Exception {
            when(transformTestService.test(any()))
                    .thenThrow(new ResourceNotFoundException("TransformationRule", 999L));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(999L, "{\"name\":\"test\"}")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
