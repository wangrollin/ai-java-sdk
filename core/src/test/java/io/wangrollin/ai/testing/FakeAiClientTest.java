package io.wangrollin.ai.testing;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakeAiClientTest {
    @Test
    void returnsEmbeddingResultsAndRecordsRequests() {
        EmbeddingResult expected = new EmbeddingResult(
                "test-model", List.of(new Embedding(0, List.of(0.1, 0.2))), null);
        FakeAiClient client = FakeAiClient.builder().embeddingResult(expected).build();
        EmbeddingRequest request = EmbeddingRequest.builder().input("document").build();

        assertEquals(expected, client.embed(request));
        assertEquals(List.of(request), client.embeddingRequests());
    }

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
    void recordsStructuredOutputRequestsForApplicationAssertions() {
        ChatResponseFormat format = ChatResponseFormat.jsonSchema("ticket_triage", """
                {
                  "type": "object",
                  "properties": {
                    "queue": { "type": "string" }
                  },
                  "required": ["queue"]
                }
                """);
        FakeAiClient client = FakeAiClient.builder()
                .chatResponse("{\"queue\":\"billing\"}")
                .build();

        client.chat(ChatRequest.builder()
                .message(ChatMessage.system("Return only JSON."))
                .message(ChatMessage.user("The invoice export is failing."))
                .responseFormat(format)
                .build());

        ChatRequest recorded = client.requests().get(0);
        assertEquals(format, recorded.responseFormat());
        assertEquals("The invoice export is failing.", recorded.messages().get(1).content());
    }

    @Test
    void scriptsFailureThenSuccessForApplicationFallbackTests() {
        FakeAiClient client = FakeAiClient.builder()
                .chatFailure(new AiException("primary model unavailable"))
                .chatResponse("fallback response")
                .build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Summarize the incident."))
                .build();

        assertThrows(AiException.class, () -> client.chat(request));
        assertEquals("fallback response", client.chat(request).text());
        assertEquals(List.of(request, request), client.requests());
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
    void returnsConfiguredResponseResultsInOrderAndRecordsRequests() {
        ResponseResult secondResult = new ResponseResult(
                "second",
                "resp-2",
                "model-2",
                "completed",
                new ResponseUsage(2, 3, 5));
        FakeAiClient client = FakeAiClient.builder()
                .responseResult("first")
                .responseResult(secondResult)
                .build();

        ResponseRequest firstRequest = ResponseRequest.builder()
                .input("First")
                .build();
        ResponseRequest secondRequest = ResponseRequest.builder()
                .model("override-model")
                .instructions("Answer briefly.")
                .input("Second")
                .textFormat(ResponseTextFormat.jsonObject())
                .build();

        assertEquals("first", client.respond(firstRequest).text());
        ResponseResult response = client.respond(secondRequest);

        assertEquals(secondResult, response);
        assertEquals(List.of(firstRequest, secondRequest), client.responseRequests());
        assertEquals("override-model", client.responseRequests().get(1).model());
        assertEquals(ResponseTextFormat.jsonObject(), client.responseRequests().get(1).textFormat());
    }

    @Test
    void returnsConfiguredResponseStreamDeltas() {
        FakeAiClient client = FakeAiClient.builder()
                .responseStreamDeltas(
                        new ResponseDelta("Hel", false),
                        new ResponseDelta("lo", false),
                        new ResponseDelta("", true))
                .build();

        List<ResponseDelta> deltas = new ArrayList<>();
        try (ResponseStream stream = client.streamResponse(ResponseRequest.builder()
                .input("Hello")
                .build())) {
            for (ResponseDelta delta : stream) {
                deltas.add(delta);
            }
        }

        assertEquals(List.of(
                new ResponseDelta("Hel", false),
                new ResponseDelta("lo", false)), deltas);
    }

    @Test
    void propagatesConfiguredFailures() {
        AiException chatFailure = new AiException("chat failed");
        AiException streamFailure = new AiException("stream failed");
        AiException responseFailure = new AiException("response failed");
        AiException responseStreamFailure = new AiException("response stream failed");
        AiException embeddingFailure = new AiException("embedding failed");
        FakeAiClient client = FakeAiClient.builder()
                .chatFailure(chatFailure)
                .streamFailure(streamFailure)
                .responseFailure(responseFailure)
                .responseStreamFailure(responseStreamFailure)
                .embeddingFailure(embeddingFailure)
                .build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build();
        ResponseRequest responseRequest = ResponseRequest.builder()
                .input("Hello")
                .build();
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder().input("Hello").build();

        assertEquals(chatFailure, assertThrows(AiException.class, () -> client.chat(request)));
        assertEquals(streamFailure, assertThrows(AiException.class, () -> client.stream(request)));
        assertEquals(responseFailure, assertThrows(AiException.class, () -> client.respond(responseRequest)));
        assertEquals(
                responseStreamFailure,
                assertThrows(AiException.class, () -> client.streamResponse(responseRequest)));
        assertEquals(embeddingFailure, assertThrows(AiException.class, () -> client.embed(embeddingRequest)));
        assertEquals(List.of(request, request), client.requests());
        assertEquals(List.of(responseRequest, responseRequest), client.responseRequests());
        assertEquals(List.of(embeddingRequest), client.embeddingRequests());
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
    void canReturnMalformedResponseStreamEventsForConsumerFailureTests() {
        FakeAiClient client = FakeAiClient.builder()
                .responseStreamMalformedEvent("{\"type\":")
                .build();

        try (ResponseStream stream = client.streamResponse(ResponseRequest.builder()
                .input("Hello")
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertEquals("Failed to parse response stream event", exception.getMessage());
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
        ResponseRequest responseRequest = ResponseRequest.builder()
                .input("Hello")
                .build();
        AiException responseException = assertThrows(AiException.class, () -> client.respond(responseRequest));
        AiException responseStreamException = assertThrows(
                AiException.class,
                () -> client.streamResponse(responseRequest));
        AiException embeddingException = assertThrows(
                AiException.class,
                () -> client.embed(EmbeddingRequest.builder().input("Hello").build()));

        assertEquals("No fake chat response configured", chatException.getMessage());
        assertEquals("No fake stream response configured", streamException.getMessage());
        assertEquals("No fake response result configured", responseException.getMessage());
        assertEquals("No fake response stream configured", responseStreamException.getMessage());
        assertEquals("No fake embedding result configured", embeddingException.getMessage());
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

    @Test
    void responseRequestHistoryCannotBeMutatedByCallers() {
        FakeAiClient client = FakeAiClient.builder()
                .responseResult("ok")
                .build();

        client.respond(ResponseRequest.builder()
                .input("Hello")
                .build());

        assertThrows(UnsupportedOperationException.class, () -> client.responseRequests().clear());
        assertEquals(1, client.responseRequests().size());
    }
}
