package com.semanticapi.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CosineSimilarity}.
 */
@DisplayName("CosineSimilarity")
class CosineSimilarityTest {

    @Nested
    @DisplayName("Identical vectors")
    class Identical {
        @Test void identicalVectors_returnOne() {
            List<Float> v = List.of(0.5f, 0.3f, 0.8f);
            assertThat(CosineSimilarity.compute(v, v)).isEqualTo(1.0);
        }

        @Test void singleElement_identicalPositive_returnOne() {
            assertThat(CosineSimilarity.compute(List.of(1.0f), List.of(1.0f))).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Orthogonal (completely different)")
    class Orthogonal {
        @Test void orthogonalVectors_returnZero() {
            List<Float> a = List.of(1.0f, 0.0f);
            List<Float> b = List.of(0.0f, 1.0f);
            assertThat(CosineSimilarity.compute(a, b)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Partial similarity")
    class Partial {
        @Test void similarVectors_betweenZeroAndOne() {
            List<Float> a = List.of(1.0f, 1.0f, 0.0f);
            List<Float> b = List.of(1.0f, 0.0f, 0.0f);
            double sim = CosineSimilarity.compute(a, b);
            assertThat(sim).isBetween(0.0, 1.0);
            assertThat(sim).isGreaterThan(0.5);
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        @Test void zeroVector_returnsZero() {
            List<Float> zero = List.of(0.0f, 0.0f, 0.0f);
            List<Float> v    = List.of(1.0f, 0.0f, 0.0f);
            assertThat(CosineSimilarity.compute(zero, v)).isEqualTo(0.0);
        }

        @Test void emptyVectors_returnsZero() {
            assertThat(CosineSimilarity.compute(List.of(), List.of())).isEqualTo(0.0);
        }

        @Test void nullVectors_returnsZero() {
            assertThat(CosineSimilarity.compute(null, null)).isEqualTo(0.0);
        }

        @Test void dimensionMismatch_throwsException() {
            List<Float> a = List.of(1.0f, 2.0f);
            List<Float> b = List.of(1.0f);
            assertThatThrownBy(() -> CosineSimilarity.compute(a, b))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mismatch");
        }

        @Test void resultClamped_neverNegative() {
            // Negative dot product → clamped to 0
            List<Float> a = List.of(1.0f, 0.0f);
            List<Float> b = List.of(-1.0f, 0.0f);
            assertThat(CosineSimilarity.compute(a, b)).isEqualTo(0.0);
        }
    }
}
