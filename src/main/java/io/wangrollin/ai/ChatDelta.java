package io.wangrollin.ai;

/**
 * Incremental update read from a streaming chat response.
 *
 * @param text newly emitted assistant text, or an empty string for metadata-only events
 * @param finishReason optional provider finish reason
 */
public record ChatDelta(String text, String finishReason) {
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
    }
}
