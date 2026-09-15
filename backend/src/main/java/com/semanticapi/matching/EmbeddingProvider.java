package com.semanticapi.matching;

import java.util.List;

/**
 * Pluggable embedding provider for Phase 6 semantic matching.
 *
 * <p>Implementations must produce dense vector embeddings for a given text string.
 * The embedding vectors are then compared using cosine similarity to determine
 * semantic relatedness between API field names.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link OllamaEmbeddingProvider} — calls a local Ollama server (default)</li>
 *   <li>{@link LexicalFallbackEmbeddingProvider} — deterministic token-overlap
 *       fallback used when the primary provider is unavailable</li>
 * </ul>
 *
 * <p>The active implementation is selected via the {@code ai.embedding.provider}
 * configuration property.  Business logic never depends on a concrete class.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>The returned vector must always have the same dimensionality for all inputs
 *       produced by the same provider instance.</li>
 *   <li>Throw {@link EmbeddingException} on unrecoverable errors.</li>
 *   <li>Never return null; return an empty list only when text is blank.</li>
 * </ul>
 */
public interface EmbeddingProvider {

    /**
     * Produce an embedding vector for the given text.
     *
     * @param text the text to embed (typically a normalized field name)
     * @return dense embedding vector as list of floats
     * @throws EmbeddingException if the provider fails and no fallback is available
     */
    List<Float> embed(String text);

    /**
     * Human-readable name of this provider (e.g. "ollama", "lexical-fallback").
     * Stored in {@link com.semanticapi.model.MatchSession#getEmbeddingProvider()}.
     */
    String providerName();

    /**
     * Human-readable model name (e.g. "nomic-embed-text").
     * Stored in {@link com.semanticapi.model.MatchSession#getEmbeddingModel()}.
     */
    String modelName();
}
