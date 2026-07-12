package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;
import io.wangrollin.ai.internal.protocol.ProtocolMapper;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;

/**
 * Internal provider wire-shape adapter used by the HTTP client.
 *
 * <p>The public SDK model stays provider-neutral. Implementations of this
 * interface own endpoint paths, provider-specific JSON payloads, response
 * parsing, streaming event parsing, and structured error extraction.
 */
public interface AiProviderAdapter {
    /**
     * Creates a provider-specific chat request specification.
     *
     * @param request public chat request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether the request should enable provider streaming
     * @return provider request specification
     */
    default ProviderRequestSpec chatRequest(ChatRequest request, String defaultModel, boolean stream) {
        return chatRequest(ProtocolMapper.fromChatRequest(request), defaultModel, stream);
    }

    /**
     * Creates a provider-specific chat request specification from the neutral turn protocol.
     *
     * @param request internal neutral request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether the request should enable provider streaming
     * @return provider request specification
     */
    ProviderRequestSpec chatRequest(AiTurnRequest request, String defaultModel, boolean stream);

    /**
     * Parses a complete chat response body.
     *
     * @param body provider response body
     * @return SDK chat response
     */
    default ChatResponse parseChatResponse(String body) {
        return ProtocolMapper.toChatResponse(parseChatTurnResult(body));
    }

    /**
     * Parses a complete chat body into the neutral turn protocol.
     *
     * @param body provider response body
     * @return neutral turn result
     */
    AiTurnResult parseChatTurnResult(String body);

    /**
     * Parses one provider streaming chat event data value.
     *
     * @param data raw server-sent event data value
     * @return SDK chat delta
     */
    default ChatDelta parseChatStreamDelta(String data) {
        return ProtocolMapper.toChatDelta(parseChatStreamEvent(data));
    }

    /**
     * Parses one provider streaming chat event data value into the neutral protocol.
     *
     * @param data raw server-sent event data value
     * @return neutral stream event
     */
    AiStreamEvent parseChatStreamEvent(String data);

    /**
     * Parses one provider streaming chat event with access to the SSE event name.
     *
     * <p>OpenAI-compatible streams usually rely only on {@code data}. Providers
     * such as Anthropic use named events, so adapters can override this method
     * while older codecs continue to implement the data-only parser.
     *
     * @param event optional SSE event name
     * @param data raw server-sent event data value
     * @return SDK chat delta
     */
    default ChatDelta parseChatStreamDelta(String event, String data) {
        return ProtocolMapper.toChatDelta(parseChatStreamEvent(event, data));
    }

    /**
     * Parses one provider streaming chat event with access to the SSE event name.
     *
     * @param event optional SSE event name
     * @param data raw server-sent event data value
     * @return neutral stream event
     */
    default AiStreamEvent parseChatStreamEvent(String event, String data) {
        return parseChatStreamEvent(data);
    }

    /**
     * Creates a provider-specific Responses API request specification.
     *
     * @param request public Responses API request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether the request should enable provider streaming
     * @return provider request specification
     */
    default ProviderRequestSpec responseRequest(ResponseRequest request, String defaultModel, boolean stream) {
        return responseRequest(ProtocolMapper.fromResponseRequest(request), defaultModel, stream);
    }

    /**
     * Creates a provider-specific Responses API request specification from the neutral turn protocol.
     *
     * @param request internal neutral request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether the request should enable provider streaming
     * @return provider request specification
     */
    ProviderRequestSpec responseRequest(AiTurnRequest request, String defaultModel, boolean stream);

    /**
     * Parses a complete Responses API body.
     *
     * @param body provider response body
     * @return SDK response result
     */
    default ResponseResult parseResponseResult(String body) {
        return ProtocolMapper.toResponseResult(parseResponseTurnResult(body));
    }

    /**
     * Parses a complete Responses API body into the neutral turn protocol.
     *
     * @param body provider response body
     * @return neutral turn result
     */
    AiTurnResult parseResponseTurnResult(String body);

    /**
     * Parses one provider streaming Responses API event data value.
     *
     * @param data raw server-sent event data value
     * @return SDK response delta
     */
    default ResponseDelta parseResponseStreamDelta(String data) {
        return ProtocolMapper.toResponseDelta(parseResponseStreamEvent(data));
    }

    /**
     * Parses one provider streaming Responses API event data value into the neutral protocol.
     *
     * @param data raw server-sent event data value
     * @return neutral stream event
     */
    AiStreamEvent parseResponseStreamEvent(String data);

    /**
     * Parses structured provider error details when available.
     *
     * @param body provider error body
     * @return structured error, or {@code null} when unavailable
     */
    AiError parseError(String body);
}
