package io.wangrollin.ai.spring.autoconfigure;

import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiProvider;
import io.wangrollin.ai.client.AiProviderPreset;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

/**
 * Configuration properties for the default {@link AiClient} managed by Spring Boot.
 *
 * <p>The starter keeps provider credentials in external application configuration
 * instead of embedding them in code. The API key and model are intentionally
 * validated when the client bean is created so applications fail fast during
 * startup if required runtime configuration is missing.
 */
@ConfigurationProperties("ai.sdk")
public class AiSdkProperties {
    /**
     * Provider API key used for bearer authentication.
     */
    private String apiKey;

    /**
     * Default model used when an SDK request does not set a request-level model.
     */
    private String model;

    /**
     * OpenAI-compatible provider base URL. When unset, the selected provider preset supplies the default.
     */
    private String baseUrl;

    /**
     * Provider protocol used to translate SDK requests into provider HTTP payloads.
     */
    private AiProvider provider = AiProvider.OPENAI_COMPATIBLE;

    /**
     * Provider preset used to fill in protocol and base URL defaults.
     */
    private AiProviderPreset providerPreset = AiProviderPreset.OPENAI;

    /**
     * HTTP connection and request timeout.
     */
    private Duration timeout = AiClient.DEFAULT_TIMEOUT;

    /**
     * Retry settings for transient provider failures.
     */
    private Retry retry = new Retry();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider == null ? AiProvider.OPENAI_COMPATIBLE : provider;
    }

    public AiProviderPreset getProviderPreset() {
        return providerPreset;
    }

    public void setProviderPreset(AiProviderPreset providerPreset) {
        this.providerPreset = providerPreset == null ? AiProviderPreset.OPENAI : providerPreset;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    String requireApiKey() {
        return requireText(apiKey, "ai.sdk.api-key");
    }

    String requireModel() {
        return requireText(model, "ai.sdk.model");
    }

    Duration requireTimeout() {
        if (timeout == null) {
            throw new IllegalStateException("ai.sdk.timeout must not be null");
        }
        return timeout;
    }

    AiProvider requireProvider() {
        if (provider == null) {
            throw new IllegalStateException("ai.sdk.provider must not be null");
        }
        return provider;
    }

    AiProviderPreset requireProviderPreset() {
        if (providerPreset == null) {
            throw new IllegalStateException("ai.sdk.provider-preset must not be null");
        }
        return providerPreset;
    }

    private static String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value;
    }

    /**
     * Retry binding properties.
     */
    public static class Retry {
        /**
         * Whether to enable retry handling for transient provider statuses.
         */
        private boolean enabled;

        /**
         * Total attempt count, including the original request.
         */
        private int maxAttempts = 3;

        /**
         * Delay before the first retry.
         */
        private Duration initialDelay = Duration.ofMillis(200);

        /**
         * Maximum exponential backoff delay.
         */
        private Duration maxDelay = Duration.ofSeconds(2);

        /**
         * HTTP statuses that should be retried before the attempt limit.
         */
        private Set<Integer> statusCodes;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public Set<Integer> getStatusCodes() {
            return statusCodes;
        }

        public void setStatusCodes(Set<Integer> statusCodes) {
            this.statusCodes = statusCodes;
        }
    }
}
