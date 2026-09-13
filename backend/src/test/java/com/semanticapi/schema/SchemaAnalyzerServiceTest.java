package com.semanticapi.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SchemaAnalyzerService}.
 *
 * <p>Covers all spec requirements:
 * <ul>
 *   <li>Simple flat field extraction (spec example format)</li>
 *   <li>JSON Schema properties format</li>
 *   <li>Data types (string, integer, number, boolean, object, array)</li>
 *   <li>Required vs optional fields</li>
 *   <li>Nested objects with dot-notation paths</li>
 *   <li>Arrays (primitive items and object items)</li>
 *   <li>Enum values</li>
 *   <li>Field descriptions</li>
 *   <li>Null/blank/invalid schema handling</li>
 * </ul>
 */
@DisplayName("SchemaAnalyzerService — Unit Tests")
class SchemaAnalyzerServiceTest {

    private SchemaAnalyzerService analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SchemaAnalyzerService();
    }

    // ══════════════════════════════════════════════════════════
    // 1. Simple flat format (spec example)
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T01 — Simple flat schema: spec example { name:string, mobile:string, age:integer }")
    void testSimpleFlatSchema() {
        String schema = """
            {
              "name":   "string",
              "mobile": "string",
              "age":    "integer"
            }
            """;

        List<ParsedField> fields = analyzer.parse(schema);

        assertThat(fields).hasSize(3);

        Map<String, ParsedField> byName = index(fields);

        assertField(byName.get("name"),   "name",   "name",   "string",  false, null);
        assertField(byName.get("mobile"), "mobile", "mobile", "string",  false, null);
        assertField(byName.get("age"),    "age",    "age",    "integer", false, null);
    }

    @Test
    @DisplayName("T02 — V1 Customer request schema (spec exact)")
    void testV1RequestSchema() {
        String schema = """
            {"name":"string","mobile":"string","email":"string"}
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields).hasSize(3);
        assertThat(fields).extracting(ParsedField::getFieldType)
            .containsOnly("string");
        assertThat(fields).extracting(ParsedField::getFieldPath)
            .containsExactlyInAnyOrder("name", "mobile", "email");
    }

    @Test
    @DisplayName("T03 — V2 Customer request schema (spec exact)")
    void testV2RequestSchema() {
        String schema = """
            {"fullName":"string","phoneNumber":"string","emailAddress":"string"}
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields).hasSize(3);
        assertThat(fields).extracting(ParsedField::getFieldPath)
            .containsExactlyInAnyOrder("fullName", "phoneNumber", "emailAddress");
    }

    // ══════════════════════════════════════════════════════════
    // 2. JSON Schema format (properties + type)
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T04 — JSON Schema format: simple properties")
    void testJsonSchemaFormat() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "id":    { "type": "integer" },
                "name":  { "type": "string" },
                "score": { "type": "number" },
                "active":{ "type": "boolean" }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields).hasSize(4);

        Map<String, ParsedField> byName = index(fields);
        assertThat(byName.get("id").getFieldType()).isEqualTo("integer");
        assertThat(byName.get("name").getFieldType()).isEqualTo("string");
        assertThat(byName.get("score").getFieldType()).isEqualTo("number");
        assertThat(byName.get("active").getFieldType()).isEqualTo("boolean");
    }

    // ══════════════════════════════════════════════════════════
    // 3. Required vs optional fields
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T05 — Required fields correctly marked")
    void testRequiredFields() {
        String schema = """
            {
              "type": "object",
              "required": ["name", "email"],
              "properties": {
                "name":  { "type": "string" },
                "email": { "type": "string" },
                "phone": { "type": "string" }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byName = index(fields);

        assertThat(byName.get("name").isRequired()).isTrue();
        assertThat(byName.get("email").isRequired()).isTrue();
        assertThat(byName.get("phone").isRequired()).isFalse();
    }

    // ══════════════════════════════════════════════════════════
    // 4. Nested objects
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T06 — Nested object: dot-notation path")
    void testNestedObject() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "address": {
                  "type": "object",
                  "properties": {
                    "street": { "type": "string" },
                    "city":   { "type": "string" },
                    "zip":    { "type": "string" }
                  }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);

        // Should have: name, address, address.street, address.city, address.zip
        assertThat(fields).hasSize(5);

        Map<String, ParsedField> byPath = indexByPath(fields);
        assertThat(byPath).containsKey("name");
        assertThat(byPath).containsKey("address");
        assertThat(byPath).containsKey("address.street");
        assertThat(byPath).containsKey("address.city");
        assertThat(byPath).containsKey("address.zip");

        assertThat(byPath.get("address").getFieldType()).isEqualTo("object");
        assertThat(byPath.get("address.city").getParentPath()).isEqualTo("address");
        assertThat(byPath.get("address.city").getFieldType()).isEqualTo("string");
    }

    @Test
    @DisplayName("T07 — Deeply nested object (3 levels)")
    void testDeeplyNestedObject() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "company": {
                  "type": "object",
                  "properties": {
                    "hq": {
                      "type": "object",
                      "properties": {
                        "country": { "type": "string" }
                      }
                    }
                  }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);

        Map<String, ParsedField> byPath = indexByPath(fields);
        assertThat(byPath).containsKey("company");
        assertThat(byPath).containsKey("company.hq");
        assertThat(byPath).containsKey("company.hq.country");
        assertThat(byPath.get("company.hq.country").getParentPath()).isEqualTo("company.hq");
    }

    // ══════════════════════════════════════════════════════════
    // 5. Arrays
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T08 — Array of primitives: [*] path notation")
    void testArrayOfPrimitives() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "tags": {
                  "type": "array",
                  "items": { "type": "string" }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byPath = indexByPath(fields);

        assertThat(byPath).containsKey("tags");
        assertThat(byPath.get("tags").getFieldType()).isEqualTo("array");
        assertThat(byPath).containsKey("tags[*]");
        assertThat(byPath.get("tags[*]").getFieldType()).isEqualTo("string");
        assertThat(byPath.get("tags[*]").getParentPath()).isEqualTo("tags");
    }

    @Test
    @DisplayName("T09 — Array of objects: [*].field path notation")
    void testArrayOfObjects() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "orders": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["orderId"],
                    "properties": {
                      "orderId": { "type": "integer" },
                      "amount":  { "type": "number" }
                    }
                  }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byPath = indexByPath(fields);

        assertThat(byPath).containsKey("orders");
        assertThat(byPath).containsKey("orders[*]");
        assertThat(byPath).containsKey("orders[*].orderId");
        assertThat(byPath).containsKey("orders[*].amount");
        assertThat(byPath.get("orders[*].orderId").getParentPath()).isEqualTo("orders[*]");
        assertThat(byPath.get("orders[*].orderId").isRequired()).isTrue();
        assertThat(byPath.get("orders[*].amount").isRequired()).isFalse();
    }

    // ══════════════════════════════════════════════════════════
    // 6. Enum values
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T10 — Enum values extracted")
    void testEnumValues() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "status": {
                  "type": "string",
                  "enum": ["ACTIVE", "INACTIVE", "PENDING"]
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        ParsedField status = fields.stream()
                .filter(f -> "status".equals(f.getFieldName()))
                .findFirst()
                .orElseThrow();

        assertThat(status.getEnumValues()).containsExactly("ACTIVE", "INACTIVE", "PENDING");
        assertThat(status.enumValuesAsString()).isEqualTo("ACTIVE,INACTIVE,PENDING");
    }

    // ══════════════════════════════════════════════════════════
    // 7. Field descriptions
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T11 — Description stored on field")
    void testFieldDescription() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Customer full name"
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields.get(0).getDescription()).isEqualTo("Customer full name");
    }

    // ══════════════════════════════════════════════════════════
    // 8. Data type variations
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T12 — Unknown type → 'unknown'")
    void testUnknownType() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "mystery": { "type": "binary" }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields.get(0).getFieldType()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("T13 — Type array [string, null] → first non-null type")
    void testNullableType() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "nickname": { "type": ["string", "null"] }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        assertThat(fields.get(0).getFieldType()).isEqualTo("string");
    }

    @Test
    @DisplayName("T14 — No type but has properties → inferred as 'object'")
    void testInferObjectType() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "meta": {
                  "properties": {
                    "version": { "type": "string" }
                  }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byPath = indexByPath(fields);
        assertThat(byPath.get("meta").getFieldType()).isEqualTo("object");
    }

    // ══════════════════════════════════════════════════════════
    // 9. Invalid / edge-case schema handling
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T15 — Null schema → empty field list")
    void testNullSchema() {
        List<ParsedField> fields = analyzer.parse(null);
        assertThat(fields).isEmpty();
    }

    @Test
    @DisplayName("T16 — Blank schema → empty field list")
    void testBlankSchema() {
        assertThat(analyzer.parse("")).isEmpty();
        assertThat(analyzer.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("T17 — Invalid JSON → SchemaParseException")
    void testInvalidJson() {
        assertThatThrownBy(() -> analyzer.parse("{ not valid json }"))
            .isInstanceOf(SchemaParseException.class)
            .hasMessageContaining("Invalid JSON");
    }

    @Test
    @DisplayName("T18 — JSON array root → SchemaParseException")
    void testArrayRoot() {
        assertThatThrownBy(() -> analyzer.parse("[\"a\", \"b\"]"))
            .isInstanceOf(SchemaParseException.class)
            .hasMessageContaining("must be a JSON object");
    }

    @Test
    @DisplayName("T19 — Empty object schema → empty field list")
    void testEmptyObjectSchema() {
        List<ParsedField> fields = analyzer.parse("{}");
        assertThat(fields).isEmpty();
    }

    // ══════════════════════════════════════════════════════════
    // 10. Field path accuracy
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("T20 — Full spec example: V1 response schema path/type check")
    void testV1ResponseSchemaPaths() {
        String schema = """
            {"id":"integer","name":"string","mobile":"string","email":"string"}
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byPath = indexByPath(fields);

        assertThat(byPath.get("id").getFieldType()).isEqualTo("integer");
        assertThat(byPath.get("name").getFieldType()).isEqualTo("string");
        assertThat(byPath.get("mobile").getFieldPath()).isEqualTo("mobile");
        assertThat(byPath.get("email").getFieldPath()).isEqualTo("email");
        fields.forEach(f -> assertThat(f.getParentPath()).isNull());
    }

    @Test
    @DisplayName("T21 — Required in nested object")
    void testRequiredInNestedObject() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "billing": {
                  "type": "object",
                  "required": ["amount"],
                  "properties": {
                    "amount":   { "type": "number" },
                    "currency": { "type": "string" }
                  }
                }
              }
            }
            """;
        List<ParsedField> fields = analyzer.parse(schema);
        Map<String, ParsedField> byPath = indexByPath(fields);

        assertThat(byPath.get("billing.amount").isRequired()).isTrue();
        assertThat(byPath.get("billing.currency").isRequired()).isFalse();
    }

    // ── Test helpers ─────────────────────────────────────────

    private Map<String, ParsedField> index(List<ParsedField> fields) {
        return fields.stream().collect(Collectors.toMap(ParsedField::getFieldName, f -> f));
    }

    private Map<String, ParsedField> indexByPath(List<ParsedField> fields) {
        return fields.stream().collect(Collectors.toMap(ParsedField::getFieldPath, f -> f));
    }

    private void assertField(ParsedField field, String name, String path, String type,
                             boolean required, String parentPath) {
        assertThat(field).isNotNull();
        assertThat(field.getFieldName()).isEqualTo(name);
        assertThat(field.getFieldPath()).isEqualTo(path);
        assertThat(field.getFieldType()).isEqualTo(type);
        assertThat(field.isRequired()).isEqualTo(required);
        assertThat(field.getParentPath()).isEqualTo(parentPath);
    }
}
