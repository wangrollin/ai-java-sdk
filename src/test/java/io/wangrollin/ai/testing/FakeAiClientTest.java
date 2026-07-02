package io.wangrollin.ai.testing;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.error.AiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakeAiClientTest {
    @Test
    void returnsConfiguredChatResponsesInOrderAndRecordsRequests() {
        ChatToolCall toolCall = new ChatToolCall("call-1", "lookup_weather", "{\"city\":\"Shanghai\"}");
        FakeAiClient client = FakeAiClient.builder()
                .chatResponse("first")
                .chatResponse(new ChatResponse("second", "id-2", "model-2", "tool_calls", null, List.of(toolCall)))
                .build();

        ChatRequest firstRequest = ChatRequest.builder()
                .message(ChatMessage.user("First"))
                .build();
        ChatRequest secondRequest = ChatRequest.builder()
                .model("override-model")
                .message(ChatMessage.user("Second"))
                .tool(ChatTool.function("lookup_weather", "{\"type\":\"object\",\"properties\":{}}"))
                .build();

        assertEquals("first", client.chat(firstRequest).text());
        ChatResponse secondResponse = client.chat(secondRequest);

        assertEquals("second", secondResponse.text());
        assertEquals("id-2", secondResponse.id());
        assertEquals(List.of(toolCall), secondResponse.toolCalls());
        assertEquals(List.of(firstRequest, secondRequest), client.requests());
        assertEquals("lookup_weather", client.requests().get(1).tools().get(0).name());
    }

    @Test
    void returnsConfiguredStreamDeltas() {
        ChatToolCall toolCall = new ChatToolCall("call-1", "lookup_weather", "{\"city\"");
        FakeAiClient client = FakeAiClient.builder()
                .streamDeltas(
                        new ChatDelta("Hel", null),
                        new ChatDelta("lo", null),
                        new ChatDelta("", null, List.of(toolCall)),
                        new ChatDelta("", "stop"))
                .build();

        List<ChatDelta> deltas = new ArrayList<>();
        try (ChatStream stream = client.stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            for (ChatDelta delta : stream) {
                deltas.add(delta);
            }
        }

        assertEquals(List.of(
                new ChatDelta("Hel", null),
                new ChatDelta("lo", null),
                new ChatDelta("", null, List.of(toolCall)),
                new ChatDelta("", "stop")), deltas);
    }

    @Test
    void propagatesConfiguredFailures() {
        AiException chatFailure = new AiException("chat failed");
        AiException streamFailure = new AiException("stream failed");
        FakeAiClient client = FakeAiClient.builder()
                .chatFailure(chatFailure)
                .streamFailure(streamFailure)
                .build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build();

        assertEquals(chatFailure, assertThrows(AiException.class, () -> client.chat(request)));
        assertEquals(streamFailure, assertThrows(AiException.class, () -> client.stream(request)));
        assertEquals(List.of(request, request), client.requests());
    }

    @Test
    void canReturnMalformedStreamEventsForConsumerFailureTests() {
        FakeAiClient client = FakeAiClient.builder()
                .streamMalformedEvent("{\"choices\":[")
                .build();

        try (ChatStream stream = client.stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertEquals("Failed to parse chat stream event", exception.getMessage());
        }
    }

    @Test
    void throwsClearErrorWhenNoOutcomeIsConfigured() {
        FakeAiClient client = FakeAiClient.builder().build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build();

        AiException chatException = assertThrows(AiException.class, () -> client.chat(request));
        AiException streamException = assertThrows(AiException.class, () -> client.stream(request));

        assertEquals("No fake chat response configured", chatException.getMessage());
        assertEquals("No fake stream response configured", streamException.getMessage());
    }

    @Test
    void requestHistoryCannotBeMutatedByCallers() {
        FakeAiClient client = FakeAiClient.builder()
                .chatResponse("ok")
                .build();

        client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertThrows(UnsupportedOperationException.class, () -> client.requests().clear());
        assertEquals(1, client.requests().size());
    }
}
