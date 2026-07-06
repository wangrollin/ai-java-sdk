package io.wangrollin.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

/**
 * Optional response-format hint for providers that support structured chat output.
 *
 * <p>The public type intentionally keeps schemas as JSON text instead of exposing
 * provider-specific request maps. The OpenAI-compatible adapter owns the final
 * wire shape while callers still get early validation that the supplied schema is
 * valid JSON.
 */
public record ChatResponseFormat(String type, String jsonSchemaName, String jsonSchema, boolean strict) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String JSON_OBJECT = "json_object";
    private static final String JSON_SCHEMA = "json_schema";

    /**
     * Creates an OpenAI-compatible JSON object response-format hint.
     *
     * @return JSON object response format
     */
    public static ChatResponseFormat jsonObject() {
        return new ChatResponseFormat(JSON_OBJECT, null, null, false);
    }

    /**
     * Creates an OpenAI-compatible JSON schema response-format hint.
     *
     * @param name schema name sent to the provider
     * @param schemaJson JSON Schema document as a JSON object string
     * @return strict JSON schema response format
     */
    public static ChatResponseFormat jsonSchema(String name, String schemaJson) {
        return jsonSchema(name, schemaJson, true);
    }

    /**
     * Creates an OpenAI-compatible JSON schema response-format hint.
     *
     * @param name schema name sent to the provider
     * @param schemaJson JSON Schema document as a JSON object string
     * @param strict whether the provider should enforce the schema strictly
     * @return JSON schema response format
     */
    public static ChatResponseFormat jsonSchema(String name, String schemaJson, boolean strict) {
        return new ChatResponseFormat(JSON_SCHEMA, requireText(name, "name"), requireJsonObject(schemaJson), strict);
    }

    /**
     * Validates response-format invariants.
     *
     * @param type provider-neutral response format type
     * @param jsonSchemaName optional JSON schema name
     * @param jsonSchema optional JSON schema document
     * @param strict strict schema enforcement flag
     */
    public ChatResponseFormat {
        type = requireText(type, "type");
        if (JSON_OBJECT.equals(type)) {
            jsonSchemaName = null;
            jsonSchema = null;
            strict = false;
        } else if (JSON_SCHEMA.equals(type)) {
            jsonSchemaName = requireText(jsonSchemaName, "jsonSchemaName");
            jsonSchema = requireJsonObject(jsonSchema);
        } else {
            throw new IllegalArgumentException("type must be json_object or json_schema");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
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
