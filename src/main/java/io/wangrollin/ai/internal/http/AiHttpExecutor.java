package io.wangrollin.ai.internal.http;

import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiPayloadFailureEvent;
import io.wangrollin.ai.diagnostic.AiPayloadRequestEvent;
import io.wangrollin.ai.diagnostic.AiPayloadResponseEvent;
import io.wangrollin.ai.diagnostic.AiRedactionPolicy;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Shared HTTP execution path for SDK operations.
 *
 * <p>This class owns retry, lifecycle event emission, and opt-in redacted
 * payload diagnostics so future provider endpoints can reuse the same
 * production behavior without duplicating transport control flow.
 */
public final class AiHttpExecutor {
    private final HttpClient httpClient;
    private final RetryPolicy retryPolicy;
    private final AiEventListener eventListener;
    private final AiPayloadDiagnosticsListener payloadDiagnosticsListener;
    private final AiRedactionPolicy redactionPolicy;
    private final URI baseUri;
    private final String path;

    public AiHttpExecutor(
            HttpClient httpClient,
            RetryPolicy retryPolicy,
            AiEventListener eventListener,
            AiPayloadDiagnosticsListener payloadDiagnosticsListener,
            AiRedactionPolicy redactionPolicy,
            URI baseUri,
            String path) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener must not be null");
        this.payloadDiagnosticsListener = Objects.requireNonNull(
                payloadDiagnosticsListener,
                "payloadDiagnosticsListener must not be null");
        this.redactionPolicy = Objects.requireNonNull(redactionPolicy, "redactionPolicy must not be null");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        this.path = requireText(path, "path");
    }

    public <T> AiHttpResult<T> send(AiHttpRequestSpec spec, HttpResponse.BodyHandler<T> bodyHandler) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(bodyHandler, "bodyHandler must not be null");
        emitPayloadRequest(spec.operation(), spec.model(), spec.stream(), spec.payloadBody());
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            sleepBeforeRetry(attempt, spec.requestDescription());
            emitStarted(spec.operation(), spec.model(), spec.stream(), attempt);
            long startNanos = System.nanoTime();
            try {
                HttpResponse<T> response = httpClient.send(spec.request(), bodyHandler);
                Duration duration = elapsedSince(startNanos);
                if (!shouldRetryResponse(response.statusCode(), attempt)) {
                    return new AiHttpResult<>(response, attempt, duration);
                }
                emitRetriedPayloadFailure(spec.operation(), spec.model(), spec.stream(), response.statusCode(), response.body());
                emitFailure(
                        spec.operation(),
                        spec.model(),
                        spec.stream(),
                        attempt,
                        response.statusCode(),
                        duration,
                        retryableStatusException(capitalize(spec.requestDescription()), response.statusCode()));
                closeRetriedBody(response.body());
            } catch (IOException e) {
                Duration duration = elapsedSince(startNanos);
                AiException exception = new AiException("Failed to send " + spec.requestDescription(), e);
                emitFailure(spec.operation(), spec.model(), spec.stream(), attempt, null, duration, exception);
                if (attempt == retryPolicy.maxAttempts()) {
                    throw exception;
                }
            } catch (InterruptedException e) {
                Duration duration = elapsedSince(startNanos);
                Thread.currentThread().interrupt();
                AiException exception = new AiException(capitalize(spec.requestDescription()) + " was interrupted", e);
                emitFailure(spec.operation(), spec.model(), spec.stream(), attempt, null, duration, exception);
                throw exception;
            }
        }
        throw new AiException("Failed to send " + spec.requestDescription());
    }

    public void emitSuccess(
            String operation,
            String model,
            boolean stream,
            int attempt,
            int statusCode,
            Duration duration,
            String finishReason,
            ChatUsage usage) {
        eventListener.requestSucceeded(new AiResponseEvent(
                operation,
                model,
                baseUri,
                path,
                stream,
                attempt,
                statusCode,
                duration,
                finishReason,
                usage));
    }

    public void emitFailure(
            String operation,
            String model,
            boolean stream,
            int attempt,
            Integer statusCode,
            Duration duration,
            RuntimeException exception) {
        eventListener.requestFailed(new AiFailureEvent(
                operation,
                model,
                baseUri,
                path,
                stream,
                attempt,
                statusCode,
                duration,
                exception.getClass().getName(),
                safeMessage(exception)));
    }

    public void emitPayloadResponse(String operation, String model, boolean stream, int statusCode, String body) {
        if (!payloadDiagnosticsEnabled()) {
            return;
        }
        payloadDiagnosticsListener.responsePayload(new AiPayloadResponseEvent(
                operation,
                model,
                baseUri,
                path,
                stream,
                statusCode,
                redactionPolicy.redactJson(body)));
    }

    public void emitPayloadFailure(String operation, String model, boolean stream, int statusCode, String body) {
        if (!payloadDiagnosticsEnabled()) {
            return;
        }
        payloadDiagnosticsListener.failurePayload(new AiPayloadFailureEvent(
                operation,
                model,
                baseUri,
                path,
                stream,
                statusCode,
                redactionPolicy.redactJson(body)));
    }

    public static String readBody(InputStream body) {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiException("Failed to read error response body", e);
        }
    }

    public static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private void emitStarted(String operation, String model, boolean stream, int attempt) {
        eventListener.requestStarted(new AiRequestEvent(operation, model, baseUri, path, stream, attempt));
    }

    private void emitPayloadRequest(String operation, String model, boolean stream, String body) {
        if (!payloadDiagnosticsEnabled()) {
            return;
        }
        payloadDiagnosticsListener.requestPayload(new AiPayloadRequestEvent(
                operation,
                model,
                baseUri,
                path,
                stream,
                redactionPolicy.redactJson(body)));
    }

    private void emitRetriedPayloadFailure(String operation, String model, boolean stream, int statusCode, Object body) {
        if (!payloadDiagnosticsEnabled()) {
            return;
        }
        if (body instanceof InputStream inputStream) {
            emitPayloadFailure(operation, model, stream, statusCode, readBody(inputStream));
            return;
        }
        if (body instanceof String text) {
            emitPayloadFailure(operation, model, stream, statusCode, text);
        }
    }

    private boolean payloadDiagnosticsEnabled() {
        return payloadDiagnosticsListener != AiPayloadDiagnosticsListener.NOOP;
    }

    private boolean shouldRetryResponse(int statusCode, int attempt) {
        return attempt < retryPolicy.maxAttempts() && retryPolicy.shouldRetryStatus(statusCode);
    }

    private void sleepBeforeRetry(int attempt, String requestDescription) {
        Duration delay = retryPolicy.delayForAttempt(attempt);
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(capitalize(requestDescription) + " was interrupted", e);
        }
    }

    private static void closeRetriedBody(Object body) {
        if (body instanceof InputStream inputStream) {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new AiException("Failed to close retry response body", e);
            }
        }
    }

    private static AiException retryableStatusException(String requestDescription, int statusCode) {
        return new AiException(requestDescription + " failed with retryable HTTP status " + statusCode, statusCode, null);
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception instanceof AiException aiException && aiException.statusCode() != null) {
            return "HTTP status " + aiException.statusCode();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
