package io.wangrollin.ai.internal.claude_messages_adapter;

import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.internal.anthropic.AnthropicMessageCodec;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;

/**
 * Adapter between the neutral turn protocol and Anthropic Claude Messages.
 */
public final class ClaudeMessagesAdapter {
    public static final String PATH = AnthropicMessageCodec.MESSAGES_PATH;

    private final AnthropicMessageCodec codec = new AnthropicMessageCodec();

    /**
     * Serializes a neutral turn request to a Claude Messages request body.
     *
     * @param request neutral request
     * @param defaultModel fallback model
     * @param stream whether to enable streaming
     * @return JSON request body
     */
    public String serializeRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        return codec.serializeRequest(request, defaultModel, stream);
    }

    /**
     * Parses a Claude Messages response body.
     *
     * @param body JSON response body
     * @return neutral turn result
     */
    public AiTurnResult parseTurnResult(String body) {
        return codec.parseTurnResult(body);
    }

    /**
     * Parses one named Claude Messages stream event.
     *
     * @param event SSE event name
     * @param data raw SSE data value
     * @return neutral stream event
     */
    public AiStreamEvent parseStreamEvent(String event, String data) {
        return codec.parseStreamEvent(event, data);
    }

    /**
     * Parses Anthropic error details.
     *
     * @param body JSON error body
     * @return structured error, or {@code null}
     */
    public AiError parseError(String body) {
        return codec.parseError(body);
    }
}
