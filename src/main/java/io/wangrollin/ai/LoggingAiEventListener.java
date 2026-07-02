package io.wangrollin.ai;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Logs safe SDK request lifecycle events through JDK {@link System.Logger}.
 *
 * <p>The listener only formats fields already exposed by {@link AiEventListener}
 * events. It never has access to API keys, prompts, model outputs, tool
 * arguments, authorization headers, or raw provider response bodies.
 */
public final class LoggingAiEventListener implements AiEventListener {
    private static final String LOGGER_NAME = "io.wangrollin.ai";

    private final Logger logger;
    private final Level startedLevel;
    private final Level succeededLevel;
    private final Level failedLevel;
    private final boolean logStartedEvents;

    private LoggingAiEventListener(Builder builder) {
        this.logger = builder.logger;
        this.startedLevel = builder.startedLevel;
        this.succeededLevel = builder.succeededLevel;
        this.failedLevel = builder.failedLevel;
        this.logStartedEvents = builder.logStartedEvents;
    }

    /**
     * Creates a listener using the SDK logger name and default levels.
     *
     * @return logging event listener
     */
    public static LoggingAiEventListener create() {
        return builder().build();
    }

    /**
     * Creates a listener using the supplied logger and default levels.
     *
     * @param logger logger that receives formatted event messages
     * @return logging event listener
     */
    public static LoggingAiEventListener create(Logger logger) {
        return builder().logger(logger).build();
    }

    /**
     * Starts building a logging listener.
     *
     * @return listener builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void requestStarted(AiRequestEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logStartedEvents || !logger.isLoggable(startedLevel)) {
            return;
        }
        log(startedLevel, fields("ai.request.started")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("attempt=" + event.attempt())
                .toString());
    }

    @Override
    public void requestSucceeded(AiResponseEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logger.isLoggable(succeededLevel)) {
            return;
        }
        log(succeededLevel, fields("ai.request.succeeded")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("attempt=" + event.attempt())
                .add("statusCode=" + event.statusCode())
                .add("durationMs=" + millis(event.duration()))
                .add(optional("finishReason", event.finishReason()))
                .add(optional("usage", usage(event.usage())))
                .toString());
    }

    @Override
    public void requestFailed(AiFailureEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logger.isLoggable(failedLevel)) {
            return;
        }
        log(failedLevel, fields("ai.request.failed")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("attempt=" + event.attempt())
                .add(optional("statusCode", event.statusCode()))
                .add("durationMs=" + millis(event.duration()))
                .add("exceptionType=" + event.exceptionType())
                .add("message=" + quote(event.message()))
                .toString());
    }

    private void log(Level level, String message) {
        logger.log(level, message);
    }

    private static StringJoiner fields(String eventName) {
        return new StringJoiner(" ").add("event=" + eventName);
    }

    private static long millis(Duration duration) {
        return duration.toMillis();
    }

    private static String optional(String name, Object value) {
        return value == null ? name + "=null" : name + "=" + value;
    }

    private static String usage(ChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return "promptTokens:" + usage.promptTokens()
                + ",completionTokens:" + usage.completionTokens()
                + ",totalTokens:" + usage.totalTokens();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Builder for {@link LoggingAiEventListener}.
     */
    public static final class Builder {
        private Logger logger = System.getLogger(LOGGER_NAME);
        private Level startedLevel = Level.DEBUG;
        private Level succeededLevel = Level.INFO;
        private Level failedLevel = Level.WARNING;
        private boolean logStartedEvents = true;

        private Builder() {
        }

        /**
         * Sets the logger that receives formatted event messages.
         *
         * @param logger logger to use
         * @return this builder
         */
        public Builder logger(Logger logger) {
            this.logger = Objects.requireNonNull(logger, "logger must not be null");
            return this;
        }

        /**
         * Sets the level used for request-start events.
         *
         * @param startedLevel log level for started events
         * @return this builder
         */
        public Builder startedLevel(Level startedLevel) {
            this.startedLevel = Objects.requireNonNull(startedLevel, "startedLevel must not be null");
            return this;
        }

        /**
         * Sets the level used for successful request events.
         *
         * @param succeededLevel log level for success events
         * @return this builder
         */
        public Builder succeededLevel(Level succeededLevel) {
            this.succeededLevel = Objects.requireNonNull(succeededLevel, "succeededLevel must not be null");
            return this;
        }

        /**
         * Sets the level used for failed request events.
         *
         * @param failedLevel log level for failure events
         * @return this builder
         */
        public Builder failedLevel(Level failedLevel) {
            this.failedLevel = Objects.requireNonNull(failedLevel, "failedLevel must not be null");
            return this;
        }

        /**
         * Enables or disables request-start events, which can be noisy for
         * high-volume applications that only need terminal success/failure logs.
         *
         * @param logStartedEvents whether to log request-start events
         * @return this builder
         */
        public Builder logStartedEvents(boolean logStartedEvents) {
            this.logStartedEvents = logStartedEvents;
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return logging event listener
         */
        public LoggingAiEventListener build() {
            return new LoggingAiEventListener(this);
        }
    }
}
