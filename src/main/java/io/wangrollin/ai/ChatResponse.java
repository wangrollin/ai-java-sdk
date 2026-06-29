package io.wangrollin.ai;

import java.util.Objects;

/**
 * Complete response returned by a synchronous chat call.
 *
 * @param text assistant text extracted from the first provider choice
 * @param id optional provider response identifier
 * @param model optional provider model name
 * @param finishReason optional provider finish reason
 */
public record ChatResponse(String text, String id, String model, String finishReason) {
    /**
     * Creates a response that only carries generated text.
     *
     * @param text assistant text
     */
    public ChatResponse(String text) {
        this(text, null, null, null);
    }

    /**
     * Creates a response after validating required fields.
     *
     * @param text assistant text
     * @param id optional provider response identifier
     * @param model optional provider model name
     * @param finishReason optional provider finish reason
     */
    public ChatResponse {
        text = Objects.requireNonNull(text, "text must not be null");
    }
}
