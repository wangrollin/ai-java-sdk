package io.wangrollin.ai;

import java.util.Objects;

/**
 * Tool call requested by the model.
 *
 * @param id optional provider tool-call identifier
 * @param name function name requested by the model
 * @param argumentsJson raw JSON argument object or fragment supplied by the provider
 */
public record ChatToolCall(String id, String name, String argumentsJson) {
    /**
     * Creates a tool call after validating the required function name.
     *
     * @param id optional provider tool-call identifier
     * @param name function name requested by the model
     * @param argumentsJson raw JSON argument object or fragment
     */
    public ChatToolCall {
        id = normalizeOptionalText(id);
        name = requireText(name, "name");
        argumentsJson = argumentsJson == null ? "" : argumentsJson;
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
}
