package io.wangrollin.ai.chat;

import java.util.Objects;
import java.util.List;

/**
 * Complete response returned by a synchronous chat call.
 *
 * <p>The {@link #text()} value is normalized to a non-null string. Provider
 * metadata is optional because compatible APIs differ in how consistently they
 * return identifiers, finish reasons, usage accounting, and tool calls.
 *
 * @param text assistant text extracted from the first provider choice
 * @param id optional provider response identifier
 * @param model optional provider model name
 * @param finishReason optional provider finish reason
 * @param usage optional token accounting metadata
 * @param toolCalls tool calls requested by the model
 */
public record ChatResponse(
        String text,
        String id,
        String model,
        String finishReason,
        ChatUsage usage,
        List<ChatToolCall> toolCalls) {
    /**
     * Creates a response that only carries generated text.
     *
     * @param text assistant text
     */
    public ChatResponse(String text) {
        this(text, null, null, null, null, List.of());
    }

    /**
     * Creates a response without usage metadata.
     *
     * @param text assistant text
     * @param id optional provider response identifier
     * @param model optional provider model name
     * @param finishReason optional provider finish reason
     */
    public ChatResponse(String text, String id, String model, String finishReason) {
        this(text, id, model, finishReason, null, List.of());
    }

    /**
     * Creates a response with usage metadata and no tool calls.
     *
     * @param text assistant text
     * @param id optional provider response identifier
     * @param model optional provider model name
     * @param finishReason optional provider finish reason
     * @param usage optional token accounting metadata
     */
    public ChatResponse(String text, String id, String model, String finishReason, ChatUsage usage) {
        this(text, id, model, finishReason, usage, List.of());
    }

    /**
     * Creates a response after validating required fields.
     *
     * @param text assistant text
     * @param id optional provider response identifier
     * @param model optional provider model name
     * @param finishReason optional provider finish reason
     * @param usage optional token accounting metadata
     */
    public ChatResponse {
        text = Objects.requireNonNull(text, "text must not be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
