package io.wangrolliin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void sendsChatCompletionRequestAndParsesText() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {
                      "choices": [
                        {
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
        assertEquals("/chat/completions", captured.get().path());
        assertEquals("POST", captured.get().method());
        assertEquals("Bearer test-key", captured.get().authorization());
        assertTrue(captured.get().contentType().startsWith("application/json"));

        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("test-model", requestJson.path("model").asText());
        assertEquals("user", requestJson.path("messages").path(0).path("role").asText());
        assertEquals("Hello", requestJson.path("messages").path(0).path("content").asText());
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
    void closesStreamInput() {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("""
                data: [DONE]

                """);
        ChatStream stream = new ChatStream(inputStream, OBJECT_MAPPER);

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
