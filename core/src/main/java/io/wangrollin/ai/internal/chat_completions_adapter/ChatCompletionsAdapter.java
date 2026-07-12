package io.wangrollin.ai.internal.chat_completions_adapter;

import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;

/**
 * Adapter between the neutral turn protocol and OpenAI-compatible Chat Completions.
 */
public final class ChatCompletionsAdapter {
    public static final String PATH = OpenAiChatCodec.CHAT_COMPLETIONS_PATH;

    private final OpenAiChatCodec codec = new OpenAiChatCodec();

    /**
     * Serializes a neutral turn request to a Chat Completions request body.
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
     * Parses a Chat Completions response body.
     *
     * @param body JSON response body
     * @return neutral turn result
     */
    public AiTurnResult parseTurnResult(String body) {
        return codec.parseTurnResult(body);
    }

    /**
     * Parses one Chat Completions stream event.
     *
     * @param data raw SSE data value
     * @return neutral stream event
     */
    public AiStreamEvent parseStreamEvent(String data) {
        return codec.parseStreamEvent(data);
    }

    /**
     * Parses OpenAI-compatible error details.
     *
     * @param body JSON error body
     * @return structured error, or {@code null}
     */
    public AiError parseError(String body) {
        return codec.parseError(body);
    }
}
