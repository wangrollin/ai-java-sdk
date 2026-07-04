package io.wangrollin.ai.internal.http;

import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiPayloadFailureEvent;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiRedactionPolicy;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHttpExecutorTest {
    private static final URI BASE_URI = URI.create("https://example.test/v1/");
    private static final String PATH = "/chat/completions";

    @Test
    void retriesRetryableStatusesAndEmitsSafeEvents() {
        RecordingAiEventListener events = new RecordingAiEventListener();
        RecordingPayloadDiagnosticsListener diagnostics = new RecordingPayloadDiagnosticsListener();
        AiHttpExecutor executor = executor(
                new SequencedHttpClient(
                        new SimpleResponse<>(429, "{\"error\":{\"message\":\"secret limit\",\"code\":\"rate_limit\"}}"),
                        new SimpleResponse<>(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")),
                events,
                diagnostics,
                retryPolicy(2));

        AiHttpResult<String> result = executor.send(spec(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, result.response().statusCode());
        assertEquals(2, result.attempt());
        assertEquals(2, events.started.size());
        assertEquals(1, events.failed.size());
        assertEquals("HTTP status 429", events.failed.get(0).message());
        assertEquals(1, diagnostics.requests.size());
        assertEquals(1, diagnostics.failures.size());
        assertFalse(diagnostics.failures.get(0).redactedBody().contains("secret limit"));
        assertTrue(diagnostics.failures.get(0).redactedBody().contains("rate_limit"));
    }

    @Test
    void closesRetryInputStreamBodies() {
        CloseTrackingInputStream retryBody = new CloseTrackingInputStream("{\"error\":{\"message\":\"retry\"}}");
        AiHttpExecutor executor = executor(
                new SequencedHttpClient(
                        new SimpleResponse<>(503, retryBody),
                        new SimpleResponse<>(200, new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)))),
                new RecordingAiEventListener(),
                new RecordingPayloadDiagnosticsListener(),
                retryPolicy(2));

        AiHttpResult<ByteArrayInputStream> result = executor.send(spec(), responseInfo -> HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofInputStream(),
                input -> {
                    try {
                        return new ByteArrayInputStream(input.readAllBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));

        assertEquals(200, result.response().statusCode());
        assertTrue(retryBody.closed());
    }

    @Test
    void emitsFailureForTransportErrorAndStopsAtAttemptLimit() {
        RecordingAiEventListener events = new RecordingAiEventListener();
        IOException failure = new IOException("socket unavailable");
        AiHttpExecutor executor = executor(
                new FailingHttpClient(failure),
                events,
                new RecordingPayloadDiagnosticsListener(),
                retryPolicy(1));

        AiException exception = assertThrows(AiException.class, () ->
                executor.send(spec(), HttpResponse.BodyHandlers.ofString()));

        assertTrue(exception.getMessage().contains("Failed to send chat request"));
        assertSame(failure, exception.getCause());
        assertEquals(1, events.started.size());
        assertEquals(1, events.failed.size());
        assertEquals(AiException.class.getName(), events.failed.get(0).exceptionType());
    }

    private static AiHttpExecutor executor(
            HttpClient httpClient,
            AiEventListener eventListener,
            AiPayloadDiagnosticsListener diagnosticsListener,
            RetryPolicy retryPolicy) {
        return new AiHttpExecutor(
                httpClient,
                retryPolicy,
                eventListener,
                diagnosticsListener,
                AiRedactionPolicy.defaultPolicy(),
                BASE_URI,
                PATH);
    }

    private static RetryPolicy retryPolicy(int maxAttempts) {
        return RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .retryableStatusCodes(Set.of(429, 500, 502, 503, 504))
                .build();
    }

    private static AiHttpRequestSpec spec() {
        HttpRequest request = HttpRequest.newBuilder(BASE_URI.resolve("chat/completions"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"messages\":[{\"content\":\"secret prompt\"}]}"))
                .build();
        return new AiHttpRequestSpec(
                request,
                "chat request",
                "chat",
                "test-model",
                false,
                "{\"messages\":[{\"content\":\"secret prompt\"}]}");
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

    private static final class RecordingPayloadDiagnosticsListener implements AiPayloadDiagnosticsListener {
        private final List<AiPayloadRequestEvent> requests = new ArrayList<>();
        private final List<AiPayloadFailureEvent> failures = new ArrayList<>();

        @Override
        public void requestPayload(AiPayloadRequestEvent event) {
            requests.add(event);
        }

        @Override
        public void failurePayload(AiPayloadFailureEvent event) {
            failures.add(event);
        }
    }

    private static final class SequencedHttpClient extends BaseHttpClient {
        private final List<HttpResponse<?>> responses;
        private int index;

        private SequencedHttpClient(HttpResponse<?>... responses) {
            this.responses = List.of(responses);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return (HttpResponse<T>) responses.get(index++);
        }
    }

    private static final class FailingHttpClient extends BaseHttpClient {
        private final IOException failure;

        private FailingHttpClient(IOException failure) {
            this.failure = failure;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            throw failure;
        }
    }

    private abstract static class BaseHttpClient extends HttpClient {
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
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record SimpleResponse<T>(int statusCode, T body) implements HttpResponse<T> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public URI uri() {
            return BASE_URI;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
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
