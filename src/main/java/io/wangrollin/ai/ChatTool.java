package io.wangrollin.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Function tool definition that can be advertised to chat-capable providers.
 *
 * @param name function name the model should use when requesting the tool
 * @param description optional human-readable description for tool selection
 * @param parametersJson JSON schema object describing the function arguments
 */
public record ChatTool(String name, String description, String parametersJson) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates a function tool after validating the JSON schema shape.
     *
     * @param name function name
     * @param description optional description
     * @param parametersJson JSON schema object for arguments
     */
    public ChatTool {
        name = requireText(name, "name");
        description = normalizeOptionalText(description);
        parametersJson = requireJsonObject(parametersJson, "parametersJson");
    }

    /**
     * Creates a function tool with no description.
     *
     * @param name function name
     * @param parametersJson JSON schema object for arguments
     * @return tool definition
     */
    public static ChatTool function(String name, String parametersJson) {
        return new ChatTool(name, null, parametersJson);
    }

    /**
     * Creates a function tool with a description.
     *
     * @param name function name
     * @param description tool description
     * @param parametersJson JSON schema object for arguments
     * @return tool definition
     */
    public static ChatTool function(String name, String description, String parametersJson) {
        return new ChatTool(name, description, parametersJson);
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
