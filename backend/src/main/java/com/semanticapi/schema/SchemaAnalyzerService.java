package com.semanticapi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Core schema parsing service for Phase 4.
 *
 * <p>Accepts a raw JSON schema string and recursively extracts all fields
 * into a flat list of {@link ParsedField} objects.
 *
 * <h2>Supported schema inputs</h2>
 * <p>This service understands two schema formats:
 *
 * <h3>1. JSON Schema (draft-07 style)</h3>
 * <pre>{@code
 * {
 *   "type": "object",
 *   "required": ["name"],
 *   "properties": {
 *     "name":    { "type": "string",  "description": "Full name" },
 *     "age":     { "type": "integer" },
 *     "address": {
 *       "type": "object",
 *       "properties": {
 *         "city":   { "type": "string" },
 *         "zip":    { "type": "string" }
 *       }
 *     },
 *     "tags": {
 *       "type": "array",
 *       "items": { "type": "string" }
 *     },
 *     "status": { "type": "string", "enum": ["ACTIVE", "INACTIVE"] }
 *   }
 * }
 * }</pre>
 *
 * <h3>2. Simple flat object (spec example format)</h3>
 * <pre>{@code
 * { "name": "string", "mobile": "string", "age": "integer" }
 * }</pre>
 *
 * <p>The simple format is auto-detected: if the object has no "type" or
 * "properties" keys but its values are strings that look like type names,
 * they are treated as type annotations.
 *
 * <h2>Extraction rules</h2>
 * <ul>
 *   <li>Flat fields → path = field name</li>
 *   <li>Nested objects → parent object included + child path = parent.child</li>
 *   <li>Arrays → array field included + items extracted as path[*] or path[*].child</li>
 *   <li>Required list → marks matching fields as required=true</li>
 *   <li>Enum values → stored on the field</li>
 *   <li>Description → stored if present</li>
 *   <li>Unknown/missing type → stored as "unknown"</li>
 * </ul>
 */
@Service
public class SchemaAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(SchemaAnalyzerService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // JSON Schema primitive types (anything else will be "unknown")
    private static final java.util.Set<String> KNOWN_TYPES = java.util.Set.of(
        "string", "integer", "number", "boolean", "object", "array", "null"
    );

    // ── Public API ────────────────────────────────────────────

    /**
     * Parse a JSON schema string and return a flat list of extracted fields.
     *
     * @param schemaJson raw JSON string (JSON Schema or simple flat object)
     * @return list of {@link ParsedField}; never null, empty if schema is blank or invalid
     * @throws SchemaParseException if the JSON is syntactically invalid
     */
    public List<ParsedField> parse(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            log.debug("Schema is blank — returning empty field list");
            return List.of();
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            throw new SchemaParseException("Invalid JSON: " + e.getMessage(), e);
        }

        if (!root.isObject()) {
            throw new SchemaParseException("Schema must be a JSON object, got: " + root.getNodeType());
        }

        List<ParsedField> fields = new ArrayList<>();
        parseNode(root, "", null, fields, extractRequiredSet(root));
        log.debug("Parsed {} fields from schema", fields.size());
        return fields;
    }

    // ── Private parsing helpers ───────────────────────────────

    /**
     * Recursively parse a schema node and append all extracted fields to {@code fields}.
     *
     * @param node       current JSON schema node
     * @param pathPrefix dot-notation path of the parent (empty for root)
     * @param parentPath parent path for the current node's children (null for root fields)
     * @param fields     accumulator
     * @param requiredAt set of field names marked "required" in the current node's parent
     */
    private void parseNode(JsonNode node,
                           String pathPrefix,
                           String parentPath,
                           List<ParsedField> fields,
                           java.util.Set<String> requiredAt) {

        // ── JSON Schema format: has "properties" ──────────────
        if (node.has("properties")) {
            JsonNode properties = node.get("properties");
            java.util.Set<String> required = extractRequiredSet(node);

            Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String     fieldName = entry.getKey();
                JsonNode   fieldNode = entry.getValue();
                String     path      = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
                String     type      = resolveType(fieldNode);
                boolean    isReq     = required.contains(fieldName);

                ParsedField.Builder b = ParsedField.builder(fieldName, path)
                    .type(type)
                    .required(isReq)
                    .parent(parentPath)
                    .description(extractDescription(fieldNode));

                // Enum values
                if (fieldNode.has("enum")) {
                    b.enumValues(extractEnumValues(fieldNode.get("enum")));
                }

                fields.add(b.build());

                // Recurse into nested objects
                if ("object".equals(type) && fieldNode.has("properties")) {
                    parseNode(fieldNode, path, path, fields, extractRequiredSet(fieldNode));
                }

                // Recurse into arrays
                if ("array".equals(type) && fieldNode.has("items")) {
                    parseArrayItems(fieldNode.get("items"), path, fields);
                }
            }
            return;
        }

        // ── Simple flat object: { "fieldName": "typeName" } ──
        // Auto-detected when the object looks like a type map
        if (isSimpleTypeMap(node)) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String   fieldName = entry.getKey();
                String   path      = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
                String   type      = normalizeType(entry.getValue().asText("unknown"));
                boolean  isReq     = requiredAt.contains(fieldName);

                fields.add(ParsedField.builder(fieldName, path)
                    .type(type)
                    .required(isReq)
                    .parent(parentPath)
                    .build());
            }
            return;
        }

        // ── Unknown format: skip with a warning ───────────────
        log.warn("Unrecognized schema node format at path '{}' — skipping", pathPrefix);
    }

    /**
     * Parse the items of an array field and add them to the field list.
     *
     * @param itemsNode the "items" node of the array field
     * @param arrayPath the dot-notation path of the array field itself
     * @param fields    accumulator
     */
    private void parseArrayItems(JsonNode itemsNode, String arrayPath, List<ParsedField> fields) {
        String itemPath = arrayPath + "[*]";

        if (itemsNode.isObject()) {
            String itemType = resolveType(itemsNode);

            if ("object".equals(itemType) && itemsNode.has("properties")) {
                // Array of objects — add a representative item field and recurse
                fields.add(ParsedField.builder(arrayPath + "[*]", itemPath)
                    .type("object")
                    .parent(arrayPath)
                    .build());
                parseNode(itemsNode, itemPath, itemPath, fields, extractRequiredSet(itemsNode));
            } else {
                // Array of primitives
                fields.add(ParsedField.builder(arrayPath + "[*]", itemPath)
                    .type(itemType)
                    .parent(arrayPath)
                    .description(extractDescription(itemsNode))
                    .build());
            }
        }
    }

    // ── Detection helpers ─────────────────────────────────────

    /**
     * Returns true if this JSON object looks like a simple type map:
     * {@code { "fieldName": "typeName", ... }} where all values are strings
     * that represent type names.
     *
     * <p>Used to distinguish the spec's simple format from full JSON Schema.
     */
    private boolean isSimpleTypeMap(JsonNode node) {
        if (!node.isObject() || node.isEmpty()) return false;
        // Must not have JSON Schema keywords that would indicate another format
        if (node.has("type") || node.has("properties") || node.has("$schema")) return false;

        // All values must be string nodes
        Iterator<JsonNode> values = node.elements();
        while (values.hasNext()) {
            if (!values.next().isTextual()) return false;
        }
        return true;
    }

    // ── Type resolution ───────────────────────────────────────

    /**
     * Resolve the JSON Schema "type" field from a property node.
     * Falls back to "unknown" if absent or unrecognized.
     */
    private String resolveType(JsonNode node) {
        if (node.has("type")) {
            JsonNode typeNode = node.get("type");
            if (typeNode.isTextual()) {
                return normalizeType(typeNode.asText());
            }
            // type can be an array: ["string", "null"] — take first non-null
            if (typeNode.isArray()) {
                for (JsonNode t : typeNode) {
                    String v = normalizeType(t.asText(""));
                    if (!"null".equals(v)) return v;
                }
            }
        }
        // Infer from presence of "properties" or "items"
        if (node.has("properties")) return "object";
        if (node.has("items"))      return "array";
        return "unknown";
    }

    private String normalizeType(String raw) {
        if (raw == null) return "unknown";
        String lower = raw.trim().toLowerCase();
        return KNOWN_TYPES.contains(lower) ? lower : "unknown";
    }

    // ── Utility ───────────────────────────────────────────────

    /** Extract the "required" array from a schema node as a Set of field names. */
    private java.util.Set<String> extractRequiredSet(JsonNode node) {
        if (!node.has("required")) return java.util.Set.of();
        JsonNode req = node.get("required");
        if (!req.isArray()) return java.util.Set.of();
        java.util.Set<String> result = new java.util.HashSet<>();
        req.forEach(n -> result.add(n.asText()));
        return result;
    }

    /** Extract enum values as a list of strings. */
    private List<String> extractEnumValues(JsonNode enumNode) {
        List<String> values = new ArrayList<>();
        enumNode.forEach(n -> values.add(n.asText()));
        return values;
    }

    /** Extract the "description" field from a schema node, or null. */
    private String extractDescription(JsonNode node) {
        return node.has("description") ? node.get("description").asText() : null;
    }
}
