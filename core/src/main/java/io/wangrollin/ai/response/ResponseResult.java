package io.wangrollin.ai.response;

import java.util.List;
import java.util.Objects;

/**
 * Complete text result returned by a synchronous Responses API call.
 *
 * @param text output text extracted from the provider response
 * @param id optional provider response identifier
 * @param model optional provider model name
 * @param status optional provider response status
 * @param usage optional token accounting metadata
 * @param toolCalls function tool calls requested by the model
 */
public record ResponseResult(
        String text,
        String id,
        String model,
        String status,
        ResponseUsage usage,
        List<ResponseToolCall> toolCalls) {
    /**
     * Creates a text-only result.
     *
     * @param text output text
     */
    public ResponseResult(String text) {
        this(text, null, null, null, null, List.of());
    }

    /**
     * Creates a result without tool calls for compatibility with text-only usage.
     *
     * @param text output text
     * @param id optional provider response identifier
     * @param model optional provider model name
     * @param status optional provider response status
     * @param usage optional token accounting metadata
     */
    public ResponseResult(String text, String id, String model, String status, ResponseUsage usage) {
        this(text, id, model, status, usage, List.of());
    }

    public ResponseResult {
        text = Objects.requireNonNull(text, "text must not be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
