package io.wangrollin.ai.response;

import java.util.Objects;

/**
 * Function tool call requested by a Responses API model.
 *
 * @param id optional provider item identifier
 * @param callId provider call id used when sending the function result back
 * @param name function name requested by the model
 * @param argumentsJson raw JSON argument object or fragment supplied by the provider
 */
public record ResponseToolCall(String id, String callId, String name, String argumentsJson) {
    /**
     * Creates a tool call after validating the provider call id and function name.
     *
     * @param id optional provider item identifier
     * @param callId provider call id used for the follow-up function output
     * @param name function name requested by the model
     * @param argumentsJson raw JSON argument object or fragment
     */
    public ResponseToolCall {
        id = normalizeOptionalText(id);
        callId = requireText(callId, "callId");
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
