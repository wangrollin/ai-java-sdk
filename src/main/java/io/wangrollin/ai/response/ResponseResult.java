package io.wangrollin.ai.response;

import java.util.Objects;

/**
 * Complete text result returned by a synchronous Responses API call.
 *
 * @param text output text extracted from the provider response
 * @param id optional provider response identifier
 * @param model optional provider model name
 * @param status optional provider response status
 * @param usage optional token accounting metadata
 */
public record ResponseResult(String text, String id, String model, String status, ResponseUsage usage) {
    /**
     * Creates a text-only result.
     *
     * @param text output text
     */
    public ResponseResult(String text) {
        this(text, null, null, null, null);
    }

    public ResponseResult {
        text = Objects.requireNonNull(text, "text must not be null");
    }
}
