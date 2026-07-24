package io.wangrollin.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiPayloadResponseEvent;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.embedding.EmbeddingUsage;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiResponseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

class AiEmbeddingClientTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsBatchRequestAndParsesVectors() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {
                      "model":"provider-model",
                      "data":[
                        {"index":0,"embedding":[0.1,0.2]},
                        {"index":1,"embedding":[0.3,0.4]}
                      ],
                      "usage":{"prompt_tokens":4,"total_tokens":4}
                    }
                    """);
        });

        EmbeddingResult result = testClient(null).embed(EmbeddingRequest.builder()
                .inputs(List.of("alpha", "beta"))
                .dimensions(2)
                .build());

        assertEquals(List.of(
                new Embedding(0, List.of(0.1, 0.2)),
                new Embedding(1, List.of(0.3, 0.4))), result.embeddings());
        assertEquals(new EmbeddingUsage(4, 4), result.usage());
        JsonNode request = OBJECT_MAPPER.readTree(body.get());
        assertEquals("test-model", request.path("model").asText());
        assertEquals("alpha", request.path("input").path(0).asText());
        assertEquals(2, request.path("dimensions").asInt());
    }

    @Test
    void usesDedicatedDefaultEmbeddingModel() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                    """);
        });

        testClient(null, "embedding-model").embed(EmbeddingRequest.builder()
                .input("alpha")
                .build());

        JsonNode request = OBJECT_MAPPER.readTree(body.get());
        assertEquals("embedding-model", request.path("model").asText());
    }

    @Test
    void requestModelOverridesDedicatedDefaultEmbeddingModel() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        startServer(exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                    """);
        });

        testClient(null, "embedding-model").embed(EmbeddingRequest.builder()
                .model("request-model")
                .input("alpha")
                .build());

        JsonNode request = OBJECT_MAPPER.readTree(body.get());
        assertEquals("request-model", request.path("model").asText());
    }

    @Test
    void rejectsBlankDedicatedDefaultEmbeddingModel() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost")
                .defaultModel("test-model")
                .defaultEmbeddingModel(" ")
                .build());

        assertTrue(failure.getMessage().contains("defaultEmbeddingModel"));
    }

    @Test
    void reportsProviderErrors() throws Exception {
        startServer(exchange -> respond(exchange, 429, """
                {"error":{"message":"rate limited"}}
                """));

        AiException failure = assertThrows(AiException.class, () -> testClient(null).embed(
                EmbeddingRequest.builder().input("alpha").build()));

        assertEquals(429, failure.statusCode());
        assertTrue(failure.getMessage().contains("rate limited"));
    }

    @Test
    void rejectsProviderResultsThatDoNotCoverEveryBatchInput() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"data":[{"index":0,"embedding":[0.1,0.2]}]}
                """));

        AiException failure = assertThrows(AiException.class, () -> testClient(null).embed(
                EmbeddingRequest.builder().inputs(List.of("alpha", "beta")).build()));

        assertTrue(failure.getMessage().contains("count does not match"));
    }

    @Test
    void redactsEmbeddingInputsAndVectorsFromDiagnostics() throws Exception {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        startServer(exchange -> respond(exchange, 200, """
                {"data":[{"index":0,"embedding":[0.123456,0.654321]}]}
                """));

        testClient(diagnostics).embed(EmbeddingRequest.builder().input("private document").build());

        String payloads = diagnostics.requests + " " + diagnostics.responses;
        assertFalse(payloads.contains("private document"));
        assertFalse(payloads.contains("0.123456"));
        assertTrue(payloads.contains("<redacted>"));
    }

    @Test
    void rejectsEmbeddingsForAnthropicProtocolBeforeSending() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));
        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .baseUrl(baseUrl())
                .defaultModel("claude-test")
                .provider(AiProvider.ANTHROPIC)
                .build();

        AiException failure = assertThrows(AiException.class, () -> client.embed(
                EmbeddingRequest.builder().input("alpha").build()));

        assertTrue(failure.getMessage().contains("does not expose an Embeddings API"));
    }

    @Test
    void emitsMetadataOnlyEmbeddingLifecycleEvent() throws Exception {
        AtomicReference<AiResponseEvent> succeeded = new AtomicReference<>();
        startServer(exchange -> respond(exchange, 200, """
                {
                  "model":"provider-model",
                  "data":[{"index":0,"embedding":[0.1,0.2]}],
                  "usage":{"prompt_tokens":2,"total_tokens":2}
                }
                """));
        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .baseUrl(baseUrl())
                .defaultModel("test-model")
                .eventListener(new AiEventListener() {
                    @Override
                    public void requestSucceeded(AiResponseEvent event) {
                        succeeded.set(event);
                    }
                })
                .build();

        client.embed(EmbeddingRequest.builder().input("private document").build());

        assertEquals("embeddings", succeeded.get().operation());
        assertEquals("provider-model", succeeded.get().model());
        assertEquals(2, succeeded.get().usage().promptTokens());
        assertEquals(0, succeeded.get().usage().completionTokens());
    }

    private AiClient testClient(AiPayloadDiagnosticsListener diagnostics) {
        return testClient(diagnostics, null);
    }

    private AiClient testClient(AiPayloadDiagnosticsListener diagnostics, String defaultEmbeddingModel) {
        AiClient.Builder builder = AiClient.builder()
                .apiKey("test-key")
                .baseUrl(baseUrl())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5));
        if (defaultEmbeddingModel != null) {
            builder.defaultEmbeddingModel(defaultEmbeddingModel);
        }
        if (diagnostics != null) {
            builder.payloadDiagnosticsListener(diagnostics);
        }
        return builder.build();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/embeddings", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class RecordingDiagnostics implements AiPayloadDiagnosticsListener {
        private final List<AiPayloadRequestEvent> requests = new ArrayList<>();
        private final List<AiPayloadResponseEvent> responses = new ArrayList<>();

        @Override
        public void requestPayload(AiPayloadRequestEvent event) {
            requests.add(event);
        }

        @Override
        public void responsePayload(AiPayloadResponseEvent event) {
            responses.add(event);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
