package io.wangrollin.ai;

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
        String requestBody = OPEN_AI_CODEC.serializeRequest(request, defaultModel, false);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri())
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = sendWithRetry(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(),
                "chat request");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw providerResponseException("Chat request", response.statusCode(), response.body());
        }
        return OPEN_AI_CODEC.parseResponse(response.body());
    }

    @Override
    public ChatStream stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String requestBody = OPEN_AI_CODEC.serializeRequest(request, defaultModel, true);
        HttpRequest httpRequest = HttpRequest.newBuilder(chatCompletionsUri())
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<InputStream> response = sendWithRetry(
                httpRequest,
                HttpResponse.BodyHandlers.ofInputStream(),
                "streaming chat request");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw providerResponseException("Streaming chat request", response.statusCode(), readBody(response.body()));
        }
        return new ChatStream(response.body(), OPEN_AI_CODEC);
    }

    private URI chatCompletionsUri() {
        return baseUri.resolve(OpenAiChatCodec.CHAT_COMPLETIONS_PATH);
    }

    private <T> HttpResponse<T> sendWithRetry(
            HttpRequest request,
            HttpResponse.BodyHandler<T> bodyHandler,
            String requestDescription) {
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            sleepBeforeRetry(attempt, requestDescription);
            try {
                HttpResponse<T> response = httpClient.send(request, bodyHandler);
                if (!shouldRetryResponse(response.statusCode(), attempt)) {
                    return response;
                }
                closeRetriedBody(response.body());
            } catch (IOException e) {
                if (attempt == retryPolicy.maxAttempts()) {
                    throw new AiException("Failed to send " + requestDescription, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException(capitalize(requestDescription) + " was interrupted", e);
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

        Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
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
}
