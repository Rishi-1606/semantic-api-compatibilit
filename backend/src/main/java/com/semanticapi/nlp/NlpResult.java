package com.semanticapi.nlp;

import java.util.List;

/**
 * Immutable in-memory result of the NLP preprocessing pipeline applied
 * to a single schema field name.
 *
 * <p>Produced by {@link NlpPipelineService} and consumed by Phase 6
 * (Semantic Matching Engine) to compare fields across API versions.
 *
 * <h2>Examples</h2>
 * <pre>
 *   Input:  "phoneNumber"
 *   Tokens: ["phone", "number"]
 *   Expanded: ["phone", "number"]
 *   Normalized: "phone number"
 *
 *   Input:  "mob"
 *   Tokens: ["mob"]
 *   Expanded: ["mobile"]
 *   Normalized: "mobile"
 *
 *   Input:  "mob_no"
 *   Tokens: ["mob", "no"]
 *   Expanded: ["mobile", "number"]
 *   Normalized: "mobile number"
 *
 *   Input:  "CUST_ADDR"
 *   Tokens: ["cust", "addr"]
 *   Expanded: ["customer", "address"]
 *   Normalized: "customer address"
 * </pre>
 *
 * <p>For nested paths (e.g., "address.city"), the pipeline is applied only
 * to the leaf field name ("city"), while the full path is preserved for
 * context-aware matching in Phase 6.
 */
public final class NlpResult {

    /** The original field name exactly as stored in the schema. */
    private final String       originalFieldName;

    /** The full dot-notation path (e.g. "address.city"). */
    private final String       fieldPath;

    /**
     * Raw tokens produced by splitting the field name on word boundaries.
     * No abbreviation expansion applied yet.
     * Example: "phoneNumber" → ["phone", "number"]
     */
    private final List<String> rawTokens;

    /**
     * Tokens after abbreviation expansion.
     * Example: ["mob", "no"] → ["mobile", "number"]
     */
    private final List<String> expandedTokens;

    /**
     * Final normalized string: expanded tokens joined by a single space,
     * all lowercase.  This is the primary input for Phase 6 comparison.
     * Example: "mobile number"
     */
    private final String       normalized;

    private NlpResult(Builder b) {
        this.originalFieldName = b.originalFieldName;
        this.fieldPath         = b.fieldPath;
        this.rawTokens         = List.copyOf(b.rawTokens);
        this.expandedTokens    = List.copyOf(b.expandedTokens);
        this.normalized        = b.normalized;
    }

    // ── Accessors ─────────────────────────────────────────────

    public String       getOriginalFieldName() { return originalFieldName; }
    public String       getFieldPath()         { return fieldPath; }
    public List<String> getRawTokens()         { return rawTokens; }
    public List<String> getExpandedTokens()    { return expandedTokens; }
    public String       getNormalized()        { return normalized; }

    @Override
    public String toString() {
        return "NlpResult{" +
               "original='" + originalFieldName + '\'' +
               ", tokens=" + rawTokens +
               ", expanded=" + expandedTokens +
               ", normalized='" + normalized + '\'' +
               '}';
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder(String originalFieldName, String fieldPath) {
        return new Builder(originalFieldName, fieldPath);
    }

    public static final class Builder {
        private final String originalFieldName;
        private final String fieldPath;
        private List<String> rawTokens     = List.of();
        private List<String> expandedTokens = List.of();
        private String       normalized    = "";

        private Builder(String originalFieldName, String fieldPath) {
            this.originalFieldName = originalFieldName;
            this.fieldPath         = fieldPath;
        }

        public Builder rawTokens(List<String> t)      { this.rawTokens = t;      return this; }
        public Builder expandedTokens(List<String> t) { this.expandedTokens = t; return this; }
        public Builder normalized(String n)           { this.normalized = n;      return this; }
        public NlpResult build()                      { return new NlpResult(this); }
    }
}
