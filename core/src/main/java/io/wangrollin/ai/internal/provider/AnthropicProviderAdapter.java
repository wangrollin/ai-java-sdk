package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.anthropic.AnthropicMessageCodec;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;

import java.util.Map;

/**
 * Adapter for Anthropic's native Claude Messages API.
 */
public final class AnthropicProviderAdapter implements AiProviderAdapter {
    private static final String CHAT_OPERATION = "anthropic.chat";
    private static final String CHAT_STREAM_OPERATION = "anthropic.stream";
    private static final String MESSAGES_PATH = "/" + AnthropicMessageCodec.MESSAGES_PATH;
    private static final Map<String, String> AUTH_HEADERS = Map.of(
            "x-api-key", "{apiKey}",
            "anthropic-version", "2023-06-01");

    private final AnthropicMessageCodec messageCodec = new AnthropicMessageCodec();

    @Override
    public ProviderRequestSpec chatRequest(ChatRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? CHAT_STREAM_OPERATION : CHAT_OPERATION,
                MESSAGES_PATH,
                model,
                stream,
                AUTH_HEADERS,
                messageCodec.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public ChatResponse parseChatResponse(String body) {
        return messageCodec.parseResponse(body);
    }

    @Override
    public ChatDelta parseChatStreamDelta(String data) {
        return messageCodec.parseStreamDelta(null, data);
    }

    @Override
    public ChatDelta parseChatStreamDelta(String event, String data) {
        return messageCodec.parseStreamDelta(event, data);
    }

    @Override
    public ProviderRequestSpec responseRequest(ResponseRequest request, String defaultModel, boolean stream) {
        throw new AiException("Anthropic does not support the OpenAI Responses API; use chat() or stream() instead");
    }

    @Override
    public ResponseResult parseResponseResult(String body) {
        throw new AiException("Anthropic does not support the OpenAI Responses API");
    }

    @Override
    public ResponseDelta parseResponseStreamDelta(String data) {
        throw new AiException("Anthropic does not support the OpenAI Responses API");
    }

    @Override
    public AiError parseError(String body) {
        return messageCodec.parseError(body);
    }
}
