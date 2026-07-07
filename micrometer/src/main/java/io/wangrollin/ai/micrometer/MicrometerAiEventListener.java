package io.wangrollin.ai.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;

import java.util.Objects;

/**
 * Bridges safe SDK lifecycle events into Micrometer meters.
 *
 * <p>The listener intentionally tags only bounded operational metadata. Provider
 * paths, base URLs, prompts, model outputs, tool arguments, raw bodies, and
 * diagnostic messages are not used as meter names or tags because those values
 * can be sensitive or high-cardinality in production metrics backends.
 */
public final class MicrometerAiEventListener implements AiEventListener {
    public static final String DEFAULT_METRIC_PREFIX = "ai.sdk";
    private static final String NONE = "none";

    private final MeterRegistry registry;
    private final String metricPrefix;

    private MicrometerAiEventListener(Builder builder) {
        this.registry = builder.registry;
        this.metricPrefix = builder.metricPrefix;
    }

    /**
     * Creates a builder for a Micrometer-backed event listener.
     *
     * @param registry target meter registry
     * @return listener builder
     */
    public static Builder builder(MeterRegistry registry) {
        return new Builder(registry);
    }

    /**
     * Creates a listener with the default {@code ai.sdk} metric prefix.
     *
     * @param registry target meter registry
     * @return event listener
     */
    public static MicrometerAiEventListener create(MeterRegistry registry) {
        return builder(registry).build();
    }

    @Override
    public void requestStarted(AiRequestEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        counter("requests.started", requestTags(event.operation(), event.model(), event.stream()))
                .increment();
    }

    @Override
    public void requestSucceeded(AiResponseEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Tags responseTags = responseTags(event.operation(), event.model(), event.stream(), event.statusCode());
        counter("requests.succeeded", responseTags).increment();
        timer("requests.duration", terminalTags(
                event.operation(),
                event.model(),
                event.stream(),
                "succeeded",
                String.valueOf(event.statusCode()),
                NONE)).record(event.duration());
        recordUsage(event.usage(), responseTags);
    }

    @Override
    public void requestFailed(AiFailureEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String status = statusTag(event.statusCode());
        Tags failureTags = failureTags(
                event.operation(),
                event.model(),
                event.stream(),
                status,
                event.exceptionType());
        counter("requests.failed", failureTags).increment();
        timer("requests.duration", terminalTags(
                event.operation(),
                event.model(),
                event.stream(),
                "failed",
                status,
                event.exceptionType())).record(event.duration());
    }

    private void recordUsage(ChatUsage usage, Tags tags) {
        if (usage == null) {
            return;
        }
        counter("tokens.prompt", tags).increment(usage.promptTokens());
        counter("tokens.completion", tags).increment(usage.completionTokens());
        counter("tokens.total", tags).increment(usage.totalTokens());
    }

    private Counter counter(String suffix, Tags tags) {
        return Counter.builder(metricName(suffix))
                .tags(tags)
                .register(registry);
    }

    private Timer timer(String suffix, Tags tags) {
        return Timer.builder(metricName(suffix))
                .tags(tags)
                .register(registry);
    }

    private String metricName(String suffix) {
        return metricPrefix + "." + suffix;
    }

    private static Tags requestTags(String operation, String model, boolean stream) {
        return Tags.of(
                "operation", operation,
                "model", model,
                "stream", String.valueOf(stream));
    }

    private static Tags responseTags(String operation, String model, boolean stream, int statusCode) {
        return requestTags(operation, model, stream)
                .and("status", String.valueOf(statusCode));
    }

    private static Tags failureTags(
            String operation,
            String model,
            boolean stream,
            String status,
            String exceptionType) {
        return requestTags(operation, model, stream)
                .and("status", status, "exception.type", exceptionType);
    }

    private static Tags terminalTags(
            String operation,
            String model,
            boolean stream,
            String outcome,
            String status,
            String exceptionType) {
        return requestTags(operation, model, stream)
                .and("outcome", outcome, "status", status, "exception.type", exceptionType);
    }

    private static String statusTag(Integer statusCode) {
        return statusCode == null ? NONE : String.valueOf(statusCode);
    }

    /**
     * Builder for {@link MicrometerAiEventListener}.
     */
    public static final class Builder {
        private final MeterRegistry registry;
        private String metricPrefix = DEFAULT_METRIC_PREFIX;

        private Builder(MeterRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry must not be null");
        }

        /**
         * Sets the metric name prefix. A trailing dot is ignored so callers can
         * pass either {@code ai.sdk} or {@code ai.sdk.}.
         *
         * @param metricPrefix metric prefix
         * @return this builder
         */
        public Builder metricPrefix(String metricPrefix) {
            Objects.requireNonNull(metricPrefix, "metricPrefix must not be null");
            String normalized = metricPrefix.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("metricPrefix must not be blank");
            }
            while (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("metricPrefix must contain a meter namespace");
            }
            this.metricPrefix = normalized;
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return event listener
         */
        public MicrometerAiEventListener build() {
            return new MicrometerAiEventListener(this);
        }
    }
}
