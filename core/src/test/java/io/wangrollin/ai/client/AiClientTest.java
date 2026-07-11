package io.wangrollin.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiPayloadFailureEvent;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiPayloadResponseEvent;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiClientTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requiresApiKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> AiClient.builder()
                .defaultModel("test-model")
                .build());

        assertEquals("apiKey must not be null", exception.getMessage());
    }

    @Test
    void usesDefaultBaseUrlAndTimeout() {
        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .defaultModel("test-model")
                .build();

        assertTrue(client != null);
    }

    @Test
    void rejectsNullProvider() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> AiClient.builder()
                .provider(null));

        assertEquals("provider must not be null", exception.getMessage());
    }

    @Test
    void rejectsNullProviderPreset() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> AiClient.builder()
                .providerPreset(null));

        assertEquals("providerPreset must not be null", exception.getMessage());
    }

    @Test
    void createsUserMessage() {
        ChatMessage message = ChatMessage.user("Hello");

        assertEquals("user", message.role());
        assertEquals("Hello", message.content());
    }

    @Test
    void rejectsRequestWithoutMessages() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .build());

        assertEquals("messages must not be empty", exception.getMessage());
    }

    @Test
    void validatesOptionalChatRequestParameters() {
        IllegalArgumentException temperature = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .temperature(-0.1)
                .build());
        IllegalArgumentException topP = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .topP(-0.1)
                .build());
        IllegalArgumentException finiteTemperature = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .temperature(Double.NaN)
                .build());
        IllegalArgumentException maxTokens = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .maxTokens(0)
                .build());
        IllegalArgumentException stopSequence = assertThrows(IllegalArgumentException.class, () -> ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .stopSequence(" ")
                .build());

        assertEquals("temperature must not be negative", temperature.getMessage());
        assertEquals("topP must not be negative", topP.getMessage());
        assertEquals("temperature must be finite", finiteTemperature.getMessage());
        assertEquals("maxTokens must be positive", maxTokens.getMessage());
        assertEquals("stopSequence must not be blank", stopSequence.getMessage());
    }

    @Test
    void sendsChatCompletionRequestAndParsesText() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {
                      "id": "chatcmpl-test",
                      "model": "test-model",
                      "usage": {
                        "prompt_tokens": 4,
                        "completion_tokens": 5,
                        "total_tokens": 9
                      },
                      "choices": [
                        {
                          "finish_reason": "stop",
                          "message": {
                            "role": "assistant",
                            "content": "Hello from the model"
                          }
                        }
                      ]
                    }
                    """);
        });

        AiClient client = testClient();
        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("Hello from the model", response.text());
        assertEquals("chatcmpl-test", response.id());
        assertEquals("test-model", response.model());
        assertEquals("stop", response.finishReason());
        assertEquals(new ChatUsage(4, 5, 9), response.usage());
        assertEquals("/chat/completions", captured.get().path());
        assertEquals("POST", captured.get().method());
        assertEquals("Bearer test-key", captured.get().authorization());
        assertTrue(captured.get().contentType().startsWith("application/json"));

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("test-model", requestJson.path("model").asText());
        assertEquals("user", requestJson.path("messages").path(0).path("role").asText());
        assertEquals("Hello", requestJson.path("messages").path(0).path("content").asText());
        assertFalse(requestJson.has("temperature"));
        assertFalse(requestJson.has("top_p"));
        assertFalse(requestJson.has("max_tokens"));
        assertFalse(requestJson.has("stop"));
    }

    @Test
    void sendsChatCompletionRequestWithExplicitOpenAiCompatibleProvider() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .provider(AiProvider.OPENAI_COMPATIBLE)
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok", response.text());
        assertEquals("/chat/completions", captured.get().path());
    }

    @Test
    void explicitBaseUrlOverridesProviderPreset() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .providerPreset(AiProviderPreset.DEEPSEEK)
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok", response.text());
        assertEquals("/chat/completions", captured.get().path());
    }

    @Test
    void sendsOptionalChatRequestParametersWhenConfigured() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .temperature(0.2)
                .topP(0.9)
                .maxTokens(128)
                .stopSequences(List.of("END", "DONE"))
                .build());

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals(0.2, requestJson.path("temperature").asDouble());
        assertEquals(0.9, requestJson.path("top_p").asDouble());
        assertEquals(128, requestJson.path("max_tokens").asInt());
        assertEquals("END", requestJson.path("stop").path(0).asText());
        assertEquals("DONE", requestJson.path("stop").path(1).asText());
    }

    @Test
    void emitsRedactedPayloadDiagnosticsWhenEnabled() throws Exception {
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        startServer(exchange -> respond(exchange, 200, """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "secret answer",
                        "tool_calls": [
                          {
                            "function": {
                              "name": "lookup_weather",
                              "arguments": "{\\"city\\":\\"Shanghai\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """));

        ChatResponse response = testClient(diagnostics).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .tool(ChatTool.function("lookup_weather", "Look up current weather", """
                        {"type":"object","properties":{"city":{"type":"string"}}}
                        """))
                .build());

        assertEquals("secret answer", response.text());
        assertEquals(1, diagnostics.requests.size());
        assertEquals(1, diagnostics.responses.size());
        assertTrue(diagnostics.failures.isEmpty());
        String diagnosticPayloads = diagnostics.requests + " " + diagnostics.responses;
        assertFalse(diagnosticPayloads.contains("secret prompt"));
        assertFalse(diagnosticPayloads.contains("secret answer"));
        assertFalse(diagnosticPayloads.contains("Shanghai"));
        assertFalse(diagnosticPayloads.contains("test-key"));
        assertTrue(diagnosticPayloads.contains("<redacted>"));
        assertTrue(diagnostics.requests.get(0).redactedBody().contains("lookup_weather"));
        assertTrue(diagnostics.responses.get(0).redactedBody().contains("\"message\":\"<redacted>\""));
    }

    @Test
    void sendsJsonObjectResponseFormatWhenConfigured() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"{\\"answer\\":\\"ok\\"}"}}]}
                    """);
        });

        testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Reply with JSON"))
                .responseFormat(ChatResponseFormat.jsonObject())
                .build());

        JsonNode responseFormat = OBJECT_MAPPER.readTree(captured.get().body()).path("response_format");
        assertEquals("json_object", responseFormat.path("type").asText());
        assertFalse(responseFormat.has("json_schema"));
    }

    @Test
    void sendsJsonSchemaResponseFormatWhenConfigured() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"{\\"answer\\":\\"ok\\"}"}}]}
                    """);
        });

        testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Reply with JSON"))
                .responseFormat(ChatResponseFormat.jsonSchema("answer_shape", """
                        {
                          "type": "object",
                          "properties": {
                            "answer": { "type": "string" }
                          },
                          "required": ["answer"],
                          "additionalProperties": false
                        }
                        """))
                .build());

        JsonNode responseFormat = OBJECT_MAPPER.readTree(captured.get().body()).path("response_format");
        JsonNode jsonSchema = responseFormat.path("json_schema");
        assertEquals("json_schema", responseFormat.path("type").asText());
        assertEquals("answer_shape", jsonSchema.path("name").asText());
        assertTrue(jsonSchema.path("strict").asBoolean());
        assertEquals("object", jsonSchema.path("schema").path("type").asText());
        assertEquals("string", jsonSchema.path("schema").path("properties").path("answer").path("type").asText());
    }

    @Test
    void sendsToolsAndParsesToolCalls() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {
                      "choices": [
                        {
                          "finish_reason": "tool_calls",
                          "message": {
                            "role": "assistant",
                            "tool_calls": [
                              {
                                "id": "call-1",
                                "type": "function",
                                "function": {
                                  "name": "lookup_weather",
                                  "arguments": "{\\"city\\":\\"Shanghai\\"}"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """);
        });

        ChatResponse response = testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("What is the weather?"))
                .tool(ChatTool.function("lookup_weather", "Look up current weather", """
                        {"type":"object","properties":{"city":{"type":"string"}}}
                        """))
                .toolChoice(ChatToolChoice.required())
                .build());

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("lookup_weather", requestJson.path("tools").path(0).path("function").path("name").asText());
        assertEquals("required", requestJson.path("tool_choice").asText());
        assertEquals("", response.text());
        assertEquals("tool_calls", response.finishReason());
        assertEquals(List.of(new ChatToolCall("call-1", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                response.toolCalls());
    }

    @Test
    void validatesResponseFormat() {
        IllegalArgumentException blankName = assertThrows(IllegalArgumentException.class, () ->
                ChatResponseFormat.jsonSchema(" ", "{}"));
        IllegalArgumentException malformedSchema = assertThrows(IllegalArgumentException.class, () ->
                ChatResponseFormat.jsonSchema("answer_shape", "{"));
        IllegalArgumentException nonObjectSchema = assertThrows(IllegalArgumentException.class, () ->
                ChatResponseFormat.jsonSchema("answer_shape", "[]"));

        assertEquals("name must not be blank", blankName.getMessage());
        assertEquals("schemaJson must be valid JSON", malformedSchema.getMessage());
        assertEquals("schemaJson must be a JSON object", nonObjectSchema.getMessage());
    }

    @Test
    void responseMetadataIsOptional() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[{"message":{"content":"ok"}}]}
                """));

        ChatResponse response = testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok", response.text());
        assertNull(response.id());
        assertNull(response.model());
        assertNull(response.finishReason());
        assertNull(response.usage());
    }

    @Test
    void requestModelOverridesClientDefaultModel() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        testClient().chat(ChatRequest.builder()
                .model("request-model")
                .message(ChatMessage.user("Hello"))
                .build());

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("request-model", requestJson.path("model").asText());
    }

    @Test
    void throwsAiExceptionForHttpErrors() throws Exception {
        startServer(exchange -> respond(exchange, 429, """
                {"error":{"message":"rate limited"}}
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertTrue(exception.getMessage().contains("HTTP status 429"));
        assertTrue(exception.getMessage().contains("rate limited"));
        assertEquals(429, exception.statusCode());
        assertEquals("rate limited", exception.error().message());
    }

    @Test
    void exposesStructuredProviderErrors() throws Exception {
        startServer(exchange -> respond(exchange, 401, """
                {
                  "error": {
                    "message": "invalid api key",
                    "type": "authentication_error",
                    "code": "invalid_api_key"
                  }
                }
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals(401, exception.statusCode());
        assertEquals(new AiError("invalid api key", "authentication_error", "invalid_api_key"), exception.error());
        assertTrue(exception.getMessage().contains("invalid api key"));
    }

    @Test
    void emitsRedactedFailurePayloadDiagnostics() throws Exception {
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        startServer(exchange -> respond(exchange, 401, """
                {
                  "error": {
                    "message": "invalid api key raw detail",
                    "code": "invalid_api_key"
                  }
                }
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient(diagnostics).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build()));

        assertEquals(401, exception.statusCode());
        assertEquals(1, diagnostics.requests.size());
        assertEquals(1, diagnostics.failures.size());
        assertTrue(diagnostics.responses.isEmpty());
        String diagnosticPayloads = diagnostics.requests + " " + diagnostics.failures;
        assertFalse(diagnosticPayloads.contains("secret prompt"));
        assertFalse(diagnosticPayloads.contains("invalid api key raw detail"));
        assertTrue(diagnostics.failures.get(0).redactedBody().contains("invalid_api_key"));
    }

    @Test
    void fallsBackToBodySummaryForNonJsonProviderErrors() throws Exception {
        startServer(exchange -> respond(exchange, 502, "upstream unavailable"));

        AiException exception = assertThrows(AiException.class, () -> testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals(502, exception.statusCode());
        assertNull(exception.error());
        assertTrue(exception.getMessage().contains("upstream unavailable"));
    }

    @Test
    void doesNotRetryByDefault() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 429, """
                    {"error":{"message":"rate limited"}}
                    """);
        });

        AiException exception = assertThrows(AiException.class, () -> testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals(1, attempts.get());
        assertTrue(exception.getMessage().contains("HTTP status 429"));
    }

    @Test
    void retriesRetryableChatStatusUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 429, """
                        {"error":{"message":"rate limited"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok after retry"}}]}
                    """);
        });

        ChatResponse response = retryingTestClient(2).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok after retry", response.text());
        assertEquals(2, attempts.get());
    }

    @Test
    void payloadDiagnosticsIncludesRetryableHttpFailures() throws Exception {
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 429, """
                        {"error":{"message":"raw rate limit detail","code":"rate_limit"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok after retry"}}]}
                    """);
        });

        ChatResponse response = retryingTestClient(2, diagnostics).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build());

        assertEquals("ok after retry", response.text());
        assertEquals(1, diagnostics.requests.size());
        assertEquals(1, diagnostics.failures.size());
        assertEquals(1, diagnostics.responses.size());
        assertFalse(diagnostics.toString().contains("secret prompt"));
        assertFalse(diagnostics.toString().contains("raw rate limit detail"));
        assertTrue(diagnostics.failures.get(0).redactedBody().contains("rate_limit"));
    }

    @Test
    void retriesMultipleServerErrorsUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                respond(exchange, 500, """
                        {"error":{"message":"temporary server error"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok after server retry"}}]}
                    """);
        });

        ChatResponse response = retryingTestClient(3).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok after server retry", response.text());
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableChatStatus() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 400, """
                    {"error":{"message":"bad request"}}
                    """);
        });

        AiException exception = assertThrows(AiException.class, () -> retryingTestClient(3).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals(1, attempts.get());
        assertTrue(exception.getMessage().contains("HTTP status 400"));
        assertTrue(exception.getMessage().contains("bad request"));
    }

    @Test
    void throwsFinalChatErrorAfterRetryExhaustion() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 503, """
                    {"error":{"message":"still unavailable"}}
                    """);
        });

        AiException exception = assertThrows(AiException.class, () -> retryingTestClient(2).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals(2, attempts.get());
        assertTrue(exception.getMessage().contains("HTTP status 503"));
        assertTrue(exception.getMessage().contains("still unavailable"));
    }

    @Test
    void throwsAiExceptionWhenResponseShapeIsInvalid() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[]}
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient().chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertEquals("Chat response did not contain choices[0].message.content", exception.getMessage());
    }

    @Test
    void streamsChatDeltasAndSendsStreamRequest() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respondStream(exchange, """
                    data: {"choices":[{"delta":{"content":"Hel"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{"content":"lo"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]

                    """);
        });

        List<ChatDelta> deltas = new ArrayList<>();
        try (ChatStream stream = testClient().stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            for (ChatDelta delta : stream) {
                deltas.add(delta);
            }
        }

        assertEquals(List.of(
                new ChatDelta("Hel", null),
                new ChatDelta("lo", null),
                new ChatDelta("", "stop")), deltas);
        assertEquals("/chat/completions", captured.get().path());
        assertEquals("POST", captured.get().method());
        assertEquals("Bearer test-key", captured.get().authorization());
        assertTrue(captured.get().contentType().startsWith("application/json"));

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("test-model", requestJson.path("model").asText());
        assertTrue(requestJson.path("stream").asBoolean());
        assertEquals("user", requestJson.path("messages").path(0).path("role").asText());
        assertEquals("Hello", requestJson.path("messages").path(0).path("content").asText());
    }

    @Test
    void streamingDiagnosticsOnlyRecordsRequestForSuccessfulStream() throws Exception {
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        startServer(exchange -> respondStream(exchange, """
                data: {"choices":[{"delta":{"content":"secret stream answer"},"finish_reason":null}]}

                data: [DONE]

                """));

        try (ChatStream stream = testClient(diagnostics).stream(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build())) {
            for (ChatDelta ignored : stream) {
                // Consume the stream to prove diagnostics do not cache successful stream bodies.
            }
        }

        assertEquals(1, diagnostics.requests.size());
        assertTrue(diagnostics.responses.isEmpty());
        assertTrue(diagnostics.failures.isEmpty());
        assertFalse(diagnostics.requests.get(0).redactedBody().contains("secret prompt"));
        assertFalse(diagnostics.toString().contains("secret stream answer"));
    }

    @Test
    void streamRequestModelOverridesClientDefaultModel() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respondStream(exchange, """
                    data: [DONE]

                    """);
        });

        try (ChatStream stream = testClient().stream(ChatRequest.builder()
                .model("stream-model")
                .message(ChatMessage.user("Hello"))
                .build())) {
            assertFalse(stream.iterator().hasNext());
        }

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("stream-model", requestJson.path("model").asText());
    }

    @Test
    void throwsAiExceptionForStreamingHttpErrors() throws Exception {
        startServer(exchange -> respond(exchange, 429, """
                {"error":{"message":"stream rate limited"}}
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient().stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertTrue(exception.getMessage().contains("HTTP status 429"));
        assertTrue(exception.getMessage().contains("stream rate limited"));
        assertEquals(429, exception.statusCode());
        assertEquals("stream rate limited", exception.error().message());
    }

    @Test
    void retriesRetryableStreamingStatusUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                respond(exchange, 503, """
                        {"error":{"message":"stream unavailable"}}
                        """);
                return;
            }
            respondStream(exchange, """
                    data: {"choices":[{"delta":{"content":"ok"},"finish_reason":null}]}

                    data: [DONE]

                    """);
        });

        List<ChatDelta> deltas = new ArrayList<>();
        try (ChatStream stream = retryingTestClient(2).stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            for (ChatDelta delta : stream) {
                deltas.add(delta);
            }
        }

        assertEquals(List.of(new ChatDelta("ok", null)), deltas);
        assertEquals(2, attempts.get());
    }

    @Test
    void throwsAiExceptionForMalformedStreamEvents() throws Exception {
        startServer(exchange -> respondStream(exchange, """
                data: {"choices":[

                """));

        try (ChatStream stream = testClient().stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertTrue(exception.getMessage().contains("Failed to parse chat stream event"));
        }
    }

    @Test
    void doesNotRetryAfterStreamConsumptionStarts() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respondStream(exchange, """
                    data: {"choices":[

                    """);
        });

        try (ChatStream stream = retryingTestClient(3).stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertTrue(exception.getMessage().contains("Failed to parse chat stream event"));
        }
        assertEquals(1, attempts.get());
    }

    private AiClient testClient() {
        return testClient(null);
    }

    private AiClient testClient(AiPayloadDiagnosticsListener diagnosticsListener) {
        AiClient.Builder builder = AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5));
        if (diagnosticsListener != null) {
            builder.payloadDiagnosticsListener(diagnosticsListener);
        }
        return builder.build();
    }

    private AiClient retryingTestClient(int maxAttempts) {
        return retryingTestClient(maxAttempts, null);
    }

    private AiClient retryingTestClient(int maxAttempts, AiPayloadDiagnosticsListener diagnosticsListener) {
        AiClient.Builder builder = AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.builder()
                        .maxAttempts(maxAttempts)
                        .initialDelay(Duration.ZERO)
                        .maxDelay(Duration.ZERO)
                        .retryableStatusCodes(Set.of(429, 500, 502, 503, 504))
                        .build());
        if (diagnosticsListener != null) {
            builder.payloadDiagnosticsListener(diagnosticsListener);
        }
        return builder.build();
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                handler.handle(exchange);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        server.start();
    }

    private static CapturedRequest capture(HttpExchange exchange) {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return new CapturedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respondStream(HttpExchange exchange, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record CapturedRequest(
            String path,
            String method,
            String authorization,
            String contentType,
            String body) {
    }

    private static final class RecordingPayloadDiagnosticsListener implements AiPayloadDiagnosticsListener {
        private final List<AiPayloadRequestEvent> requests = new ArrayList<>();
        private final List<AiPayloadResponseEvent> responses = new ArrayList<>();
        private final List<AiPayloadFailureEvent> failures = new ArrayList<>();

        @Override
        public void requestPayload(AiPayloadRequestEvent event) {
            requests.add(event);
        }

        @Override
        public void responsePayload(AiPayloadResponseEvent event) {
            responses.add(event);
        }

        @Override
        public void failurePayload(AiPayloadFailureEvent event) {
            failures.add(event);
        }

        @Override
        public String toString() {
            return "requests=" + requests + ", responses=" + responses + ", failures=" + failures;
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

}
