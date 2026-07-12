package io.wangrollin.ai.spring.autoconfigure;

import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.client.RetryPolicy;
import io.wangrollin.ai.diagnostic.AiPayloadDiagnosticsListener;
import io.wangrollin.ai.diagnostic.AiRedactionPolicy;
import io.wangrollin.ai.event.AiEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.util.Set;

/**
 * Spring Boot auto-configuration for the default OpenAI-compatible SDK client.
 *
 * <p>The starter creates exactly one default {@link AiClient} when the
 * application has not supplied its own SDK client. The concrete bean implements
 * both {@link AiChatClient} and {@link AiResponseClient}, so application code can
 * depend on the narrower interface it needs.
 */
@AutoConfiguration
@ConditionalOnClass(AiClient.class)
@EnableConfigurationProperties(AiSdkProperties.class)
public class AiSdkAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean({AiClient.class, AiChatClient.class, AiResponseClient.class})
    AiClient aiClient(
            AiSdkProperties properties,
            ObjectProvider<AiEventListener> eventListener,
            ObjectProvider<AiPayloadDiagnosticsListener> payloadDiagnosticsListener,
            ObjectProvider<AiRedactionPolicy> redactionPolicy,
            ObjectProvider<HttpClient> httpClient) {
        AiClient.Builder builder = AiClient.builder()
                .apiKey(properties.requireApiKey())
                .baseUrl(properties.requireBaseUrl())
                .defaultModel(properties.requireModel())
                .providerPreset(properties.requireProviderPreset())
                .provider(properties.requireProvider())
                .timeout(properties.requireTimeout())
                .retryPolicy(retryPolicy(properties.getRetry()));

        eventListener.ifAvailable(builder::eventListener);
        payloadDiagnosticsListener.ifAvailable(builder::payloadDiagnosticsListener);
        redactionPolicy.ifAvailable(builder::redactionPolicy);
        httpClient.ifAvailable(builder::httpClient);

        return builder.build();
    }

    private static RetryPolicy retryPolicy(AiSdkProperties.Retry retry) {
        if (retry == null || !retry.isEnabled()) {
            return RetryPolicy.none();
        }

        RetryPolicy.Builder builder = RetryPolicy.builder()
                .maxAttempts(retry.getMaxAttempts())
                .initialDelay(retry.getInitialDelay())
                .maxDelay(retry.getMaxDelay());
        Set<Integer> statusCodes = retry.getStatusCodes();
        if (statusCodes != null) {
            builder.retryableStatusCodes(statusCodes);
        }
        return builder.build();
    }
}
