package io.wangrollin.ai.client;

import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiRedactionPolicy;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.internal.http.AiHttpExecutor;
import io.wangrollin.ai.internal.http.AiHttpRequestSpec;
import io.wangrollin.ai.internal.http.AiHttpResult;
import io.wangrollin.ai.internal.provider.AiProviderAdapter;
import io.wangrollin.ai.internal.provider.AnthropicProviderAdapter;
import io.wangrollin.ai.internal.provider.OpenAiCompatibleProviderAdapter;
import io.wangrollin.ai.internal.provider.ProviderRequestSpec;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP implementation of {@link AiChatClient}.
 *
 * <p>The client owns HTTP request construction, retry handling, streaming
 * response parsing, and safe lifecycle event emission. Provider-specific JSON
 * details stay inside an internal provider adapter so application code can
 * depend on the smaller {@link AiChatClient} contract where possible.
 */
public final class AiClient implements AiChatClient, AiResponseClient {
    /**
     * Default OpenAI-compatible API base URL.
     */
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /**
     * Default request and connection timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private static final String API_KEY_HEADER_PLACEHOLDER = "{apiKey}";

    private final String apiKey;
    private final URI baseUri;
    private final String defaultModel;
    private final Duration timeout;
    private final AiProviderAdapter providerAdapter;
    private final AiHttpExecutor httpExecutor;

    private AiClient(Builder builder) {
        this.apiKey = requireText(builder.apiKey, "apiKey");
        AiProviderPreset providerPreset = builder.providerPreset == null ? AiProviderPreset.OPENAI : builder.providerPreset;
        this.baseUri = normalizeBaseUri(builder.baseUrl);
        this.defaultModel = requireText(builder.defaultModel, "defaultModel");
        this.timeout = builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout;
        if (this.timeout.isZero() || this.timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.providerAdapter = providerAdapter(builder.provider == null ? providerPreset.provider() : builder.provider);
        RetryPolicy retryPolicy = builder.retryPolicy == null ? RetryPolicy.none() : builder.retryPolicy;
        AiEventListener eventListener = builder.eventListener == null ? AiEventListener.NOOP : builder.eventListener;
        AiPayloadDiagnosticsListener payloadDiagnosticsListener = builder.payloadDiagnosticsListener == null
                ? AiPayloadDiagnosticsListener.NOOP
                : builder.payloadDiagnosticsListener;
        AiRedactionPolicy redactionPolicy = builder.redactionPolicy == null
                ? AiRedactionPolicy.defaultPolicy()
                : builder.redactionPolicy;
        HttpClient httpClient = builder.httpClient == null
                ? HttpClient.newBuilder().connectTimeout(this.timeout).build()
                : builder.httpClient;
        this.httpExecutor = new AiHttpExecutor(
                httpClient,
                retryPolicy,
                eventListener,
                payloadDiagnosticsListener,
                redactionPolicy,
                baseUri);
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
        ProviderRequestSpec providerRequest = providerAdapter.chatRequest(request, defaultModel, false);
        HttpRequest.Builder httpRequestBuilder = providerHttpRequestBuilder(providerRequest)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(providerRequest.body()));
        HttpRequest httpRequest = httpRequestBuilder.build();

        AiHttpResult<String> result = httpExecutor.send(new AiHttpRequestSpec(
                httpRequest,
                "chat request",
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                providerRequest.body()), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            httpExecutor.emitPayloadFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    response.body());
            AiException exception = providerResponseException("Chat request", response.statusCode(), response.body());
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    exception);
            throw exception;
        }
        try {
            httpExecutor.emitPayloadResponse(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    response.body());
            ChatResponse chatResponse = providerAdapter.parseChatResponse(response.body());
            httpExecutor.emitSuccess(
                    providerRequest.operation(),
                    eventModel(providerRequest.model(), chatResponse.model()),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    chatResponse.finishReason(),
                    chatResponse.usage());
            return chatResponse;
        } catch (AiException e) {
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    e);
            throw e;
        }
    }

    @Override
    public ChatStream stream(ChatRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ProviderRequestSpec providerRequest = providerAdapter.chatRequest(request, defaultModel, true);
        HttpRequest.Builder httpRequestBuilder = providerHttpRequestBuilder(providerRequest)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(providerRequest.body()));
        HttpRequest httpRequest = httpRequestBuilder.build();

        AiHttpResult<InputStream> result = httpExecutor.send(new AiHttpRequestSpec(
                httpRequest,
                "streaming chat request",
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                providerRequest.body()), HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = AiHttpExecutor.readBody(response.body());
            httpExecutor.emitPayloadFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    errorBody);
            AiException exception = providerResponseException(
                    "Streaming chat request",
                    response.statusCode(),
                    errorBody);
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    exception);
            throw exception;
        }
        httpExecutor.emitSuccess(
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                result.attempt(),
                response.statusCode(),
                result.duration(),
                null,
                null);
        long streamOpenNanos = System.nanoTime();
        return new ChatStream(
                response.body(),
                (event, data) -> providerAdapter.parseChatStreamDelta(event, data),
                failure -> httpExecutor.emitFailure(
                        providerRequest.operation(),
                        providerRequest.model(),
                        providerRequest.path(),
                        providerRequest.stream(),
                        result.attempt(),
                        response.statusCode(),
                        result.duration().plus(AiHttpExecutor.elapsedSince(streamOpenNanos)),
                        failure));
    }

    @Override
    public ResponseResult respond(ResponseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ProviderRequestSpec providerRequest = providerAdapter.responseRequest(request, defaultModel, false);
        HttpRequest.Builder httpRequestBuilder = providerHttpRequestBuilder(providerRequest)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(providerRequest.body()));
        HttpRequest httpRequest = httpRequestBuilder.build();

        AiHttpResult<String> result = httpExecutor.send(new AiHttpRequestSpec(
                httpRequest,
                "response request",
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                providerRequest.body()), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            httpExecutor.emitPayloadFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    response.body());
            AiException exception = providerResponseException("Response request", response.statusCode(), response.body());
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    exception);
            throw exception;
        }
        try {
            httpExecutor.emitPayloadResponse(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    response.body());
            ResponseResult responseResult = providerAdapter.parseResponseResult(response.body());
            httpExecutor.emitSuccess(
                    providerRequest.operation(),
                    responseResult.model() == null ? providerRequest.model() : responseResult.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    responseResult.status(),
                    null);
            return responseResult;
        } catch (AiException e) {
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    e);
            throw e;
        }
    }

    @Override
    public ResponseStream streamResponse(ResponseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ProviderRequestSpec providerRequest = providerAdapter.responseRequest(request, defaultModel, true);
        HttpRequest.Builder httpRequestBuilder = providerHttpRequestBuilder(providerRequest)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(providerRequest.body()));
        HttpRequest httpRequest = httpRequestBuilder.build();

        AiHttpResult<InputStream> result = httpExecutor.send(new AiHttpRequestSpec(
                httpRequest,
                "streaming response request",
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                providerRequest.body()), HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response = result.response();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = AiHttpExecutor.readBody(response.body());
            httpExecutor.emitPayloadFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    response.statusCode(),
                    errorBody);
            AiException exception = providerResponseException(
                    "Streaming response request",
                    response.statusCode(),
                    errorBody);
            httpExecutor.emitFailure(
                    providerRequest.operation(),
                    providerRequest.model(),
                    providerRequest.path(),
                    providerRequest.stream(),
                    result.attempt(),
                    response.statusCode(),
                    result.duration(),
                    exception);
            throw exception;
        }
        httpExecutor.emitSuccess(
                providerRequest.operation(),
                providerRequest.model(),
                providerRequest.path(),
                providerRequest.stream(),
                result.attempt(),
                response.statusCode(),
                result.duration(),
                null,
                null);
        long streamOpenNanos = System.nanoTime();
        return new ResponseStream(
                response.body(),
                providerAdapter::parseResponseStreamDelta,
                failure -> httpExecutor.emitFailure(
                        providerRequest.operation(),
                        providerRequest.model(),
                        providerRequest.path(),
                        providerRequest.stream(),
                        result.attempt(),
                        response.statusCode(),
                        result.duration().plus(AiHttpExecutor.elapsedSince(streamOpenNanos)),
                        failure));
    }

    private URI providerUri(ProviderRequestSpec providerRequest) {
        return baseUri.resolve(providerRequest.relativePath());
    }

    private HttpRequest.Builder providerHttpRequestBuilder(ProviderRequestSpec providerRequest) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(providerUri(providerRequest));
        providerRequest.headers().forEach((name, value) ->
                builder.header(name, value.replace(API_KEY_HEADER_PLACEHOLDER, apiKey)));
        return builder;
    }

    private String eventModel(String requestModel, String responseModel) {
        return responseModel == null ? requestModel : responseModel;
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

    private static AiProviderAdapter providerAdapter(AiProvider provider) {
        return switch (provider) {
            // Keep protocol selection public and adapter classes internal so new
            // providers can be added without freezing the adapter SPI too early.
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProviderAdapter();
            case ANTHROPIC -> new AnthropicProviderAdapter();
        };
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

    private AiException providerResponseException(String requestDescription, int statusCode, String body) {
        AiError error = providerAdapter.parseError(body);
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
        private AiPayloadDiagnosticsListener payloadDiagnosticsListener;
        private AiRedactionPolicy redactionPolicy;
        private HttpClient httpClient;
        private AiProvider provider;
        private AiProviderPreset providerPreset;

        private Builder() {
        }

        /**
         * Sets the provider API key used by the selected protocol's authentication headers.
         *
         * @param apiKey API key value; do not commit real keys to source control
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the provider base URL.
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
         * Sets the provider protocol used to translate SDK requests to HTTP payloads.
         *
         * <p>The default is {@link AiProvider#OPENAI_COMPATIBLE}. The builder
         * accepts a provider enum instead of an internal adapter instance so
         * application code does not depend on provider wire-shape internals.
         *
         * @param provider provider protocol to use
         * @return this builder
         */
        public Builder provider(AiProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider must not be null");
            return this;
        }

        /**
         * Applies a known provider preset for OpenAI-compatible model services.
         *
         * <p>Presets fill in the provider protocol only. They expose documented
         * provider endpoints via {@link AiProviderPreset#baseUrl()} for callers
         * that want to copy the value into external configuration, but the
         * runtime client still requires an explicit {@link #baseUrl(String)} so
         * applications do not accidentally bind to a provider endpoint.
         * Presets do not include API keys, default models, or provider-specific
         * feature guarantees. A later or earlier explicit
         * {@link #provider(AiProvider)} call wins for the protocol field.
         *
         * @param providerPreset provider preset to apply
         * @return this builder
         */
        public Builder providerPreset(AiProviderPreset providerPreset) {
            this.providerPreset = Objects.requireNonNull(providerPreset, "providerPreset must not be null");
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
         * Sets an opt-in listener for redacted provider request and response payloads.
         *
         * <p>Payload diagnostics are disabled by default. Even with redaction,
         * applications should enable this only for controlled troubleshooting
         * with appropriate log retention and access controls.
         *
         * @param payloadDiagnosticsListener listener to receive redacted payload diagnostics
         * @return this builder
         */
        public Builder payloadDiagnosticsListener(AiPayloadDiagnosticsListener payloadDiagnosticsListener) {
            this.payloadDiagnosticsListener = Objects.requireNonNull(
                    payloadDiagnosticsListener,
                    "payloadDiagnosticsListener must not be null");
            return this;
        }

        /**
         * Sets the redaction policy used before opt-in payload diagnostics are emitted.
         *
         * @param redactionPolicy redaction policy to apply
         * @return this builder
         */
        public Builder redactionPolicy(AiRedactionPolicy redactionPolicy) {
            this.redactionPolicy = Objects.requireNonNull(redactionPolicy, "redactionPolicy must not be null");
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

}
