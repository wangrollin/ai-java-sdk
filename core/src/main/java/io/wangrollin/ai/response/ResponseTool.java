package io.wangrollin.ai.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Function tool definition that can be advertised to Responses API providers.
 *
 * @param name function name the model should request when calling the tool
 * @param description optional human-readable description for tool selection
 * @param parametersJson JSON schema object describing the function arguments
 * @param strict optional strict-schema hint; {@code null} leaves provider defaults unchanged
 */
public record ResponseTool(String name, String description, String parametersJson, Boolean strict) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates a function tool after validating the JSON schema shape.
     *
     * @param name function name
     * @param description optional description
     * @param parametersJson JSON schema object for arguments
     * @param strict optional strict-schema hint
     */
    public ResponseTool {
        name = requireText(name, "name");
        description = normalizeOptionalText(description);
        parametersJson = requireJsonObject(parametersJson, "parametersJson");
    }

    /**
     * Creates a function tool with no description and provider-default strictness.
     *
     * @param name function name
     * @param parametersJson JSON schema object for arguments
     * @return tool definition
     */
    public static ResponseTool function(String name, String parametersJson) {
        return new ResponseTool(name, null, parametersJson, null);
    }

    /**
     * Creates a function tool with a description and provider-default strictness.
     *
     * @param name function name
     * @param description tool description
     * @param parametersJson JSON schema object for arguments
     * @return tool definition
     */
    public static ResponseTool function(String name, String description, String parametersJson) {
        return new ResponseTool(name, description, parametersJson, null);
    }

    /**
     * Creates a function tool with an explicit strict-schema hint.
     *
     * @param name function name
     * @param description tool description
     * @param parametersJson JSON schema object for arguments
     * @param strict strict-schema hint to send to the provider
     * @return tool definition
     */
    public static ResponseTool function(String name, String description, String parametersJson, boolean strict) {
        return new ResponseTool(name, description, parametersJson, strict);
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

    private static String requireJsonObject(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        try {
            JsonNode node = OBJECT_MAPPER.readTree(value);
            if (!node.isObject()) {
                throw new IllegalArgumentException(name + " must be a JSON object");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(name + " must be valid JSON", e);
        }
    }
}
