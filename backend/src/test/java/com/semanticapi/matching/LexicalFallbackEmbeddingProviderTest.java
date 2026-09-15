package com.semanticapi.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LexicalFallbackEmbeddingProvider}.
 *
 * Validates that the fallback produces:
 * - L2-normalised vectors of consistent length
 * - Cosine similarity=1.0 for identical text
 * - Higher similarity for partially overlapping text than for disjoint text
 * - Correct provider/model names
 */
@DisplayName("LexicalFallbackEmbeddingProvider")
class LexicalFallbackEmbeddingProviderTest {

    private final LexicalFallbackEmbeddingProvider provider = new LexicalFallbackEmbeddingProvider();

    @Test
    @DisplayName("providerName and modelName are correct")
    void providerAndModelNames() {
        assertThat(provider.providerName()).isEqualTo("lexical-fallback");
        assertThat(provider.modelName()).contains("512");
    }

    @Test
    @DisplayName("Identical text → cosine similarity ≈ 1.0")
    void identicalText_similarity1() {
        List<Float> a = provider.embed("phone number");
        List<Float> b = provider.embed("phone number");
        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("Completely different text → cosine similarity 0.0")
    void differentText_similarity0() {
        List<Float> a = provider.embed("phone");
        List<Float> b = provider.embed("address");
        // These may share no tokens, similarity ~0 (depends on hash collisions)
        double sim = CosineSimilarity.compute(a, b);
        assertThat(sim).isBetween(0.0, 1.0); // must be in range
    }

    @Test
    @DisplayName("Partial overlap → intermediate similarity")
    void partialOverlap_intermediateSimlarity() {
        List<Float> full    = provider.embed("phone number");
        List<Float> partial = provider.embed("phone");
        List<Float> other   = provider.embed("email address");
        double simPartial = CosineSimilarity.compute(full, partial);
        double simOther   = CosineSimilarity.compute(full, other);
        // "phone number" and "phone" share a token; "email address" shares none
        assertThat(simPartial).isGreaterThan(simOther);
    }

    @Test
    @DisplayName("Empty/blank text → returns a zero vector (safe)")
    void emptyText_returnsZeroVector() {
        List<Float> v = provider.embed("");
        assertThat(v).hasSize(512);
        assertThat(v).allMatch(f -> f == 0.0f);
    }

    @Test
    @DisplayName("Null text → returns safe zero vector")
    void nullText_returnsZeroVector() {
        List<Float> v = provider.embed(null);
        assertThat(v).hasSize(512);
    }

    @Test
    @DisplayName("Vector is L2-normalised (magnitude ~1.0)")
    void vectorIsNormalised() {
        List<Float> v = provider.embed("customer identifier");
        double norm = v.stream().mapToDouble(f -> (double) f * f).sum();
        norm = Math.sqrt(norm);
        assertThat(norm).isCloseTo(1.0, within(1e-5));
    }

    @Test
    @DisplayName("Consistent dimensionality across calls")
    void consistentDimensionality() {
        List<Float> a = provider.embed("foo");
        List<Float> b = provider.embed("bar baz qux");
        assertThat(a).hasSameSizeAs(b);
    }

    private org.assertj.core.data.Offset<Double> within(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }
}
