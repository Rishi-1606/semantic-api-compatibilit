package com.semanticapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semanticapi.dto.TransformTestRequest;
import com.semanticapi.dto.TransformTestResponse;
import com.semanticapi.exception.ResourceNotFoundException;
import com.semanticapi.model.TransformationRule;
import com.semanticapi.repository.TransformationRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Phase 12 — Transformation Tester (preview facility).
 *
 * <p>Applies a single transformation rule's operation to a sample JSON object
 * in-process, without calling any external API.
 *
 * <p><strong>This is NOT a second runtime engine.</strong>
 * The Phase 10 Node.js {@code TransformationExecutor} remains the authoritative
 * runtime transformation engine. This service is a safe read-only preview tool
 * for developers to verify rule behaviour before approving rules for production use.
 *
 * <p>Supported operations (simple single-field preview):
 * <ul>
 *   <li>RENAME — renames sourcePath key to targetPath</li>
 *   <li>MOVE   — moves flat sourcePath to nested targetPath</li>
 *   <li>FLATTEN — moves nested sourcePath to flat targetPath</li>
 *   <li>TYPE_CONVERSION — converts value type (string↔integer↔number↔boolean)</li>
 *   <li>ENUM_MAPPING — maps a known enum value using stored parameters</li>
 * </ul>
 * COMBINE and SPLIT require multiple source/target paths and cannot be previewed
 * from a single field; the response explains this limitation.
 */
@Service
public class TransformTestService {

    private static final Logger log = LoggerFactory.getLogger(TransformTestService.class);

    private final TransformationRuleRepository ruleRepository;
    private final ObjectMapper                 objectMapper;

    public TransformTestService(TransformationRuleRepository ruleRepository,
                                ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.objectMapper   = objectMapper;
    }

    /**
     * Apply one rule's transformation to sample JSON.
     *
     * @param request contains ruleId and raw JSON string
     * @return preview result (success or failure with explanation)
     * @throws ResourceNotFoundException if ruleId does not exist
     * @throws IllegalArgumentException  if inputJson is not valid JSON
     */
    public TransformTestResponse test(TransformTestRequest request) {
        // 1. Load rule
        TransformationRule rule = ruleRepository.findById(request.getRuleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TransformationRule", request.getRuleId()));

        String operation  = rule.getOperation().name();
        String sourcePath = rule.getSourcePath();
        String targetPath = rule.getTargetPath();

        // 2. Parse input JSON
        JsonNode input;
        try {
            input = objectMapper.readTree(request.getInputJson());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("inputJson is not valid JSON: " + e.getOriginalMessage());
        }

        if (!input.isObject()) {
            throw new IllegalArgumentException("inputJson must be a JSON object (not an array or scalar)");
        }

        ObjectNode doc = ((ObjectNode) input).deepCopy();

        try {
            switch (rule.getOperation()) {
                case RENAME -> applyRename(doc, sourcePath, targetPath);
                case MOVE   -> applyMove(doc, sourcePath, targetPath);
                case FLATTEN -> applyFlatten(doc, sourcePath, targetPath);
                case TYPE_CONVERSION -> applyTypeConversion(doc, sourcePath, targetPath, rule);
                case ENUM_MAPPING   -> applyEnumMapping(doc, sourcePath, targetPath, rule);
                case COMBINE, SPLIT ->
                        throw new UnsupportedOperationException(
                                operation + " requires multiple source/target paths " +
                                "and cannot be previewed as a single-field test. " +
                                "Use the Phase 10 gateway for full end-to-end testing.");
                default ->
                        throw new UnsupportedOperationException(
                                "Preview not supported for operation: " + operation);
            }

            String outputJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(doc);

            return TransformTestResponse.success(
                    rule.getId(), operation, sourcePath, targetPath,
                    request.getInputJson(), outputJson);

        } catch (UnsupportedOperationException e) {
            return TransformTestResponse.failure(
                    rule.getId(), operation, sourcePath, targetPath,
                    request.getInputJson(), e.getMessage());
        } catch (Exception e) {
            log.warn("Transform test failed for ruleId={}: {}", rule.getId(), e.getMessage());
            return TransformTestResponse.failure(
                    rule.getId(), operation, sourcePath, targetPath,
                    request.getInputJson(), "Transformation error: " + e.getMessage());
        }
    }

    // ── Operation implementations ─────────────────────────────────────────────

    /** RENAME: rename top-level sourcePath key to targetPath key. */
    private void applyRename(ObjectNode doc, String sourcePath, String targetPath) {
        if (!doc.has(sourcePath)) return; // field absent — no error, just no-op
        JsonNode value = doc.get(sourcePath);
        doc.remove(sourcePath);
        setNestedValue(doc, targetPath, value);
    }

    /** MOVE: take flat sourcePath and set it at nested targetPath (dot-notation). */
    private void applyMove(ObjectNode doc, String sourcePath, String targetPath) {
        if (!doc.has(sourcePath)) return;
        JsonNode value = doc.get(sourcePath);
        doc.remove(sourcePath);
        setNestedValue(doc, targetPath, value);
    }

    /** FLATTEN: read nested sourcePath (dot-notation) and set it as flat targetPath. */
    private void applyFlatten(ObjectNode doc, String sourcePath, String targetPath) {
        JsonNode value = getNestedValue(doc, sourcePath);
        if (value == null) return;
        removeNestedValue(doc, sourcePath);
        doc.set(targetPath, value);
    }

    /** TYPE_CONVERSION: convert value at sourcePath to the target type. */
    private void applyTypeConversion(ObjectNode doc, String sourcePath, String targetPath,
                                     TransformationRule rule) {
        JsonNode value = getNestedValue(doc, sourcePath);
        if (value == null) return;
        String targetType = rule.getTargetType() != null ? rule.getTargetType().toLowerCase() : "string";
        JsonNode converted = convertValue(value, targetType);
        removeNestedValue(doc, sourcePath);
        setNestedValue(doc, targetPath, converted);
    }

    /** ENUM_MAPPING: map a known enum value using stored parameters JSON. */
    private void applyEnumMapping(ObjectNode doc, String sourcePath, String targetPath,
                                  TransformationRule rule) throws Exception {
        JsonNode value = getNestedValue(doc, sourcePath);
        if (value == null || !value.isTextual()) return;

        String raw = value.asText();
        if (rule.getParameters() != null) {
            JsonNode params = objectMapper.readTree(rule.getParameters());
            JsonNode mappings = params.path("mappings");
            if (!mappings.isMissingNode() && mappings.has(raw)) {
                raw = mappings.get(raw).asText();
            }
        }
        removeNestedValue(doc, sourcePath);
        setNestedValue(doc, targetPath, objectMapper.getNodeFactory().textNode(raw));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Read a value from doc using dot-notation path. Returns null if absent. */
    private JsonNode getNestedValue(ObjectNode root, String path) {
        String[] parts = path.split("\\.", -1);
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        return current;
    }

    /** Set a value in doc at dot-notation path, creating intermediate objects. */
    private void setNestedValue(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.", -1);
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.has(parts[i]) || !current.get(parts[i]).isObject()) {
                current.set(parts[i], objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(parts[i]);
        }
        current.set(parts[parts.length - 1], value);
    }

    /** Remove a value at dot-notation path from doc. */
    private void removeNestedValue(ObjectNode root, String path) {
        String[] parts = path.split("\\.", -1);
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode next = current.get(parts[i]);
            if (next == null || !next.isObject()) return;
            current = (ObjectNode) next;
        }
        current.remove(parts[parts.length - 1]);
    }

    /** Convert a JsonNode value to the specified target type string. */
    private JsonNode convertValue(JsonNode value, String targetType) {
        var factory = objectMapper.getNodeFactory();
        return switch (targetType) {
            case "string"  -> factory.textNode(value.asText());
            case "integer" -> factory.numberNode(value.asLong());
            case "number"  -> factory.numberNode(value.asDouble());
            case "boolean" -> factory.booleanNode(value.asBoolean());
            default        -> value; // unknown type — preserve as-is
        };
    }
}
