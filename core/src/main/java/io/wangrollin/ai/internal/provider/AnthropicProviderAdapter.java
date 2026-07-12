package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.claude_messages_adapter.ClaudeMessagesAdapter;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;

import java.util.Map;

/**
 * Adapter for Anthropic's native Claude Messages API.
 */
public final class AnthropicProviderAdapter implements AiProviderAdapter {
    private static final String CHAT_OPERATION = "anthropic.chat";
    private static final String CHAT_STREAM_OPERATION = "anthropic.stream";
    private static final String MESSAGES_PATH = "/" + ClaudeMessagesAdapter.PATH;
    private static final Map<String, String> AUTH_HEADERS = Map.of(
            "x-api-key", "{apiKey}",
            "anthropic-version", "2023-06-01");

    private final ClaudeMessagesAdapter claudeMessagesAdapter = new ClaudeMessagesAdapter();

    @Override
    public ProviderRequestSpec chatRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        return new ProviderRequestSpec(
                stream ? CHAT_STREAM_OPERATION : CHAT_OPERATION,
                MESSAGES_PATH,
                model,
                stream,
                AUTH_HEADERS,
                claudeMessagesAdapter.serializeRequest(request, defaultModel, stream));
    }

    @Override
    public AiTurnResult parseChatTurnResult(String body) {
        return claudeMessagesAdapter.parseTurnResult(body);
    }

    @Override
    public AiStreamEvent parseChatStreamEvent(String data) {
        return claudeMessagesAdapter.parseStreamEvent(null, data);
    }

    @Override
    public AiStreamEvent parseChatStreamEvent(String event, String data) {
        return claudeMessagesAdapter.parseStreamEvent(event, data);
    }

    @Override
    public ProviderRequestSpec responseRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        throw new AiException("Anthropic does not support the OpenAI Responses API; use chat() or stream() instead");
    }

    @Override
    public AiTurnResult parseResponseTurnResult(String body) {
        throw new AiException("Anthropic does not support the OpenAI Responses API");
    }

    @Override
    public AiStreamEvent parseResponseStreamEvent(String data) {
        throw new AiException("Anthropic does not support the OpenAI Responses API");
    }

    @Override
    public AiError parseError(String body) {
        return claudeMessagesAdapter.parseError(body);
    }
}
