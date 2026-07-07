package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import io.wangrollin.ai.internal.openai.OpenAiResponseCodec;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;

/**
 * Default adapter for OpenAI-compatible chat completions and Responses API endpoints.
 */
public final class OpenAiCompatibleProviderAdapter implements AiProviderAdapter {
    private static final String CHAT_OPERATION = "chat";
    private static final String CHAT_STREAM_OPERATION = "stream";
    private static final String RESPONSE_OPERATION = "response";
    private static final String RESPONSE_STREAM_OPERATION = "response.stream";
    private static final String CHAT_PATH = "/" + OpenAiChatCodec.CHAT_COMPLETIONS_PATH;
    private static final String RESPONSES_PATH = "/" + OpenAiResponseCodec.RESPONSES_PATH;

    private final OpenAiChatCodec chatCodec = new OpenAiChatCodec();
    private final OpenAiResponseCodec responseCodec = new OpenAiResponseCodec();

    @Override
    public ProviderRequestSpec chatRequest(ChatRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? CHAT_STREAM_OPERATION : CHAT_OPERATION,
                CHAT_PATH,
                model,
                stream,
                chatCodec.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public ChatResponse parseChatResponse(String body) {
        return chatCodec.parseResponse(body);
    }

    @Override
    public ChatDelta parseChatStreamDelta(String data) {
        return chatCodec.parseStreamDelta(data);
    }

    @Override
    public ProviderRequestSpec responseRequest(ResponseRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? RESPONSE_STREAM_OPERATION : RESPONSE_OPERATION,
                RESPONSES_PATH,
                model,
                stream,
                responseCodec.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public ResponseResult parseResponseResult(String body) {
        return responseCodec.parseResponse(body);
    }

    @Override
    public ResponseDelta parseResponseStreamDelta(String data) {
        return responseCodec.parseStreamDelta(data);
    }

    @Override
    public AiError parseError(String body) {
        return chatCodec.parseError(body);
    }
}
