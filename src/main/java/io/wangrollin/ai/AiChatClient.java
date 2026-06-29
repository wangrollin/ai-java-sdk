package io.wangrollin.ai;

/**
 * Minimal chat contract that application code can depend on without coupling
 * itself to a concrete HTTP transport or provider implementation.
 */
public interface AiChatClient {
    /**
     * Sends a chat request and waits for the complete assistant response.
     *
     * @param request chat request to send
     * @return parsed chat response
     * @throws AiException when transport, provider, or response parsing fails
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Sends a chat request and returns an iterator over streaming deltas.
     *
     * @param request chat request to send
     * @return stream that must be closed by the caller
     * @throws AiException when the stream cannot be opened
     */
    ChatStream stream(ChatRequest request);
}
