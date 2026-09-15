package com.semanticapi.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Deterministic, lexical-only fallback {@link EmbeddingProvider}.
 *
 * <p>Used when the primary provider (e.g. Ollama) is unavailable.
 *
 * <h2>How it works</h2>
 * <p>This provider does NOT produce real embedding vectors.
 * Instead, it encodes a text string as a sparse bag-of-words style vector
 * over a fixed vocabulary built from the input tokens.
 *
 * <p>The resulting cosine similarity therefore reflects <em>lexical token overlap</em>
 * (similar to Jaccard similarity on token sets), NOT semantic meaning.
 *
 * <p>This is intentionally transparent:
 * <ul>
 *   <li>The session records {@code embeddingProvider="lexical-fallback"}</li>
 *   <li>Candidates record {@code notes="computed by lexical-fallback, not semantic"}</li>
 *   <li>No code path pretends this is semantic</li>
 * </ul>
 *
 * <h2>Vector construction</h2>
 * <p>Text is split on whitespace. Each unique token gets a fixed index in a global
 * character-hash-based slot (similar to hashing trick with 256 buckets).
 * The vector value for each slot is the token's TF-IDF weight within this request
 * (approximated as binary 1.0 per token occurrence).
 *
 * <p>Because cosine similarity on identical bag-of-words vectors returns 1.0 and
 * on disjoint sets returns 0.0, this correctly distinguishes exact matches from
 * completely different fields. Partial matches (shared tokens) produce intermediate
 * scores.  This matches the Jaccard token overlap metric.
 *
 * <h2>When is it used?</h2>
 * <ul>
 *   <li>When {@code ai.embedding.provider=lexical-fallback} is explicitly configured</li>
 *   <li>When {@code ai.embedding.fallback-on-error=true} and Ollama is unreachable</li>
 * </ul>
 */
public class LexicalFallbackEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(LexicalFallbackEmbeddingProvider.class);
    private static final int BUCKET_COUNT = 512;

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return Collections.nCopies(BUCKET_COUNT, 0.0f);
        }

        log.debug("LexicalFallback: embedding '{}' using token hash-bucket method", text);

        float[] vector = new float[BUCKET_COUNT];
        String[] tokens = text.toLowerCase(Locale.ROOT).trim().split("\\s+");

        for (String token : tokens) {
            if (!token.isBlank()) {
                int bucket = Math.abs(token.hashCode()) % BUCKET_COUNT;
                vector[bucket] += 1.0f;
            }
        }

        // L2-normalize the vector so cosine similarity = dot product
        double norm = 0.0;
        for (float v : vector) norm += (double) v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        }

        List<Float> result = new ArrayList<>(BUCKET_COUNT);
        for (float v : vector) result.add(v);
        return result;
    }

    @Override
    public String providerName() { return "lexical-fallback"; }

    @Override
    public String modelName() { return "token-hash-bucket-512"; }
}
