package com.semanticapi.dto;

/**
 * Response body for POST /api/transform/test.
 *
 * <p>Returns the result of applying a single transformation rule to sample JSON
 * in-process (no external API call).
 */
public class TransformTestResponse {

    private Long    ruleId;
    private String  operation;
    private String  sourcePath;
    private String  targetPath;
    private String  inputJson;
    private String  outputJson;
    private boolean success;
    private String  errorMessage;

    // ── Factory ───────────────────────────────────────────────

    public static TransformTestResponse success(Long ruleId, String operation,
                                                String sourcePath, String targetPath,
                                                String inputJson, String outputJson) {
        TransformTestResponse r = new TransformTestResponse();
        r.ruleId      = ruleId;
        r.operation   = operation;
        r.sourcePath  = sourcePath;
        r.targetPath  = targetPath;
        r.inputJson   = inputJson;
        r.outputJson  = outputJson;
        r.success     = true;
        r.errorMessage = null;
        return r;
    }

    public static TransformTestResponse failure(Long ruleId, String operation,
                                                String sourcePath, String targetPath,
                                                String inputJson, String errorMessage) {
        TransformTestResponse r = new TransformTestResponse();
        r.ruleId      = ruleId;
        r.operation   = operation;
        r.sourcePath  = sourcePath;
        r.targetPath  = targetPath;
        r.inputJson   = inputJson;
        r.outputJson  = null;
        r.success     = false;
        r.errorMessage = errorMessage;
        return r;
    }

    // ── Getters ───────────────────────────────────────────────

    public Long    getRuleId()       { return ruleId; }
    public String  getOperation()    { return operation; }
    public String  getSourcePath()   { return sourcePath; }
    public String  getTargetPath()   { return targetPath; }
    public String  getInputJson()    { return inputJson; }
    public String  getOutputJson()   { return outputJson; }
    public boolean isSuccess()       { return success; }
    public String  getErrorMessage() { return errorMessage; }
}
