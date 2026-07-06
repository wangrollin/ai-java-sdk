package io.wangrollin.ai.diagnostic;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Logs redacted provider payload diagnostics through JDK {@link System.Logger}.
 *
 * <p>This listener should be enabled only for controlled troubleshooting. It
 * logs payloads after {@link AiRedactionPolicy} has removed sensitive fields,
 * but applications remain responsible for log retention and access controls.
 */
public final class LoggingAiPayloadDiagnosticsListener implements AiPayloadDiagnosticsListener {
    private static final String LOGGER_NAME = "io.wangrollin.ai.diagnostic";

    private final Logger logger;
    private final Level level;

    private LoggingAiPayloadDiagnosticsListener(Builder builder) {
        this.logger = builder.logger;
        this.level = builder.level;
    }

    /**
     * Creates a payload diagnostics logger using default settings.
     *
     * @return diagnostics listener
     */
    public static LoggingAiPayloadDiagnosticsListener create() {
        return builder().build();
    }

    /**
     * Starts building a diagnostics logger.
     *
     * @return listener builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void requestPayload(AiPayloadRequestEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logger.isLoggable(level)) {
            return;
        }
        log(fields("ai.payload.request")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("body=" + quote(event.redactedBody()))
                .toString());
    }

    @Override
    public void responsePayload(AiPayloadResponseEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logger.isLoggable(level)) {
            return;
        }
        log(fields("ai.payload.response")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("statusCode=" + event.statusCode())
                .add("body=" + quote(event.redactedBody()))
                .toString());
    }

    @Override
    public void failurePayload(AiPayloadFailureEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!logger.isLoggable(level)) {
            return;
        }
        log(fields("ai.payload.failure")
                .add("operation=" + event.operation())
                .add("model=" + event.model())
                .add("path=" + event.path())
                .add("stream=" + event.stream())
                .add("statusCode=" + event.statusCode())
                .add("body=" + quote(event.redactedBody()))
                .toString());
    }

    private void log(String message) {
        logger.log(level, message);
    }

    private static StringJoiner fields(String eventName) {
        return new StringJoiner(" ").add("event=" + eventName);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Builder for {@link LoggingAiPayloadDiagnosticsListener}.
     */
    public static final class Builder {
        private Logger logger = System.getLogger(LOGGER_NAME);
        private Level level = Level.DEBUG;

        private Builder() {
        }

        /**
         * Sets the logger that receives redacted diagnostic messages.
         *
         * @param logger logger to use
         * @return this builder
         */
        public Builder logger(Logger logger) {
            this.logger = Objects.requireNonNull(logger, "logger must not be null");
            return this;
        }

        /**
         * Sets the log level for all payload diagnostic events.
         *
         * @param level log level
         * @return this builder
         */
        public Builder level(Level level) {
            this.level = Objects.requireNonNull(level, "level must not be null");
            return this;
        }

        /**
         * Builds the listener.
         *
         * @return diagnostics listener
         */
        public LoggingAiPayloadDiagnosticsListener build() {
            return new LoggingAiPayloadDiagnosticsListener(this);
        }
    }
}
