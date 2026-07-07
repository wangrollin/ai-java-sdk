package io.wangrollin.ai.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.wangrollin.ai.event.AiEventListener;
import io.wangrollin.ai.event.AiFailureEvent;
import io.wangrollin.ai.event.AiRequestEvent;
import io.wangrollin.ai.event.AiResponseEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Bridges safe SDK lifecycle events into OpenTelemetry spans.
 *
 * <p>The listener deliberately records only bounded operational metadata. It
 * does not include API keys, prompts, generated text, tool arguments, raw
 * provider bodies, provider URLs, endpoint paths, or diagnostic messages in
 * span names or attributes.
 */
public final class OpenTelemetryAiEventListener implements AiEventListener {
    /**
     * Default instrumentation scope name for SDK tracing.
     */
    public static final String DEFAULT_INSTRUMENTATION_NAME = "io.wangrollin.ai.sdk";

    private static final String ATTRIBUTE_OPERATION = "ai.operation";
    private static final String ATTRIBUTE_MODEL = "ai.model";
    private static final String ATTRIBUTE_STREAM = "ai.stream";
    private static final String ATTRIBUTE_ATTEMPT = "ai.attempt";
    private static final String ATTRIBUTE_FINISH_REASON = "ai.finish_reason";
    private static final String ATTRIBUTE_STATUS_CODE = "http.response.status_code";
    private static final String ATTRIBUTE_EXCEPTION_TYPE = "exception.type";

    private final Tracer tracer;

    private OpenTelemetryAiEventListener(Builder builder) {
        this.tracer = builder.tracer;
    }

    /**
     * Creates a builder backed by the tracer from the given OpenTelemetry instance.
     *
     * @param openTelemetry configured OpenTelemetry entry point
     * @return listener builder
     */
    public static Builder builder(OpenTelemetry openTelemetry) {
        return new Builder(openTelemetry);
    }

    /**
     * Creates a builder backed by an already configured tracer.
     *
     * @param tracer tracer to use for SDK spans
     * @return listener builder
     */
    public static Builder builder(Tracer tracer) {
        return new Builder(tracer);
    }

    /**
     * Creates a listener using the default instrumentation scope name.
     *
     * @param openTelemetry configured OpenTelemetry entry point
     * @return event listener
     */
    public static OpenTelemetryAiEventListener create(OpenTelemetry openTelemetry) {
        return builder(openTelemetry).build();
    }

    /**
     * Request-start events intentionally do not create spans because the current
     * event contract has no correlation identifier for reliably matching start
     * and terminal events across retries or threads.
     *
     * @param event safe request metadata
     */
    @Override
    public void requestStarted(AiRequestEvent event) {
        Objects.requireNonNull(event, "event must not be null");
    }

    @Override
    public void requestSucceeded(AiResponseEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        long endEpochNanos = currentEpochNanos();
        Span span = spanBuilder(event.operation(), event.duration(), endEpochNanos)
                .setAttribute(ATTRIBUTE_OPERATION, event.operation())
                .setAttribute(ATTRIBUTE_MODEL, event.model())
                .setAttribute(ATTRIBUTE_STREAM, event.stream())
                .setAttribute(ATTRIBUTE_ATTEMPT, event.attempt())
                .setAttribute(ATTRIBUTE_STATUS_CODE, event.statusCode())
                .startSpan();
        try {
            if (event.finishReason() != null) {
                span.setAttribute(ATTRIBUTE_FINISH_REASON, event.finishReason());
            }
            span.setStatus(StatusCode.OK);
        } finally {
            span.end(endEpochNanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void requestFailed(AiFailureEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        long endEpochNanos = currentEpochNanos();
        SpanBuilder spanBuilder = spanBuilder(event.operation(), event.duration(), endEpochNanos)
                .setAttribute(ATTRIBUTE_OPERATION, event.operation())
                .setAttribute(ATTRIBUTE_MODEL, event.model())
                .setAttribute(ATTRIBUTE_STREAM, event.stream())
                .setAttribute(ATTRIBUTE_ATTEMPT, event.attempt())
                .setAttribute(ATTRIBUTE_EXCEPTION_TYPE, event.exceptionType());
        if (event.statusCode() != null) {
            spanBuilder.setAttribute(ATTRIBUTE_STATUS_CODE, event.statusCode());
        }
        Span span = spanBuilder.startSpan();
        try {
            span.setStatus(StatusCode.ERROR);
        } finally {
            span.end(endEpochNanos, TimeUnit.NANOSECONDS);
        }
    }

    private SpanBuilder spanBuilder(String operation, Duration duration, long endEpochNanos) {
        return tracer.spanBuilder("ai.sdk." + operation)
                .setStartTimestamp(endEpochNanos - duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static long currentEpochNanos() {
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }

    /**
     * Builder for {@link OpenTelemetryAiEventListener}.
     */
    public static final class Builder {
        private Tracer tracer;
        private OpenTelemetry openTelemetry;
        private String instrumentationName = DEFAULT_INSTRUMENTATION_NAME;

        private Builder(OpenTelemetry openTelemetry) {
            this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        }

        private Builder(Tracer tracer) {
            this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
        }

        /**
         * Sets the instrumentation scope name used when creating a tracer from
         * an {@link OpenTelemetry} instance.
         *
         * @param instrumentationName non-blank instrumentation scope name
         * @return this builder
         */
        public Builder instrumentationName(String instrumentationName) {
            Objects.requireNonNull(instrumentationName, "instrumentationName must not be null");
            String normalized = instrumentationName.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("instrumentationName must not be blank");
            }
            this.instrumentationName = normalized;
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return event listener
         */
        public OpenTelemetryAiEventListener build() {
            if (tracer == null) {
                tracer = openTelemetry.getTracer(instrumentationName);
            }
            return new OpenTelemetryAiEventListener(this);
        }
    }
}
