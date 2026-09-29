package com.semanticapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/transform/test.
 *
 * <p>The tester applies a single transformation rule to sample JSON in-process,
 * without forwarding the request to any external API.
 *
 * <p>This is a safe preview facility and does NOT constitute a second runtime
 * transformation engine. Phase 10 Node.js TransformationExecutor remains the
 * authoritative runtime engine.
 */
public class TransformTestRequest {

    @NotNull(message = "ruleId must not be null")
    private Long ruleId;

    @NotBlank(message = "inputJson must not be blank")
    private String inputJson;

    public Long   getRuleId()              { return ruleId; }
    public void   setRuleId(Long id)       { this.ruleId = id; }

    public String getInputJson()           { return inputJson; }
    public void   setInputJson(String json){ this.inputJson = json; }
}
