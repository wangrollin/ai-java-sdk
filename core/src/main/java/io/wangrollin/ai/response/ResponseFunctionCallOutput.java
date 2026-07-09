package io.wangrollin.ai.response;

import java.util.Objects;

/**
 * Tool result item returned to a Responses API model after the application
 * executes a requested function call.
 *
 * @param callId provider call id from {@link ResponseToolCall#callId()}
 * @param output application-provided tool result text
 */
public record ResponseFunctionCallOutput(String callId, String output) implements ResponseInputItem {
    /**
     * Creates a function-call output after validating required provider fields.
     *
     * @param callId provider call id from the prior response
     * @param output tool result text to send back to the model
     */
    public ResponseFunctionCallOutput {
        callId = requireText(callId, "callId");
        output = requireText(output, "output");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
