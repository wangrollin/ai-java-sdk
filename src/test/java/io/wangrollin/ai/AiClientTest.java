package io.wangrollin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
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
    private static final OpenAiChatCodec OPEN_AI_CODEC = new OpenAiChatCodec();

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

    @Test
    void validatesRetryPolicy() {
        IllegalArgumentException maxAttempts = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .maxAttempts(0)
                .build());
        IllegalArgumentException initialDelay = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .initialDelay(Duration.ofMillis(-1))
                .build());
        IllegalArgumentException maxDelay = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .maxDelay(Duration.ofMillis(-1))
                .build());
        IllegalArgumentException delayOrder = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(2))
                .maxDelay(Duration.ofSeconds(1))
                .build());

        assertEquals("maxAttempts must be positive", maxAttempts.getMessage());
        assertEquals("initialDelay must not be negative", initialDelay.getMessage());
        assertEquals("maxDelay must not be negative", maxDelay.getMessage());
        assertEquals("initialDelay must not be greater than maxDelay", delayOrder.getMessage());
    }

    @Test
    void closesStreamInput() {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("""
                data: [DONE]

                """);
        ChatStream stream = new ChatStream(inputStream, OPEN_AI_CODEC);

        stream.close();

        assertTrue(inputStream.closed());
    }

    private AiClient testClient() {
        return AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private AiClient retryingTestClient(int maxAttempts) {
        return AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.builder()
                        .maxAttempts(maxAttempts)
                        .initialDelay(Duration.ZERO)
                        .maxDelay(Duration.ZERO)
                        .retryableStatusCodes(Set.of(429, 500, 502, 503, 504))
                        .build())
                .build();
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

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }
}
