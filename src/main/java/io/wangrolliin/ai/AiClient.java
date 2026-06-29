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
    private final HttpClient httpClient;

    private AiClient(Builder builder) {
        this.apiKey = requireText(builder.apiKey, "apiKey");
        this.baseUri = normalizeBaseUri(builder.baseUrl == null ? DEFAULT_BASE_URL : builder.baseUrl);
        this.defaultModel = requireText(builder.defaultModel, "defaultModel");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        if (this.timeout.isZero() || this.timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
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

        HttpResponse<String> response = send(httpRequest);
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

        HttpResponse<InputStream> response = sendStream(httpRequest);
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
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
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

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AiException("Failed to send chat request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Chat request was interrupted", e);
        }
    }

    private HttpResponse<InputStream> sendStream(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new AiException("Failed to send streaming chat request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Streaming chat request was interrupted", e);
        }
    }

    private ChatResponse parseChatResponse(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new AiException("Chat response did not contain choices[0].message.content");
            }
            return new ChatResponse(content.asText());
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat response", e);
        }
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

        Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public AiClient build() {
            return new AiClient(this);
        }
    }
}
