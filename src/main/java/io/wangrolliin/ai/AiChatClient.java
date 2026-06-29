package io.wangrolliin.ai;

/**
 * Minimal chat contract that application code can depend on without coupling
 * itself to a concrete HTTP transport or provider implementation.
 */
public interface AiChatClient {
    ChatResponse chat(ChatRequest request);

    ChatStream stream(ChatRequest request);
}
