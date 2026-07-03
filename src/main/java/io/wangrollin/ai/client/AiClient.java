package io.wangrollin.ai.client;

import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI-compatible HTTP implementation of {@link AiChatClient}.
 *
 * <p>The client owns HTTP request construction, retry handling, streaming
 * response parsing, and safe lifecycle event emission. Provider-specific JSON
 * details stay inside the internal OpenAI adapter so application code can
 * depend on the smaller {@link AiChatClient} contract where possible.
 */
public final class AiClient implements AiChatClient {
    /**
     * Default OpenAI-compatible API base URL.
     */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /**
     * Default request and connection timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final OpenAiChatCodec OPEN_AI_CODEC = new OpenAiChatCodec();

    private final String apiKey;
    private final URI baseUri;
    private final String defaultModel;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;
    private final AiEventListener eventListener;
    private final HttpClient httpClient;

    private AiClient(Builder builder) {
        this.apiKey = requireText(builder.apiKey, "apiKey");
        this.baseUri = normalizeBaseUri(builder.baseUrl == null ? DEFAULT_BASE_URL : builder.baseUrl);
        this.defaultModel = requireText(builder.defaultModel, "defaultModel");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        if (this.timeout.isZero() || this.timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.retryPolicy = builder.retryPolicy == null ? RetryPolicy.none() : builder.retryPolicy;
        this.eventListener = builder.eventListener == null ? AiEventListener.NOOP : builder.eventListener;
        this.httpClient = builder.httpClient == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).build()
                : builder.httpClient;
    }

    /**
     * Starts building an HTTP AI client.
     *
     * @return client builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String model = modelFor(request);
        String requestBody = OPEN_AI_CODEC.serializeRequest(request, defaultModel, false);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri())
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        AttemptResult<String> result = sendWithRetry(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(),
                "chat request",
                "chat",
                model,
                false);
        HttpResponse<String> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            AiException exception = providerResponseException("Chat request", response.statusCode(), response.body());
            emitFailure("chat", model, false, result.attempt(), response.statusCode(), result.duration(), exception);
            throw exception;
        }
        try {
            ChatResponse chatResponse = OPEN_AI_CODEC.parseResponse(response.body());
            emitSuccess(
                    "chat",
                    eventModel(model, chatResponse.model()),
                    false,
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    chatResponse.finishReason(),
                    chatResponse.usage());
            return chatResponse;
        } catch (AiException e) {
            emitFailure("chat", model, false, result.attempt(), response.statusCode(), result.duration(), e);
            throw e;
        }
    }

    @Override
    public ChatStream stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String model = modelFor(request);
        String requestBody = OPEN_AI_CODEC.serializeRequest(request, defaultModel, true);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri())
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        AttemptResult<InputStream> result = sendWithRetry(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream(),
                "streaming chat request",
                "stream",
                model,
                true);
        HttpResponse<InputStream> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            AiException exception = providerResponseException(
                    "Streaming chat request",
                    response.statusCode(),
                    readBody(response.body()));
            emitFailure("stream", model, true, result.attempt(), response.statusCode(), result.duration(), exception);
            throw exception;
        }
        emitSuccess("stream", model, true, result.attempt(), response.statusCode(), result.duration(), null, null);
        long streamOpenNanos = System.nanoTime();
        return new ChatStream(
                response.body(),
                OPEN_AI_CODEC::parseStreamDelta,
                failure -> emitFailure(
                        "stream",
                        model,
                        true,
                        result.attempt(),
                        response.statusCode(),
                        result.duration().plus(elapsedSince(streamOpenNanos)),
                        failure));
    }

    private URI chatCompletionsUri() {
        return baseUri.resolve(OpenAiChatCodec.CHAT_COMPLETIONS_PATH);
    }

    private <T> AttemptResult<T> sendWithRetry(
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler,
            String requestDescription,
            String operation,
            String model,
            boolean stream) {
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            sleepBeforeRetry(attempt, requestDescription);
            emitStarted(operation, model, stream, attempt);
            long startNanos = System.nanoTime();
            try {
                HttpResponse<T> response = httpClient.send(request, bodyHandler);
                Duration duration = elapsedSince(startNanos);
                if (!shouldRetryResponse(response.statusCode(), attempt)) {
                    return new AttemptResult<>(response, attempt, duration);
                }
                emitFailure(operation, model, stream, attempt, response.statusCode(), duration, retryableStatusException(
                        capitalize(requestDescription),
                        response.statusCode()));
                closeRetriedBody(response.body());
            } catch (IOException e) {
                Duration duration = elapsedSince(startNanos);
                AiException exception = new AiException("Failed to send " + requestDescription, e);
                emitFailure(operation, model, stream, attempt, null, duration, exception);
                if (attempt == retryPolicy.maxAttempts()) {
                    throw exception;
                }
            } catch (InterruptedException e) {
                Duration duration = elapsedSince(startNanos);
                Thread.currentThread().interrupt();
                AiException exception = new AiException(capitalize(requestDescription) + " was interrupted", e);
                emitFailure(operation, model, stream, attempt, null, duration, exception);
                throw exception;
            }
        }
        throw new AiException("Failed to send " + requestDescription);
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

    private boolean shouldRetryResponse(int statusCode, int attempt) {
        return attempt < retryPolicy.maxAttempts() && retryPolicy.shouldRetryStatus(statusCode);
    }

    private static AiException retryableStatusException(String requestDescription, int statusCode) {
        return new AiException(requestDescription + " failed with retryable HTTP status " + statusCode, statusCode, null);
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

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String modelFor(ChatRequest request) {
        return request.model() == null ? defaultModel : request.model();
    }

    private String eventModel(String requestModel, String responseModel) {
        return responseModel == null ? requestModel : responseModel;
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private void emitStarted(String operation, String model, boolean stream, int attempt) {
        eventListener.requestStarted(new AiRequestEvent(
                operation,
                model,
                baseUri,
                "/" + OpenAiChatCodec.CHAT_COMPLETIONS_PATH,
                stream,
                attempt));
    }

    private void emitSuccess(
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
                "/" + OpenAiChatCodec.CHAT_COMPLETIONS_PATH,
                stream,
                attempt,
                statusCode,
                duration,
                finishReason,
                usage));
    }

    private void emitFailure(
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
                "/" + OpenAiChatCodec.CHAT_COMPLETIONS_PATH,
                stream,
                attempt,
                statusCode,
                duration,
                exception.getClass().getName(),
                safeMessage(exception)));
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception instanceof AiException aiException && aiException.statusCode() != null) {
            return "HTTP status " + aiException.statusCode();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static URI normalizeBaseUri(String baseUrl) {
        String value = requireText(baseUrl, "baseUrl");
        String withTrailingSlash = value.endsWith("/") ? value : value + "/";
        return URI.create(withTrailingSlash);
    }

    private static String requireText(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "<empty body>";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500) + "...";
    }

    private static String readBody(InputStream body) {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiException("Failed to read error response body", e);
        }
    }

    private static AiException providerResponseException(String requestDescription, int statusCode, String body) {
        AiError error = OPEN_AI_CODEC.parseError(body);
        String detail = error != null && error.message() != null ? error.message() : summarize(body);
        return new AiException(
                requestDescription + " failed with HTTP status " + statusCode + ": " + detail,
                statusCode,
                error);
    }

    /**
     * Builder for {@link AiClient}.
     */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String defaultModel;
        private Duration timeout;
        private RetryPolicy retryPolicy;
        private AiEventListener eventListener;
        private HttpClient httpClient;

        private Builder() {
        }

        /**
         * Sets the provider API key used for bearer authentication.
         *
         * @param apiKey API key value; do not commit real keys to source control
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenAI-compatible base URL.
         *
         * @param baseUrl provider base URL, with or without a trailing slash
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the model used when a request does not provide its own model.
         *
         * @param defaultModel provider model name
         * @return this builder
         */
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /**
         * Sets the HTTP connection and request timeout.
         *
         * @param timeout positive timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets retry behavior for transient response statuses and send failures.
         *
         * @param retryPolicy retry policy to use
         * @return this builder
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
            return this;
        }

        /**
         * Sets a listener for safe request lifecycle events.
         *
         * <p>Events intentionally expose metadata such as operation, model,
         * status, duration, and attempt number; they do not include API keys,
         * prompts, generated text, tool arguments, or raw provider bodies.
         *
         * @param eventListener listener to receive request diagnostics
         * @return this builder
         */
        public Builder eventListener(AiEventListener eventListener) {
            this.eventListener = Objects.requireNonNull(eventListener, "eventListener must not be null");
            return this;
        }

        /**
         * Sets the HTTP client used by this SDK client.
         *
         * <p>This hook is useful for tests and applications that need a
         * preconfigured JDK HTTP client, for example to customize proxy,
         * executor, authenticator, or TLS behavior.
         *
         * @param httpClient HTTP client to use for requests
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            return this;
        }

        /**
         * Builds the client after validating required configuration.
         *
         * @return configured AI client
         */
        public AiClient build() {
            return new AiClient(this);
        }
    }

    private record AttemptResult<T>(HttpResponse<T> response, int attempt, Duration duration) {
    }
}
