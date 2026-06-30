package io.wangrollin.ai;

import java.util.List;

/**
 * Incremental update read from a streaming chat response.
 *
 * @param text newly emitted assistant text, or an empty string for metadata-only events
 * @param finishReason optional provider finish reason
 * @param toolCalls tool-call fragments emitted by this stream event
 */
public record ChatDelta(String text, String finishReason, List<ChatToolCall> toolCalls) {
    /**
     * Creates a streaming delta and normalizes missing text to an empty string.
     *
     * @param text newly emitted assistant text
     * @param finishReason optional provider finish reason
     */
    public ChatDelta {
        if (text == null) {
            text = "";
        }
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * Creates a text-only streaming delta.
     *
     * @param text newly emitted assistant text
     * @param finishReason optional provider finish reason
     */
    public ChatDelta(String text, String finishReason) {
        this(text, finishReason, List.of());
    }
}
