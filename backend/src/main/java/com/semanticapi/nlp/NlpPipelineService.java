package com.semanticapi.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * NLP preprocessing pipeline for Phase 5.
 *
 * <p>Applies the full preprocessing sequence to a single field name:
 * <ol>
 *   <li><b>Tokenize</b> — split on camelCase, snake_case, kebab-case, etc.
 *       via {@link FieldNameTokenizer}</li>
 *   <li><b>Expand abbreviations</b> — replace each token with its full form
 *       using {@link AbbreviationDictionary}</li>
 *   <li><b>Normalize</b> — join expanded tokens with single spaces (already
 *       lowercase from tokenizer)</li>
 * </ol>
 *
 * <p>This service is purely in-memory — it does not touch the database.
 * Persistence of NLP results is handled by {@link com.semanticapi.service.NlpAnalysisService}.
 *
 * <h2>Examples</h2>
 * <pre>
 *   process("phoneNumber")  → NlpResult{tokens=["phone","number"], normalized="phone number"}
 *   process("mob")          → NlpResult{tokens=["mob"], expanded=["mobile"], normalized="mobile"}
 *   process("mob_no")       → NlpResult{tokens=["mob","no"], expanded=["mobile","number"], normalized="mobile number"}
 *   process("CUST_ADDR")    → NlpResult{tokens=["cust","addr"], expanded=["customer","address"], normalized="customer address"}
 *   process("XMLParser")    → NlpResult{tokens=["xml","parser"], normalized="xml parser"}
 *   process("address.city") → treats "city" as leaf, path preserved
 * </pre>
 */
@Service
public class NlpPipelineService {

    private static final Logger log = LoggerFactory.getLogger(NlpPipelineService.class);

    private final AbbreviationDictionary dictionary;

    public NlpPipelineService(AbbreviationDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Run the NLP pipeline on a schema field.
     *
     * <p>The {@code fieldPath} is the full dot-notation path from Phase 4
     * (e.g., "address.city"). Tokenization is applied only to the leaf name
     * so that each word retains its individual meaning; the full path is
     * preserved for context-aware matching in Phase 6.
     *
     * @param fieldName the raw field name (leaf, e.g. "phoneNumber")
     * @param fieldPath the full path from Phase 4 (e.g. "address.phoneNumber")
     * @return {@link NlpResult} with raw tokens, expanded tokens, and normalized string
     */
    public NlpResult process(String fieldName, String fieldPath) {
        if (fieldName == null || fieldName.isBlank()) {
            log.debug("Empty fieldName at path '{}' — returning empty NlpResult", fieldPath);
            return NlpResult.builder("", fieldPath != null ? fieldPath : "")
                    .rawTokens(List.of())
                    .expandedTokens(List.of())
                    .normalized("")
                    .build();
        }

        // Step 1: Tokenize the leaf name
        List<String> raw = FieldNameTokenizer.tokenize(fieldName);

        // Step 2: Expand abbreviations token by token
        List<String> expanded = raw.stream()
                .map(dictionary::expand)
                .collect(Collectors.toUnmodifiableList());

        // Step 3: Join into normalized string
        String normalized = String.join(" ", expanded);

        log.trace("NLP: '{}' → raw={} → expanded={} → '{}'", fieldName, raw, expanded, normalized);

        return NlpResult.builder(fieldName, fieldPath != null ? fieldPath : fieldName)
                .rawTokens(raw)
                .expandedTokens(expanded)
                .normalized(normalized)
                .build();
    }

    /**
     * Convenience overload when field name and path are the same (root-level fields).
     */
    public NlpResult process(String fieldName) {
        return process(fieldName, fieldName);
    }
}
