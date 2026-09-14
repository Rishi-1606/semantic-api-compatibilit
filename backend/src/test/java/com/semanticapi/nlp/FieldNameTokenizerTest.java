package com.semanticapi.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FieldNameTokenizer}.
 *
 * <p>Covers all naming conventions required by the spec:
 * camelCase, PascalCase, snake_case, SCREAMING_SNAKE, kebab-case,
 * spaces, acronyms, digits, nested paths, arrays, and edge cases.
 */
@DisplayName("FieldNameTokenizer — Unit Tests")
class FieldNameTokenizerTest {

    // ── camelCase ─────────────────────────────────────────────

    @Nested @DisplayName("camelCase")
    class CamelCase {

        @Test @DisplayName("T01 — phoneNumber → [phone, number]")
        void camelCase1() {
            assertThat(FieldNameTokenizer.tokenize("phoneNumber"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T02 — emailAddress → [email, address]")
        void camelCase2() {
            assertThat(FieldNameTokenizer.tokenize("emailAddress"))
                .containsExactly("email", "address");
        }

        @Test @DisplayName("T03 — customerId → [customer, id]")
        void camelCase3() {
            assertThat(FieldNameTokenizer.tokenize("customerId"))
                .containsExactly("customer", "id");
        }

        @Test @DisplayName("T04 — dateOfBirth → [date, of, birth]")
        void camelCase4() {
            assertThat(FieldNameTokenizer.tokenize("dateOfBirth"))
                .containsExactly("date", "of", "birth");
        }
    }

    // ── PascalCase ────────────────────────────────────────────

    @Nested @DisplayName("PascalCase")
    class PascalCase {

        @Test @DisplayName("T05 — PhoneNumber → [phone, number]")
        void pascalCase1() {
            assertThat(FieldNameTokenizer.tokenize("PhoneNumber"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T06 — FullName → [full, name]")
        void pascalCase2() {
            assertThat(FieldNameTokenizer.tokenize("FullName"))
                .containsExactly("full", "name");
        }

        @Test @DisplayName("T07 — CustomerEmailAddress → [customer, email, address]")
        void pascalCase3() {
            assertThat(FieldNameTokenizer.tokenize("CustomerEmailAddress"))
                .containsExactly("customer", "email", "address");
        }
    }

    // ── snake_case ────────────────────────────────────────────

    @Nested @DisplayName("snake_case")
    class SnakeCase {

        @Test @DisplayName("T08 — phone_number → [phone, number]")
        void snake1() {
            assertThat(FieldNameTokenizer.tokenize("phone_number"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T09 — customer_email_address → [customer, email, address]")
        void snake2() {
            assertThat(FieldNameTokenizer.tokenize("customer_email_address"))
                .containsExactly("customer", "email", "address");
        }
    }

    // ── SCREAMING_SNAKE ───────────────────────────────────────

    @Nested @DisplayName("SCREAMING_SNAKE_CASE")
    class ScreamingSnake {

        @Test @DisplayName("T10 — PHONE_NUMBER → [phone, number]")
        void screaming1() {
            assertThat(FieldNameTokenizer.tokenize("PHONE_NUMBER"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T11 — PHONE_NO → [phone, no]")
        void screaming2() {
            assertThat(FieldNameTokenizer.tokenize("PHONE_NO"))
                .containsExactly("phone", "no");
        }

        @Test @DisplayName("T12 — CUST_ADDR → [cust, addr]")
        void screaming3() {
            assertThat(FieldNameTokenizer.tokenize("CUST_ADDR"))
                .containsExactly("cust", "addr");
        }
    }

    // ── kebab-case ────────────────────────────────────────────

    @Nested @DisplayName("kebab-case")
    class KebabCase {

        @Test @DisplayName("T13 — phone-number → [phone, number]")
        void kebab1() {
            assertThat(FieldNameTokenizer.tokenize("phone-number"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T14 — customer-id → [customer, id]")
        void kebab2() {
            assertThat(FieldNameTokenizer.tokenize("customer-id"))
                .containsExactly("customer", "id");
        }
    }

    // ── spaces ────────────────────────────────────────────────

    @Nested @DisplayName("spaces")
    class Spaces {

        @Test @DisplayName("T15 — 'phone number' → [phone, number]")
        void spaces1() {
            assertThat(FieldNameTokenizer.tokenize("phone number"))
                .containsExactly("phone", "number");
        }

        @Test @DisplayName("T16 — 'phone  number' (multiple spaces) → [phone, number]")
        void spaces2() {
            assertThat(FieldNameTokenizer.tokenize("phone  number"))
                .containsExactly("phone", "number");
        }
    }

    // ── acronyms ──────────────────────────────────────────────

    @Nested @DisplayName("Acronyms")
    class Acronyms {

        @Test @DisplayName("T17 — XMLParser → [xml, parser]")
        void acronym1() {
            assertThat(FieldNameTokenizer.tokenize("XMLParser"))
                .containsExactly("xml", "parser");
        }

        @Test @DisplayName("T18 — APIKey → [api, key]")
        void acronym2() {
            assertThat(FieldNameTokenizer.tokenize("APIKey"))
                .containsExactly("api", "key");
        }
    }

    // ── digits ────────────────────────────────────────────────

    @Nested @DisplayName("Digit boundaries")
    class Digits {

        @Test @DisplayName("T19 — address1 → [address, 1]")
        void digit1() {
            assertThat(FieldNameTokenizer.tokenize("address1"))
                .containsExactly("address", "1");
        }

        @Test @DisplayName("T20 — phone2mobile → [phone, 2, mobile]")
        void digit2() {
            assertThat(FieldNameTokenizer.tokenize("phone2mobile"))
                .containsExactly("phone", "2", "mobile");
        }
    }

    // ── nested paths ──────────────────────────────────────────

    @Nested @DisplayName("Nested field paths (tokenizePath)")
    class NestedPaths {

        @Test @DisplayName("T21 — address.city → leaf=[city]")
        void nested1() {
            assertThat(FieldNameTokenizer.tokenizePath("address.city"))
                .containsExactly("city");
        }

        @Test @DisplayName("T22 — address.zipCode → leaf=[zip, code]")
        void nested2() {
            assertThat(FieldNameTokenizer.tokenizePath("address.zipCode"))
                .containsExactly("zip", "code");
        }

        @Test @DisplayName("T23 — items[*].productId → leaf=[product, id]")
        void nested3() {
            assertThat(FieldNameTokenizer.tokenizePath("items[*].productId"))
                .containsExactly("product", "id");
        }

        @Test @DisplayName("T24 — items[*] → leaf=[items]")
        void nested4() {
            assertThat(FieldNameTokenizer.tokenizePath("items[*]"))
                .containsExactly("items");
        }

        @Test @DisplayName("T25 — root level: name → leaf=[name]")
        void nested5() {
            assertThat(FieldNameTokenizer.tokenizePath("name"))
                .containsExactly("name");
        }
    }

    // ── edge cases ────────────────────────────────────────────

    @Nested @DisplayName("Edge cases")
    class EdgeCases {

        @Test @DisplayName("T26 — null → empty list")
        void edgeNull() {
            assertThat(FieldNameTokenizer.tokenize(null)).isEmpty();
        }

        @Test @DisplayName("T27 — blank string → empty list")
        void edgeBlank() {
            assertThat(FieldNameTokenizer.tokenize("")).isEmpty();
            assertThat(FieldNameTokenizer.tokenize("  ")).isEmpty();
        }

        @Test @DisplayName("T28 — single word → [word]")
        void edgeSingle() {
            assertThat(FieldNameTokenizer.tokenize("name")).containsExactly("name");
        }

        @Test @DisplayName("T29 — already lowercase → unchanged")
        void edgeLower() {
            assertThat(FieldNameTokenizer.tokenize("email")).containsExactly("email");
        }
    }
}
