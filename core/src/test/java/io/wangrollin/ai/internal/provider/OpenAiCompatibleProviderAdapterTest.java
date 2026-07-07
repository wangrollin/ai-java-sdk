package io.wangrollin.ai.internal.provider;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleProviderAdapterTest {
    private final OpenAiCompatibleProviderAdapter adapter = new OpenAiCompatibleProviderAdapter();

    @Test
    void createsChatRequestSpecForSyncAndStreamCalls() {
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build();

        ProviderRequestSpec sync = adapter.chatRequest(request, "test-model", false);
        ProviderRequestSpec stream = adapter.chatRequest(request, "test-model", true);

        assertEquals("chat", sync.operation());
        assertEquals("/chat/completions", sync.path());
        assertEquals("test-model", sync.model());
        assertTrue(sync.body().contains("\"messages\""));
        assertEquals("stream", stream.operation());
        assertTrue(stream.stream());
        assertTrue(stream.body().contains("\"stream\":true"));
    }

    @Test
    void createsResponseRequestSpecForSyncAndStreamCalls() {
        ResponseRequest request = ResponseRequest.builder()
                .model("request-model")
                .input("Hello")
                .build();

        ProviderRequestSpec sync = adapter.responseRequest(request, "default-model", false);
        ProviderRequestSpec stream = adapter.responseRequest(request, "default-model", true);

        assertEquals("response", sync.operation());
        assertEquals("/responses", sync.path());
        assertEquals("request-model", sync.model());
        assertTrue(sync.body().contains("\"input\":\"Hello\""));
        assertEquals("response.stream", stream.operation());
        assertTrue(stream.stream());
        assertTrue(stream.body().contains("\"stream\":true"));
    }

    @Test
    void delegatesParsingToOpenAiCompatibleCodecs() {
        ChatResponse chatResponse = adapter.parseChatResponse("""
                {"choices":[{"message":{"content":"chat text"},"finish_reason":"stop"}]}
                """);
        ChatDelta chatDelta = adapter.parseChatStreamDelta("""
                {"choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}
                """);
        ResponseResult responseResult = adapter.parseResponseResult("""
                {"output_text":"response text","status":"completed"}
                """);
        ResponseDelta responseDelta = adapter.parseResponseStreamDelta("""
                {"type":"response.output_text.delta","delta":"Res"}
                """);
        AiError error = adapter.parseError("""
                {"error":{"message":"rate limited","type":"rate_limit","code":"too_many_requests"}}
                """);

        assertEquals("chat text", chatResponse.text());
        assertEquals("stop", chatResponse.finishReason());
        assertEquals("Hel", chatDelta.text());
        assertEquals("response text", responseResult.text());
        assertEquals("completed", responseResult.status());
        assertEquals("Res", responseDelta.text());
        assertEquals(new AiError("rate limited", "rate_limit", "too_many_requests"), error);
    }
}
