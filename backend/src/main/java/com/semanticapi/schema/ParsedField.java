package com.semanticapi.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable in-memory representation of a single extracted schema field.
 *
 * <p>This is the output of {@link SchemaAnalyzerService} — a pure data
 * class that holds all the information extracted from a single JSON schema
 * field, before it is persisted as a {@link com.semanticapi.model.SchemaField}.
 *
 * <p>Supports the full spec requirements:
 * <ul>
 *   <li>Field name and dot-notation path</li>
 *   <li>Data type (string, integer, number, boolean, object, array, null, unknown)</li>
 *   <li>Required / optional status</li>
 *   <li>Parent path (null for root-level fields)</li>
 *   <li>Enum values (when the schema defines them)</li>
 *   <li>Nested objects and arrays represented via child fields</li>
 * </ul>
 *
 * <p>Example for the spec's flat schema:
 * <pre>{@code
 *   ParsedField{name="name",   path="name",   type="string",  required=true,  parent=null}
 *   ParsedField{name="mobile", path="mobile", type="string",  required=false, parent=null}
 *   ParsedField{name="age",    path="age",    type="integer", required=false, parent=null}
 * }</pre>
 *
 * <p>Example for a nested schema:
 * <pre>{@code
 *   ParsedField{name="address",     path="address",      type="object", ...}
 *   ParsedField{name="street",      path="address.street", type="string", parent="address"}
 *   ParsedField{name="city",        path="address.city",   type="string", parent="address"}
 * }</pre>
 *
 * <p>Example for an array:
 * <pre>{@code
 *   ParsedField{name="tags", path="tags", type="array", ...}
 *   ParsedField{name="tags[*]", path="tags[*]", type="string", parent="tags"}
 * }</pre>
 */
public class ParsedField {

    private final String       fieldName;
    private final String       fieldPath;
    private final String       fieldType;
    private final boolean      required;
    private final String       parentPath;
    private final List<String> enumValues;
    private final String       description;

    private ParsedField(Builder b) {
        this.fieldName   = b.fieldName;
        this.fieldPath   = b.fieldPath;
        this.fieldType   = b.fieldType;
        this.required    = b.required;
        this.parentPath  = b.parentPath;
        this.enumValues  = List.copyOf(b.enumValues);
        this.description = b.description;
    }

    // ── Accessors ─────────────────────────────────────────────

    public String       getFieldName()   { return fieldName; }
    public String       getFieldPath()   { return fieldPath; }
    public String       getFieldType()   { return fieldType; }
    public boolean      isRequired()     { return required; }
    public String       getParentPath()  { return parentPath; }
    public List<String> getEnumValues()  { return enumValues; }
    public String       getDescription() { return description; }

    /**
     * Convenience: format enum values as comma-separated string for storage.
     * Returns null if no enum values.
     */
    public String enumValuesAsString() {
        return enumValues.isEmpty() ? null : String.join(",", enumValues);
    }

    @Override
    public String toString() {
        return "ParsedField{" +
               "name='" + fieldName + '\'' +
               ", path='" + fieldPath + '\'' +
               ", type='" + fieldType + '\'' +
               ", required=" + required +
               (parentPath != null ? ", parent='" + parentPath + '\'' : "") +
               (!enumValues.isEmpty() ? ", enum=" + enumValues : "") +
               '}';
    }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder(String fieldName, String fieldPath) {
        return new Builder(fieldName, fieldPath);
    }

    public static final class Builder {
        private final String fieldName;
        private final String fieldPath;
        private String       fieldType   = "unknown";
        private boolean      required    = false;
        private String       parentPath  = null;
        private List<String> enumValues  = new ArrayList<>();
        private String       description = null;

        private Builder(String fieldName, String fieldPath) {
            this.fieldName = fieldName;
            this.fieldPath = fieldPath;
        }

        public Builder type(String type)            { this.fieldType = type;    return this; }
        public Builder required(boolean r)          { this.required = r;        return this; }
        public Builder parent(String parentPath)    { this.parentPath = parentPath; return this; }
        public Builder enumValues(List<String> evs) { this.enumValues = evs;   return this; }
        public Builder description(String desc)     { this.description = desc;  return this; }
        public ParsedField build()                  { return new ParsedField(this); }
    }
}
