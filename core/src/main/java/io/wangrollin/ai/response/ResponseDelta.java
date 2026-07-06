package io.wangrollin.ai.response;

/**
 * Incremental update read from a streaming Responses API response.
 *
 * @param text newly emitted output text
 * @param done whether the provider signaled completion
 */
public record ResponseDelta(String text, boolean done) {
    public ResponseDelta {
        if (text == null) {
            text = "";
        }
    }
}
