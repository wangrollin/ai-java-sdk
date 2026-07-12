package io.wangrollin.ai.internal.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral complete model turn result.
 *
 * @param text concatenated assistant text
 * @param id optional provider response id
 * @param model optional provider model name
 * @param finishReason optional finish reason
 * @param status optional provider response status
 * @param usage optional token accounting
 * @param toolCalls tool calls requested by the model
 */
public record AiTurnResult(
        String text,
        String id,
        String model,
        String finishReason,
        String status,
        AiUsage usage,
        List<AiToolCall> toolCalls) {
    public AiTurnResult {
        text = Objects.requireNonNull(text, "text must not be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
