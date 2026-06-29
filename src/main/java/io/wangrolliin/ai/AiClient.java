package io.wangrolliin.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AiClient {
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public static Builder builder() {
        return new Builder();
    }

    public ChatResponse chat(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String requestBody = serialize(chatPayload(request, false));
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
            throw new AiException("Chat request failed with HTTP status "
                    + response.statusCode() + ": " + summarize(response.body()));
        }
        return parseChatResponse(response.body());
    }

    public ChatStream stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String requestBody = serialize(chatPayload(request, true));
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
            throw new AiException("Streaming chat request failed with HTTP status "
                    + response.statusCode() + ": " + summarize(readBody(response.body())));
        }
        return new ChatStream(response.body(), OBJECT_MAPPER);
    }

    private Map<String, Object> chatPayload(ChatRequest request, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        List<Map<String, String>> messages = request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        putIfPresent(payload, "max_tokens", request.maxTokens());
        if (!request.stopSequences().isEmpty()) {
            payload.put("stop", request.stopSequences());
        }
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private static void putIfPresent(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }

    private URI chatCompletionsUri() {
        return baseUri.resolve("chat/completions");
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize chat request", e);
        }
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

    private ChatResponse parseChatResponse(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            JsonNode content = choice.path("message").path("content");
            if (!content.isTextual()) {
                throw new AiException("Chat response did not contain choices[0].message.content");
            }
            return new ChatResponse(
                    content.asText(),
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(choice.path("finish_reason")));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat response", e);
        }
    }

    private static String optionalText(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
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

    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String defaultModel;
        private Duration timeout;
        private RetryPolicy retryPolicy;
        private HttpClient httpClient;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
            return this;
        }

        Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public AiClient build() {
            return new AiClient(this);
        }
    }
}
