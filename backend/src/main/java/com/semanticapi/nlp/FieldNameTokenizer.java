package com.semanticapi.nlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Splits API field names into lowercase word tokens, handling all common
 * naming conventions found in REST APIs.
 *
 * <h2>Supported conventions</h2>
 * <ul>
 *   <li><b>camelCase</b>     — {@code phoneNumber} → ["phone", "number"]</li>
 *   <li><b>PascalCase</b>    — {@code PhoneNumber} → ["phone", "number"]</li>
 *   <li><b>snake_case</b>    — {@code phone_number} → ["phone", "number"]</li>
 *   <li><b>SCREAMING_SNAKE</b>— {@code PHONE_NUMBER} → ["phone", "number"]</li>
 *   <li><b>kebab-case</b>    — {@code phone-number} → ["phone", "number"]</li>
 *   <li><b>dot.notation</b>  — {@code phone.number} → ["phone", "number"]</li>
 *   <li><b>spaces</b>        — {@code phone number} → ["phone", "number"]</li>
 *   <li><b>mixed</b>         — {@code PHONE_No}     → ["phone", "no"]</li>
 *   <li><b>acronyms</b>      — {@code XMLParser}    → ["xml", "parser"]</li>
 *   <li><b>digits</b>        — {@code address1}     → ["address", "1"]</li>
 * </ul>
 *
 * <p>Nested field paths (e.g., {@code address.city} from Phase 4) are handled
 * by passing only the leaf name.  The caller is responsible for extracting the
 * leaf before tokenizing.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class FieldNameTokenizer {

    private FieldNameTokenizer() {}

    /**
     * Regex that splits on camelCase/PascalCase word boundaries.
     *
     * <p>Algorithm (applied left-to-right):
     * <ol>
     *   <li>Insert a boundary before an uppercase letter that follows a lowercase letter
     *       (e.g., {@code phoneNumber} → {@code phone|Number})</li>
     *   <li>Insert a boundary between a run of uppercase letters and the next uppercase
     *       followed by a lowercase (handles acronyms like {@code XMLParser} →
     *       {@code XML|Parser})</li>
     *   <li>Insert a boundary between a letter and a digit (e.g., {@code address1} →
     *       {@code address|1})</li>
     * </ol>
     */
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile(
        "(?<=[a-z0-9])(?=[A-Z])" +          // camelCase boundary
        "|(?<=[A-Z])(?=[A-Z][a-z])" +        // acronym before CamelWord: XMLParser
        "|(?<=[a-zA-Z])(?=[0-9])" +          // letter-digit: address1
        "|(?<=[0-9])(?=[a-zA-Z])"            // digit-letter: address1extra
    );

    /** Characters used as explicit separators: _ - . space */
    private static final Pattern SEPARATOR = Pattern.compile("[_\\-. ]+");

    /**
     * Tokenize a single field name into lowercase word tokens.
     *
     * @param fieldName the raw field name; may be null or blank
     * @return an unmodifiable list of lowercase tokens; empty if input is blank
     */
    public static List<String> tokenize(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return List.of();
        }

        // Step 1: Insert camelCase/PascalCase boundaries
        String spaced = CAMEL_BOUNDARY.matcher(fieldName).replaceAll(" ");

        // Step 2: Replace explicit separators (_, -, ., multiple spaces) with single space
        spaced = SEPARATOR.matcher(spaced).replaceAll(" ");

        // Step 3: Split on spaces, lowercase each token, remove empties
        return Arrays.stream(spaced.trim().split(" "))
                .map(String::toLowerCase)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Extract the leaf name from a dot-notation path before tokenizing.
     *
     * <p>For example:
     * <ul>
     *   <li>{@code address.city} → leaf = "city"</li>
     *   <li>{@code items[*].productId} → leaf = "productId"</li>
     *   <li>{@code name} → leaf = "name"</li>
     * </ul>
     *
     * @param fieldPath the full dot-notation path as stored by Phase 4
     * @return lowercase tokens of the leaf name
     */
    public static List<String> tokenizePath(String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return List.of();
        }
        // For array items like "items[*]" or "items[*].productId", strip [*] markers
        String cleaned = fieldPath.replaceAll("\\[\\*]", "");
        // Take only the last segment of the dot path
        int lastDot = cleaned.lastIndexOf('.');
        String leaf = (lastDot >= 0) ? cleaned.substring(lastDot + 1) : cleaned;
        return tokenize(leaf);
    }
}
