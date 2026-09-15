package com.semanticapi.matching;

import java.util.List;

/**
 * Stateless cosine similarity calculator for embedding vectors.
 *
 * <h2>Formula</h2>
 * <pre>
 *   cosine(A, B) = (A · B) / (||A|| × ||B||)
 *
 *   where A · B = Σ(a_i × b_i)
 *   and ||A|| = sqrt(Σ(a_i²))
 * </pre>
 *
 * <p>Returns a value in [0, 1] (clamped; can be negative for orthogonal vectors
 * in some embedding spaces, but clamped to 0).
 *
 * <p>If either vector is zero-magnitude (all zeros), returns 0.0 rather than NaN.
 *
 * <p>If vectors have different dimensionality (can happen during provider switch),
 * throws {@link IllegalArgumentException}.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    /**
     * Compute cosine similarity between two embedding vectors.
     *
     * @param a first vector (must be non-null, same size as b)
     * @param b second vector (must be non-null, same size as a)
     * @return similarity in [0.0, 1.0]
     */
    public static double compute(List<Float> a, List<Float> b) {
        if (a == null || b == null) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: " + a.size() + " vs " + b.size()
                    + ". Ensure both fields are embedded by the same provider.");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            double ai = a.get(i);
            double bi = b.get(i);
            dot   += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }

        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);

        if (normA == 0.0 || normB == 0.0) return 0.0;

        double similarity = dot / (normA * normB);
        // Clamp to [0, 1] — some embedding models can produce slightly negative
        // dot products for very dissimilar concepts; treat as 0
        return Math.max(0.0, Math.min(1.0, similarity));
    }
}
