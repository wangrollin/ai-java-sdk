package io.wangrollin.ai.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/**
 * Optional text-format hint for providers that support structured Responses API output.
 *
 * <p>The SDK keeps JSON schemas as validated text so the public API remains
 * small while the OpenAI-compatible adapter owns the provider-specific
 * {@code text.format} wire shape.
 */
public record ResponseTextFormat(
        String type,
        String jsonSchemaName,
        String jsonSchemaDescription,
        String jsonSchema,
        boolean strict) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEXT = "text";
    private static final String JSON_OBJECT = "json_object";
    private static final String JSON_SCHEMA = "json_schema";

    /**
     * Requests normal text output explicitly.
     *
     * @return text response format
     */
    public static ResponseTextFormat text() {
        return new ResponseTextFormat(TEXT, null, null, null, false);
    }

    /**
     * Requests a JSON object response-format hint.
     *
     * @return JSON object response format
     */
    public static ResponseTextFormat jsonObject() {
        return new ResponseTextFormat(JSON_OBJECT, null, null, null, false);
    }

    /**
     * Requests output that matches a JSON Schema.
     *
     * @param name schema name sent to the provider
     * @param schemaJson JSON Schema document as a JSON object string
     * @return strict JSON schema response format
     */
    public static ResponseTextFormat jsonSchema(String name, String schemaJson) {
        return jsonSchema(name, null, schemaJson, true);
    }

    /**
     * Requests output that matches a JSON Schema.
     *
     * @param name schema name sent to the provider
     * @param schemaJson JSON Schema document as a JSON object string
     * @param strict whether the provider should enforce the schema strictly
     * @return JSON schema response format
     */
    public static ResponseTextFormat jsonSchema(String name, String schemaJson, boolean strict) {
        return jsonSchema(name, null, schemaJson, strict);
    }

    /**
     * Requests output that matches a JSON Schema with a provider-facing description.
     *
     * @param name schema name sent to the provider
     * @param description optional schema description; blank values are treated as absent
     * @param schemaJson JSON Schema document as a JSON object string
     * @param strict whether the provider should enforce the schema strictly
     * @return JSON schema response format
     */
    public static ResponseTextFormat jsonSchema(
            String name,
            String description,
            String schemaJson,
            boolean strict) {
        return new ResponseTextFormat(
                JSON_SCHEMA,
                requireText(name, "name"),
                normalizeOptionalText(description),
                requireJsonObject(schemaJson),
                strict);
    }

    /**
     * Validates response-format invariants.
     *
     * @param type provider response format type
     * @param jsonSchemaName optional JSON schema name
     * @param jsonSchemaDescription optional JSON schema description
     * @param jsonSchema optional JSON schema document
     * @param strict strict schema enforcement flag
     */
    public ResponseTextFormat {
        type = requireText(type, "type");
        if (TEXT.equals(type) || JSON_OBJECT.equals(type)) {
            jsonSchemaName = null;
            jsonSchemaDescription = null;
            jsonSchema = null;
            strict = false;
        } else if (JSON_SCHEMA.equals(type)) {
            jsonSchemaName = requireText(jsonSchemaName, "jsonSchemaName");
            jsonSchemaDescription = normalizeOptionalText(jsonSchemaDescription);
            jsonSchema = requireJsonObject(jsonSchema);
        } else {
            throw new IllegalArgumentException("type must be text, json_object, or json_schema");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String requireJsonObject(String value) {
        String json = requireText(value, "schemaJson");
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isObject()) {
                throw new IllegalArgumentException("schemaJson must be a JSON object");
            }
            return json;
        } catch (IOException e) {
            throw new IllegalArgumentException("schemaJson must be valid JSON", e);
        }
    }
}
