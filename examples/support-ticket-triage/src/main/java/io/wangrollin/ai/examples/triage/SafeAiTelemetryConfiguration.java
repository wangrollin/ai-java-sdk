package io.wangrollin.ai.examples.triage;

import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiResponseEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Demonstrates wiring SDK lifecycle events into application logging while
 * keeping the example on metadata-only fields that are safe by default.
 */
@Configuration(proxyBeanMethods = false)
class SafeAiTelemetryConfiguration {
    private static final Logger LOGGER = Logger.getLogger(SafeAiTelemetryConfiguration.class.getName());

    @Bean
    AiEventListener safeAiEventLogger() {
        return new AiEventListener() {
            @Override
            public void requestSucceeded(AiResponseEvent event) {
                LOGGER.info(() -> "ai_request_succeeded operation=%s model=%s status=%s duration=%s"
                        .formatted(event.operation(), event.model(), event.statusCode(), event.duration()));
            }

            @Override
            public void requestFailed(AiFailureEvent event) {
                LOGGER.log(
                        Level.WARNING,
                        () -> "ai_request_failed operation=%s model=%s status=%s type=%s duration=%s"
                                .formatted(
                                        event.operation(),
                                        event.model(),
                                        event.statusCode(),
                                        event.exceptionType(),
                                        event.duration()));
            }
        };
    }
}
