package com.semanticapi.matching;

import com.semanticapi.model.MatchCandidate;
import com.semanticapi.model.NlpFieldResult;
import com.semanticapi.model.SchemaField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Core semantic matching logic for Phase 6.
 *
 * <p>Given a set of source fields (with their NLP results) and target fields,
 * this class produces a list of {@link CandidatePair} records — one per
 * (source, target) combination — ranked by confidence score.
 *
 * <h2>Score Calculation</h2>
 * <pre>
 *   confidence = cosine_similarity × type_factor × path_factor
 *
 *   cosine_similarity:
 *     Cosine distance between embedding vectors of the normalized field names.
 *     Computed by {@link CosineSimilarity}.
 *
 *   type_factor (schema context — data type compatibility):
 *     same type group    → 1.0
 *     compatible types   → 0.85   e.g. integer ↔ number, string ↔ enum
 *     incompatible types → 0.6    e.g. string  ↔ integer
 *
 *   path_factor (structural context — field path similarity):
 *     root-level vs root-level  → 1.0
 *     same depth                → 1.0
 *     depth differs by 1        → 0.9
 *     depth differs by 2+       → 0.8
 * </pre>
 *
 * <h2>Context Awareness</h2>
 * <p>Per spec section 6.6: a string field is never matched with an integer field at
 * full confidence even if the names are semantically similar.  The type_factor
 * penalty handles this without hard-coding any field names.
 *
 * <h2>Design Principle</h2>
 * <p>This class contains NO field-name-specific logic.  It operates purely on
 * embedding vectors, normalized strings, and schema metadata.  It works for any
 * REST/JSON API domain.
 */
@Component
public class SemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatcher.class);

    /**
     * Intermediate holder before persistence — avoids creating {@link MatchCandidate}
     * entities inside the matching logic.
     */
    public record CandidatePair(
            SchemaField sourceField,
            SchemaField targetField,
            String sourceNormalized,
            String targetNormalized,
            double cosineSimilarity,
            double typeFactor,
            double pathFactor,
            double confidence
    ) {}

    /**
     * Produce all (source × target) candidate pairs, ranked by confidence.
     *
     * @param sourceFields NLP results for all source schema fields
     * @param targetFields NLP results for all target schema fields
     * @param provider     the active embedding provider (already resolved by factory)
     * @return ranked list of candidate pairs (highest confidence first)
     */
    public List<CandidatePair> match(
            List<NlpFieldResult> sourceFields,
            List<NlpFieldResult> targetFields,
            EmbeddingProvider provider) {

        if (sourceFields.isEmpty() || targetFields.isEmpty()) {
            return List.of();
        }

        // ── 1. Embed all source and target normalized strings ──────────────────
        log.debug("Embedding {} source + {} target fields via {}",
                sourceFields.size(), targetFields.size(), provider.providerName());

        List<List<Float>> srcVectors = embed(sourceFields, provider);
        List<List<Float>> tgtVectors = embed(targetFields, provider);

        // ── 2. Cross-product: compute confidence for every (src, tgt) pair ────
        List<CandidatePair> pairs = new ArrayList<>(sourceFields.size() * targetFields.size());

        for (int i = 0; i < sourceFields.size(); i++) {
            NlpFieldResult src = sourceFields.get(i);
            List<Float> srcVec = srcVectors.get(i);

            for (int j = 0; j < targetFields.size(); j++) {
                NlpFieldResult tgt = targetFields.get(j);
                List<Float> tgtVec = tgtVectors.get(j);

                double cosine     = CosineSimilarity.compute(srcVec, tgtVec);
                double typeFactor = computeTypeFactor(src.getSchemaField(), tgt.getSchemaField());
                double pathFactor = computePathFactor(src.getSchemaField(), tgt.getSchemaField());
                double confidence = round3(cosine * typeFactor * pathFactor);

                pairs.add(new CandidatePair(
                        src.getSchemaField(),
                        tgt.getSchemaField(),
                        src.getNormalized(),
                        tgt.getNormalized(),
                        round3(cosine),
                        round3(typeFactor),
                        round3(pathFactor),
                        confidence
                ));
            }
        }

        // ── 3. Sort by confidence descending ─────────────────────────────────
        pairs.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));
        return pairs;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<List<Float>> embed(List<NlpFieldResult> fields, EmbeddingProvider provider) {
        List<List<Float>> vectors = new ArrayList<>(fields.size());
        for (NlpFieldResult field : fields) {
            String text = field.getNormalized();
            if (text == null || text.isBlank()) text = field.getOriginalName();
            vectors.add(provider.embed(text));
        }
        return vectors;
    }

    /**
     * Data-type compatibility factor.
     *
     * <p>Prevents high-confidence matches between semantically similar field names
     * that have incompatible types (e.g. "id" (string) vs "identifier" (integer)).
     * Per spec section 6.5.
     */
    private double computeTypeFactor(SchemaField src, SchemaField tgt) {
        String srcType = normalizeType(src.getFieldType());
        String tgtType = normalizeType(tgt.getFieldType());

        if (srcType == null || tgtType == null || srcType.equals(tgtType)) {
            return 1.0;
        }

        // Compatible numeric types
        if (isNumeric(srcType) && isNumeric(tgtType)) return 0.85;

        // String-like compatible types (string, enum, text, varchar, etc.)
        if (isStringLike(srcType) && isStringLike(tgtType)) return 0.95;

        // boolean stays compatible with boolean only
        if (srcType.equals("boolean") || tgtType.equals("boolean")) return 0.6;

        // array/object structural incompatibility
        if (srcType.equals("array") || tgtType.equals("array")) return 0.7;
        if (srcType.equals("object") || tgtType.equals("object")) return 0.75;

        // Catch-all for truly incompatible types (e.g. integer vs string)
        return 0.6;
    }

    /**
     * Path depth similarity factor.
     *
     * <p>Two fields at the same nesting depth are more likely to correspond
     * than a root-level field and a deeply nested field.
     */
    private double computePathFactor(SchemaField src, SchemaField tgt) {
        int srcDepth = fieldDepth(src.getFieldPath());
        int tgtDepth = fieldDepth(tgt.getFieldPath());
        int diff = Math.abs(srcDepth - tgtDepth);
        return switch (diff) {
            case 0 -> 1.0;
            case 1 -> 0.9;
            default -> 0.8;
        };
    }

    private String normalizeType(String type) {
        if (type == null) return null;
        return type.toLowerCase().trim();
    }

    private boolean isNumeric(String type) {
        return switch (type) {
            case "integer", "int", "long", "number", "float", "double",
                 "decimal", "numeric", "short", "bigdecimal" -> true;
            default -> false;
        };
    }

    private boolean isStringLike(String type) {
        return switch (type) {
            case "string", "str", "text", "varchar", "char", "enum", "uuid" -> true;
            default -> false;
        };
    }

    /** Count dots in a path to determine depth (e.g. "contact.phone" → depth 1). */
    private int fieldDepth(String path) {
        if (path == null || path.isBlank()) return 0;
        return (int) path.chars().filter(c -> c == '.').count();
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
