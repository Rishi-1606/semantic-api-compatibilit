package com.semanticapi.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NlpPipelineService}.
 *
 * <p>Uses a minimal in-process {@link AbbreviationDictionary} so these tests
 * run without Spring context (pure unit tests).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>camelCase with no abbreviation</li>
 *   <li>Abbreviation expansion (spec examples: mob, ph, no, addr, cust)</li>
 *   <li>Combined: abbreviated tokens in SCREAMING_SNAKE</li>
 *   <li>Nested field path — leaf is processed, path preserved</li>
 *   <li>Array paths</li>
 *   <li>No abbreviation — passthrough</li>
 *   <li>Null / blank input</li>
 *   <li>Multi-word field names</li>
 * </ul>
 */
@DisplayName("NlpPipelineService — Unit Tests")
class NlpPipelineServiceTest {

    private NlpPipelineService pipeline;
    private AbbreviationDictionary dictionary;

    @BeforeEach
    void setUp() {
        // Configure a minimal dictionary (mirrors application.yml subset)
        dictionary = new AbbreviationDictionary();
        dictionary.setAbbreviations(Map.of(
            "mob",  "mobile",
            "ph",   "phone",
            "no",   "number",
            "num",  "number",
            "addr", "address",
            "str",  "street",
            "cust", "customer",
            "id",   "identifier",
            "dt",   "date",
            "qty",  "quantity"
        ));
        pipeline = new NlpPipelineService(dictionary);
    }

    // ── Spec examples ─────────────────────────────────────────

    @Test
    @DisplayName("T01 — phoneNumber: camelCase, no abbreviation")
    void phoneNumber() {
        NlpResult r = pipeline.process("phoneNumber");
        assertThat(r.getOriginalFieldName()).isEqualTo("phoneNumber");
        assertThat(r.getRawTokens()).containsExactly("phone", "number");
        assertThat(r.getExpandedTokens()).containsExactly("phone", "number");
        assertThat(r.getNormalized()).isEqualTo("phone number");
    }

    @Test
    @DisplayName("T02 — mob: single abbreviation → mobile")
    void mob() {
        NlpResult r = pipeline.process("mob");
        assertThat(r.getRawTokens()).containsExactly("mob");
        assertThat(r.getExpandedTokens()).containsExactly("mobile");
        assertThat(r.getNormalized()).isEqualTo("mobile");
    }

    @Test
    @DisplayName("T03 — mob_no: snake_case + abbreviations → mobile number")
    void mobNo() {
        NlpResult r = pipeline.process("mob_no");
        assertThat(r.getRawTokens()).containsExactly("mob", "no");
        assertThat(r.getExpandedTokens()).containsExactly("mobile", "number");
        assertThat(r.getNormalized()).isEqualTo("mobile number");
    }

    @Test
    @DisplayName("T04 — CUST_ADDR: SCREAMING_SNAKE + abbreviations → customer address")
    void custAddr() {
        NlpResult r = pipeline.process("CUST_ADDR");
        assertThat(r.getRawTokens()).containsExactly("cust", "addr");
        assertThat(r.getExpandedTokens()).containsExactly("customer", "address");
        assertThat(r.getNormalized()).isEqualTo("customer address");
    }

    @Test
    @DisplayName("T05 — phone_number: snake_case, no abbreviation")
    void phoneNumber_snake() {
        NlpResult r = pipeline.process("phone_number");
        assertThat(r.getRawTokens()).containsExactly("phone", "number");
        assertThat(r.getExpandedTokens()).containsExactly("phone", "number");
        assertThat(r.getNormalized()).isEqualTo("phone number");
    }

    @Test
    @DisplayName("T06 — PhoneNumber: PascalCase, no abbreviation")
    void phoneNumber_pascal() {
        NlpResult r = pipeline.process("PhoneNumber");
        assertThat(r.getNormalized()).isEqualTo("phone number");
    }

    @Test
    @DisplayName("T07 — phone-number: kebab-case, no abbreviation")
    void phoneNumber_kebab() {
        NlpResult r = pipeline.process("phone-number");
        assertThat(r.getNormalized()).isEqualTo("phone number");
    }

    @Test
    @DisplayName("T08 — PHONE_NO: SCREAMING_SNAKE + 'no' abbreviation → phone number")
    void phoneNo() {
        NlpResult r = pipeline.process("PHONE_NO");
        assertThat(r.getRawTokens()).containsExactly("phone", "no");
        assertThat(r.getExpandedTokens()).containsExactly("phone", "number");
        assertThat(r.getNormalized()).isEqualTo("phone number");
    }

    // ── Nested paths ──────────────────────────────────────────

    @Test
    @DisplayName("T09 — nested path: process leaf, preserve full path")
    void nestedPath() {
        NlpResult r = pipeline.process("city", "address.city");
        assertThat(r.getOriginalFieldName()).isEqualTo("city");
        assertThat(r.getFieldPath()).isEqualTo("address.city");
        assertThat(r.getNormalized()).isEqualTo("city");
    }

    @Test
    @DisplayName("T10 — array path items[*].productId: leaf = productId")
    void arrayPath() {
        NlpResult r = pipeline.process("productId", "items[*].productId");
        assertThat(r.getRawTokens()).containsExactly("product", "id");
        assertThat(r.getExpandedTokens()).containsExactly("product", "identifier");
        assertThat(r.getNormalized()).isEqualTo("product identifier");
        assertThat(r.getFieldPath()).isEqualTo("items[*].productId");
    }

    // ── No expansion (passthrough) ─────────────────────────────

    @Test
    @DisplayName("T11 — name: single word, no expansion → name")
    void noExpansion() {
        NlpResult r = pipeline.process("name");
        assertThat(r.getRawTokens()).containsExactly("name");
        assertThat(r.getExpandedTokens()).containsExactly("name");
        assertThat(r.getNormalized()).isEqualTo("name");
    }

    @Test
    @DisplayName("T12 — fullName: camelCase, no expansion → full name")
    void fullName() {
        NlpResult r = pipeline.process("fullName");
        assertThat(r.getNormalized()).isEqualTo("full name");
        assertThat(r.getRawTokens()).containsExactly("full", "name");
    }

    // ── Multi-word ─────────────────────────────────────────────

    @Test
    @DisplayName("T13 — dateOfBirth: multi-word camelCase")
    void dateOfBirth() {
        NlpResult r = pipeline.process("dateOfBirth");
        assertThat(r.getRawTokens()).containsExactly("date", "of", "birth");
        assertThat(r.getNormalized()).isEqualTo("date of birth");
    }

    @Test
    @DisplayName("T14 — orderQtyNum: combined abbreviations → order quantity number")
    void orderQtyNum() {
        NlpResult r = pipeline.process("orderQtyNum");
        assertThat(r.getExpandedTokens()).containsExactly("order", "quantity", "number");
        assertThat(r.getNormalized()).isEqualTo("order quantity number");
    }

    // ── Edge cases ─────────────────────────────────────────────

    @Test
    @DisplayName("T15 — null input → empty NlpResult")
    void nullInput() {
        NlpResult r = pipeline.process(null);
        assertThat(r.getRawTokens()).isEmpty();
        assertThat(r.getExpandedTokens()).isEmpty();
        assertThat(r.getNormalized()).isEmpty();
    }

    @Test
    @DisplayName("T16 — blank input → empty NlpResult")
    void blankInput() {
        NlpResult r = pipeline.process("   ");
        assertThat(r.getNormalized()).isEmpty();
    }

    @Test
    @DisplayName("T17 — tokens preserved: rawTokens ≠ expandedTokens when abbreviation used")
    void rawVsExpanded() {
        NlpResult r = pipeline.process("mobNo");
        assertThat(r.getRawTokens()).containsExactly("mob", "no");
        assertThat(r.getExpandedTokens()).containsExactly("mobile", "number");
        // raw and expanded are different
        assertThat(r.getRawTokens()).isNotEqualTo(r.getExpandedTokens());
    }

    @Test
    @DisplayName("T18 — no abbreviation: rawTokens == expandedTokens")
    void rawEqualsExpanded() {
        NlpResult r = pipeline.process("customer");
        assertThat(r.getRawTokens()).isEqualTo(r.getExpandedTokens());
    }
}
