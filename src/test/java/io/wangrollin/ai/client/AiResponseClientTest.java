package io.wangrollin.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiPayloadFailureEvent;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiPayloadResponseEvent;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseUsage;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResponseClientTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsResponseRequestAndParsesText() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {
                      "id": "resp_123",
                      "model": "provider-model",
                      "status": "completed",
                      "output_text": "Hello from responses",
                      "usage": {
                        "input_tokens": 2,
                        "output_tokens": 3,
                        "total_tokens": 5
                      }
                    }
                    """);
        });

        ResponseResult result = testClient().respond(ResponseRequest.builder()
                .input("Hello")
                .instructions("Answer briefly.")
                .temperature(0.2)
                .topP(0.9)
                .maxOutputTokens(64)
                .build());

        assertEquals(new ResponseResult(
                "Hello from responses",
                "resp_123",
                "provider-model",
                "completed",
                new ResponseUsage(2, 3, 5)), result);
        assertEquals("/responses", captured.get().path());
        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertEquals("test-model", requestJson.path("model").asText());
        assertEquals("Hello", requestJson.path("input").asText());
        assertEquals("Answer briefly.", requestJson.path("instructions").asText());
        assertEquals(64, requestJson.path("max_output_tokens").asInt());
    }

    @Test
    void responseRequestModelOverridesClientDefaultModel() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"output_text":"ok"}
                    """);
        });

        testClient().respond(ResponseRequest.builder()
                .model("request-model")
                .input("Hello")
                .build());

        assertEquals("request-model", OBJECT_MAPPER.readTree(captured.get().body()).path("model").asText());
    }

    @Test
    void sendsResponseStructuredOutputFormat() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respond(exchange, 200, """
                    {"output_text":"{\\"risk\\":\\"low\\"}"}
                    """);
        });

        testClient().respond(ResponseRequest.builder()
                .input("Summarize risk")
                .textFormat(ResponseTextFormat.jsonSchema("risk_summary", """
                        {
                          "type": "object",
                          "properties": {
                            "risk": { "type": "string" }
                          },
                          "required": ["risk"],
                          "additionalProperties": false
                        }
                        """))
                .build());

        JsonNode format = OBJECT_MAPPER.readTree(captured.get().body()).path("text").path("format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals("risk_summary", format.path("name").asText());
        assertEquals("object", format.path("schema").path("type").asText());
        assertTrue(format.path("strict").asBoolean());
    }

    @Test
    void throwsAiExceptionForResponseHttpErrors() throws Exception {
        startServer(exchange -> respond(exchange, 429, """
                {"error":{"message":"rate limited"}}
                """));

        AiException exception = assertThrows(AiException.class, () -> testClient().respond(ResponseRequest.builder()
                .input("Hello")
                .build()));

        assertEquals(429, exception.statusCode());
        assertTrue(exception.getMessage().contains("rate limited"));
    }

    @Test
    void retriesResponseStatusUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, """
                        {"error":{"message":"temporary"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"output_text":"ok after retry"}
                    """);
        });

        ResponseResult result = retryingClient(2).respond(ResponseRequest.builder()
                .input("Hello")
                .build());

        assertEquals("ok after retry", result.text());
        assertEquals(2, attempts.get());
    }

    @Test
    void payloadDiagnosticsAreRedactedForResponses() throws Exception {
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        startServer(exchange -> respond(exchange, 200, """
                {"output_text":"secret answer"}
                """));

        ResponseResult result = testClient(diagnostics).respond(ResponseRequest.builder()
                .input("secret prompt")
                .build());

        assertEquals("secret answer", result.text());
        String payloads = diagnostics.requests + " " + diagnostics.responses;
        assertFalse(payloads.contains("secret prompt"));
        assertFalse(payloads.contains("secret answer"));
        assertTrue(payloads.contains("<redacted>"));
    }

    @Test
    void streamsResponseDeltasAndSendsStreamRequest() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        startServer(exchange -> {
            captured.set(capture(exchange));
            respondStream(exchange, """
                    data: {"type":"response.output_text.delta","delta":"Hel"}

                    data: {"type":"response.output_text.delta","delta":"lo"}

                    data: {"type":"response.completed"}

                    """);
        });

        List<ResponseDelta> deltas = new ArrayList<>();
        try (ResponseStream stream = testClient().streamResponse(ResponseRequest.builder()
                .input("Hello")
                .build())) {
            for (ResponseDelta delta : stream) {
                deltas.add(delta);
            }
        }

        assertEquals(List.of(new ResponseDelta("Hel", false), new ResponseDelta("lo", false)), deltas);
        JsonNode requestJson = OBJECT_MAPPER.readTree(captured.get().body());
        assertTrue(requestJson.path("stream").asBoolean());
    }

    @Test
    void doesNotRetryAfterResponseStreamConsumptionStarts() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            attempts.incrementAndGet();
            respondStream(exchange, """
                    data: {"type":"response.output_text.delta",

                    """);
        });

        try (ResponseStream stream = retryingClient(3).streamResponse(ResponseRequest.builder()
                .input("Hello")
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertTrue(exception.getMessage().contains("Failed to parse response stream event"));
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

    private AiClient retryingClient(int maxAttempts) {
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
        server.createContext("/responses", exchange -> {
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
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
