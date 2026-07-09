package io.wangrollin.ai.response;

import java.util.List;

/**
 * Incremental update read from a streaming Responses API response.
 *
 * @param text newly emitted output text
 * @param done whether the provider signaled completion
 * @param toolCalls function tool calls emitted by this stream event
 */
public record ResponseDelta(String text, boolean done, List<ResponseToolCall> toolCalls) {
    /**
     * Creates a text-only streaming delta.
     *
     * @param text newly emitted output text
     * @param done whether the provider signaled completion
     */
    public ResponseDelta(String text, boolean done) {
        this(text, done, List.of());
    }

    public ResponseDelta {
        if (text == null) {
            text = "";
        }
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
