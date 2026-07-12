package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
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
    ProviderRequestSpec chatRequest(ChatRequest request, String defaultModel, boolean stream);

    /**
     * Parses a complete chat response body.
     *
     * @param body provider response body
     * @return SDK chat response
     */
    ChatResponse parseChatResponse(String body);

    /**
     * Parses one provider streaming chat event data value.
     *
     * @param data raw server-sent event data value
     * @return SDK chat delta
     */
    ChatDelta parseChatStreamDelta(String data);

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
        return parseChatStreamDelta(data);
    }

    /**
     * Creates a provider-specific Responses API request specification.
     *
     * @param request public Responses API request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether the request should enable provider streaming
     * @return provider request specification
     */
    ProviderRequestSpec responseRequest(ResponseRequest request, String defaultModel, boolean stream);

    /**
     * Parses a complete Responses API body.
     *
     * @param body provider response body
     * @return SDK response result
     */
    ResponseResult parseResponseResult(String body);

    /**
     * Parses one provider streaming Responses API event data value.
     *
     * @param data raw server-sent event data value
     * @return SDK response delta
     */
    ResponseDelta parseResponseStreamDelta(String data);

    /**
     * Parses structured provider error details when available.
     *
     * @param body provider error body
     * @return structured error, or {@code null} when unavailable
     */
    AiError parseError(String body);
}
