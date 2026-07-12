package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.response.ResponseRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicProviderAdapterTest {
    private final AnthropicProviderAdapter adapter = new AnthropicProviderAdapter();

    @Test
    void createsChatRequestSpecForSyncAndStreamCalls() {
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build();

        ProviderRequestSpec sync = adapter.chatRequest(request, "claude-test", false);
        ProviderRequestSpec stream = adapter.chatRequest(request, "claude-test", true);

        assertEquals("anthropic.chat", sync.operation());
        assertEquals("/messages", sync.path());
        assertEquals("claude-test", sync.model());
        assertEquals("{apiKey}", sync.headers().get("x-api-key"));
        assertEquals("2023-06-01", sync.headers().get("anthropic-version"));
        assertTrue(sync.body().contains("\"messages\""));
        assertEquals("anthropic.stream", stream.operation());
        assertTrue(stream.stream());
        assertTrue(stream.body().contains("\"stream\":true"));
    }

    @Test
    void delegatesParsingToAnthropicCodec() {
        ChatResponse response = adapter.parseChatResponse("""
                {"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}
                """);
        ChatDelta delta = adapter.parseChatStreamDelta("content_block_delta", """
                {"delta":{"type":"text_delta","text":"ok"}}
                """);

        assertEquals("ok", response.text());
        assertEquals("end_turn", response.finishReason());
        assertEquals(new ChatDelta("ok", null), delta);
    }

    @Test
    void rejectsResponsesApiRequests() {
        AiException exception = assertThrows(AiException.class, () -> adapter.responseRequest(ResponseRequest.builder()
                .input("Hello")
                .build(), "claude-test", false));

        assertTrue(exception.getMessage().contains("does not support the OpenAI Responses API"));
    }
}
