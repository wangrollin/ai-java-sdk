package io.wangrollin.ai.event;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.error.AiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEventListenerTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void emitsSafeChatSuccessEvents() throws Exception {
        RecordingAiEventListener listener = new RecordingAiEventListener();
        startServer(exchange -> respond(exchange, 200, """
                {
                  "model": "provider-model",
                  "usage": {
                    "prompt_tokens": 3,
                    "completion_tokens": 4,
                    "total_tokens": 7
                  },
                  "choices": [
                    {
                      "finish_reason": "stop",
                      "message": { "content": "secret answer" }
                    }
                  ]
                }
                """));

        ChatResponse response = testClient(listener).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build());

        assertEquals("secret answer", response.text());
        assertEquals(1, listener.started.size());
        assertEquals(1, listener.succeeded.size());
        assertEquals("chat", listener.started.get(0).operation());
        assertEquals("test-model", listener.started.get(0).model());
        assertEquals("/chat/completions", listener.started.get(0).path());
        assertFalse(listener.started.get(0).stream());
        assertEquals(1, listener.started.get(0).attempt());

        AiResponseEvent event = listener.succeeded.get(0);
        assertEquals("chat", event.operation());
        assertEquals("provider-model", event.model());
        assertEquals(200, event.statusCode());
        assertEquals("stop", event.finishReason());
        assertEquals(new ChatUsage(3, 4, 7), event.usage());
        assertFalse(event.duration().isNegative());
        assertTrue(listener.failed.isEmpty());
    }

    @Test
    void emitsEventsForRetryableStatusesUntilSuccess() throws Exception {
        RecordingAiEventListener listener = new RecordingAiEventListener();
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, """
                        {"error":{"message":"raw upstream detail"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"ok"}}]}
                    """);
        });

        ChatResponse response = retryingTestClient(listener, 2).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok", response.text());
        assertEquals(List.of(1, 2), listener.started.stream().map(AiRequestEvent::attempt).toList());
        assertEquals(1, listener.failed.size());
        assertEquals(503, listener.failed.get(0).statusCode());
        assertEquals("HTTP status 503", listener.failed.get(0).message());
        assertEquals(1, listener.succeeded.size());
        assertEquals(2, listener.succeeded.get(0).attempt());
    }

    @Test
    void inMemoryMetricsCollectsEventsFromRealClientRetries() throws Exception {
        InMemoryAiMetricsListener metrics = InMemoryAiMetricsListener.create();
        AtomicInteger attempts = new AtomicInteger();
        startServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, """
                        {"error":{"message":"raw upstream detail"}}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {
                      "model": "provider-model",
                      "usage": {
                        "prompt_tokens": 2,
                        "completion_tokens": 5,
                        "total_tokens": 7
                      },
                      "choices":[{"message":{"content":"ok"}}]
                    }
                    """);
        });

        ChatResponse response = retryingTestClient(metrics, 2).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build());

        AiMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals("ok", response.text());
        assertEquals(2, snapshot.startedCount());
        assertEquals(1, snapshot.succeededCount());
        assertEquals(1, snapshot.failedCount());
        assertEquals(2, snapshot.startedByOperation().get("chat"));
        assertEquals(1, snapshot.terminalByStatusCode().get(503));
        assertEquals(1, snapshot.terminalByStatusCode().get(200));
        assertEquals(1, snapshot.terminalByModel().get("test-model"));
        assertEquals(1, snapshot.terminalByModel().get("provider-model"));
        assertEquals(2, snapshot.promptTokens());
        assertEquals(5, snapshot.completionTokens());
        assertEquals(7, snapshot.totalTokens());
    }

    @Test
    void emitsSanitizedFailureForFinalProviderError() throws Exception {
        RecordingAiEventListener listener = new RecordingAiEventListener();
        startServer(exchange -> respond(exchange, 502, "raw upstream unavailable"));

        AiException exception = assertThrows(AiException.class, () -> testClient(listener).chat(ChatRequest.builder()
                .message(ChatMessage.user("secret prompt"))
                .build()));

        assertTrue(exception.getMessage().contains("raw upstream unavailable"));
        assertEquals(1, listener.failed.size());
        AiFailureEvent failure = listener.failed.get(0);
        assertEquals("chat", failure.operation());
        assertEquals(502, failure.statusCode());
        assertEquals("HTTP status 502", failure.message());
        assertFalse(failure.message().contains("raw upstream"));
        assertTrue(listener.succeeded.isEmpty());
    }

    @Test
    void emitsFailureForTransportIOException() {
        RecordingAiEventListener listener = new RecordingAiEventListener();
        AiClient client = AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:1")
                .defaultModel("test-model")
                .eventListener(listener)
                .httpClient(new ThrowingHttpClient(new IOException("socket unavailable")))
                .build();

        AiException exception = assertThrows(AiException.class, () -> client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build()));

        assertTrue(exception.getMessage().contains("Failed to send chat request"));
        assertEquals(1, listener.started.size());
        assertEquals(1, listener.failed.size());
        assertNull(listener.failed.get(0).statusCode());
        assertEquals(AiException.class.getName(), listener.failed.get(0).exceptionType());
        assertTrue(listener.failed.get(0).message().contains("Failed to send chat request"));
    }

    @Test
    void emitsStreamSuccessAndConsumptionFailure() throws Exception {
        RecordingAiEventListener listener = new RecordingAiEventListener();
        startServer(exchange -> respondStream(exchange, """
                data: {"choices":[

                """));

        try (ChatStream stream = testClient(listener).stream(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build())) {
            AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

            assertTrue(exception.getMessage().contains("Failed to parse chat stream event"));
        }

        assertEquals(1, listener.started.size());
        assertTrue(listener.started.get(0).stream());
        assertEquals(1, listener.succeeded.size());
        assertEquals("stream", listener.succeeded.get(0).operation());
        assertEquals(1, listener.failed.size());
        assertEquals(200, listener.failed.get(0).statusCode());
        assertTrue(listener.failed.get(0).message().contains("Failed to parse chat stream event"));
    }

    @Test
    void defaultNoopListenerDoesNotChangeBehavior() throws Exception {
        startServer(exchange -> respond(exchange, 200, """
                {"choices":[{"message":{"content":"ok"}}]}
                """));

        ChatResponse response = testClient(null).chat(ChatRequest.builder()
                .message(ChatMessage.user("Hello"))
                .build());

        assertEquals("ok", response.text());
    }

    private AiClient testClient(RecordingAiEventListener listener) {
        AiClient.Builder builder = AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5));
        if (listener != null) {
            builder.eventListener(listener);
        }
        return builder.build();
    }

    private AiClient retryingTestClient(AiEventListener listener, int maxAttempts) {
        return AiClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .defaultModel("test-model")
                .timeout(Duration.ofSeconds(5))
                .eventListener(listener)
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

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class RecordingAiEventListener implements AiEventListener {
        private final List<AiRequestEvent> started = new ArrayList<>();
        private final List<AiResponseEvent> succeeded = new ArrayList<>();
        private final List<AiFailureEvent> failed = new ArrayList<>();

        @Override
        public void requestStarted(AiRequestEvent event) {
            started.add(event);
        }

        @Override
        public void requestSucceeded(AiResponseEvent event) {
            succeeded.add(event);
        }

        @Override
        public void requestFailed(AiFailureEvent event) {
            failed.add(event);
        }
    }

    private static final class ThrowingHttpClient extends HttpClient {
        private final IOException failure;

        private ThrowingHttpClient(IOException failure) {
            this.failure = failure;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            throw failure;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(failure);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
