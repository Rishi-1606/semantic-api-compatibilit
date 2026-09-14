package com.semanticapi.nlp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configurable abbreviation dictionary for the NLP pipeline.
 *
 * <p>Maps common API field name abbreviations to their full forms.
 * Entries are loaded from {@code application.yml} under the prefix
 * {@code nlp.abbreviations}, so new mappings can be added without
 * touching Java code.
 *
 * <h2>Design rules (per spec)</h2>
 * <ul>
 *   <li>Generic domain-agnostic abbreviations only — no Customer-API-specific
 *       semantic mappings (e.g. do NOT map "name" → "fullName").</li>
 *   <li>Only shorten → lengthen mappings (abbreviation → full word).</li>
 *   <li>Applied <em>after</em> tokenization, token by token.</li>
 *   <li>Lookup is case-insensitive (tokens are always lowercase by this point).</li>
 * </ul>
 *
 * <h2>Example configuration</h2>
 * <pre>
 * nlp:
 *   abbreviations:
 *     mob: mobile
 *     ph: phone
 *     no: number
 *     num: number
 *     addr: address
 *     str: street
 *     cust: customer
 *     id: identifier
 *     ...
 * </pre>
 */
@ConfigurationProperties(prefix = "nlp")
public class AbbreviationDictionary {

    /**
     * Abbreviation → full-form map, injected from application.yml.
     * Key: abbreviation (lowercase), Value: full word (lowercase).
     */
    private Map<String, String> abbreviations = new HashMap<>();

    /**
     * Expand a single token if it has an abbreviation entry.
     *
     * @param token a single lowercase token
     * @return the expanded form if found, otherwise the original token unchanged
     */
    public String expand(String token) {
        if (token == null) return null;
        return abbreviations.getOrDefault(token.toLowerCase(), token);
    }

    /**
     * Check whether a token is a known abbreviation.
     */
    public boolean isAbbreviation(String token) {
        return token != null && abbreviations.containsKey(token.toLowerCase());
    }

    /**
     * Return an unmodifiable view of all entries for diagnostic purposes.
     */
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(abbreviations);
    }

    // ── Setter used by Spring Boot @ConfigurationProperties ──

    public void setAbbreviations(Map<String, String> abbreviations) {
        this.abbreviations = new HashMap<>(abbreviations);
    }

    public Map<String, String> getAbbreviations() {
        return abbreviations;
    }
}
