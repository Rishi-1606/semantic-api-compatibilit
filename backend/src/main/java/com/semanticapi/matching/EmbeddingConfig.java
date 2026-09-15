package com.semanticapi.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI/embedding subsystem.
 *
 * <p>All values are controlled via {@code application.yml} under the {@code ai.embedding}
 * prefix — no code changes needed to switch providers or models.
 *
 * <h2>Example</h2>
 * <pre>
 * ai:
 *   embedding:
 *     provider: ollama           # ollama | lexical-fallback
 *     base-url: http://localhost:11434
 *     model: nomic-embed-text
 *     fallback-on-error: true    # use lexical-fallback when provider fails
 *     timeout-seconds: 10
 *     auto-approve-threshold: 0.90
 *     review-threshold: 0.70
 * </pre>
 */
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingConfig {

    /** Active provider: "ollama" or "lexical-fallback". */
    private String provider = "ollama";

    /** Base URL of the embedding API (used by OllamaEmbeddingProvider). */
    private String baseUrl = "http://localhost:11434";

    /** Model name to use (e.g. "nomic-embed-text"). */
    private String model = "nomic-embed-text";

    /**
     * When true, fall back to {@link LexicalFallbackEmbeddingProvider} if the
     * primary provider throws an exception.  When false, propagate the error.
     */
    private boolean fallbackOnError = true;

    /** HTTP timeout in seconds for embedding API calls. */
    private int timeoutSeconds = 10;

    /**
     * Confidence ≥ this threshold → AUTO_APPROVED.
     * Default 0.90 as per spec.
     */
    private double autoApproveThreshold = 0.90;

    /**
     * Confidence between this and autoApproveThreshold → REQUIRES_REVIEW.
     * Default 0.70 as per spec.
     */
    private double reviewThreshold = 0.70;

    // ── Getters / Setters ─────────────────────────────────────

    public String  getProvider()             { return provider; }
    public void    setProvider(String p)     { this.provider = p; }
    public String  getBaseUrl()              { return baseUrl; }
    public void    setBaseUrl(String u)      { this.baseUrl = u; }
    public String  getModel()               { return model; }
    public void    setModel(String m)        { this.model = m; }
    public boolean isFallbackOnError()       { return fallbackOnError; }
    public void    setFallbackOnError(boolean f) { this.fallbackOnError = f; }
    public int     getTimeoutSeconds()       { return timeoutSeconds; }
    public void    setTimeoutSeconds(int t)  { this.timeoutSeconds = t; }
    public double  getAutoApproveThreshold() { return autoApproveThreshold; }
    public void    setAutoApproveThreshold(double t) { this.autoApproveThreshold = t; }
    public double  getReviewThreshold()      { return reviewThreshold; }
    public void    setReviewThreshold(double t) { this.reviewThreshold = t; }
}
