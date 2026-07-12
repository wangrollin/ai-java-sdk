package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.internal.chat_completions_adapter.ChatCompletionsAdapter;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;
import io.wangrollin.ai.internal.responses_adapter.ResponsesAdapter;

import java.util.Map;

/**
 * Default adapter for OpenAI-compatible chat completions and Responses API endpoints.
 */
public final class OpenAiCompatibleProviderAdapter implements AiProviderAdapter {
    private static final String CHAT_OPERATION = "chat";
    private static final String CHAT_STREAM_OPERATION = "stream";
    private static final String RESPONSE_OPERATION = "response";
    private static final String RESPONSE_STREAM_OPERATION = "response.stream";
    private static final String CHAT_PATH = "/" + ChatCompletionsAdapter.PATH;
    private static final String RESPONSES_PATH = "/" + ResponsesAdapter.PATH;
    private static final Map<String, String> AUTH_HEADERS = Map.of("Authorization", "Bearer {apiKey}");

    private final ChatCompletionsAdapter chatCompletionsAdapter = new ChatCompletionsAdapter();
    private final ResponsesAdapter responsesAdapter = new ResponsesAdapter();

    @Override
    public ProviderRequestSpec chatRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? CHAT_STREAM_OPERATION : CHAT_OPERATION,
                CHAT_PATH,
                model,
                stream,
                AUTH_HEADERS,
                chatCompletionsAdapter.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public AiTurnResult parseChatTurnResult(String body) {
        return chatCompletionsAdapter.parseTurnResult(body);
    }

    @Override
    public AiStreamEvent parseChatStreamEvent(String data) {
        return chatCompletionsAdapter.parseStreamEvent(data);
    }

    @Override
    public ProviderRequestSpec responseRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? RESPONSE_STREAM_OPERATION : RESPONSE_OPERATION,
                RESPONSES_PATH,
                model,
                stream,
                AUTH_HEADERS,
                responsesAdapter.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public AiTurnResult parseResponseTurnResult(String body) {
        return responsesAdapter.parseTurnResult(body);
    }

    @Override
    public AiStreamEvent parseResponseStreamEvent(String data) {
        return responsesAdapter.parseStreamEvent(data);
    }

    @Override
    public AiError parseError(String body) {
        return chatCompletionsAdapter.parseError(body);
    }
}
