package io.wangrollin.ai.internal.protocol;

import java.util.List;

/**
 * Provider-neutral streaming event emitted by wire adapters.
 *
 * @param textDelta incremental text, or empty for metadata-only events
 * @param done whether the provider signaled completion
 * @param finishReason optional finish reason
 * @param toolCalls tool-call events completed by this stream item
 */
public record AiStreamEvent(String textDelta, boolean done, String finishReason, List<AiToolCall> toolCalls) {
    public AiStreamEvent {
        textDelta = textDelta == null ? "" : textDelta;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiStreamEvent text(String textDelta) {
        return new AiStreamEvent(textDelta, false, null, List.of());
    }

    public static AiStreamEvent done(String finishReason) {
        return new AiStreamEvent("", true, finishReason, List.of());
    }
}
