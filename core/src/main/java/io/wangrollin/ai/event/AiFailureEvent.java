package io.wangrollin.ai.event;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Safe metadata emitted when an SDK request attempt fails.
 *
 * @param operation SDK operation such as {@code chat} or {@code stream}
 * @param model model selected for this request
 * @param baseUri configured provider base URI
 * @param path provider endpoint path
 * @param stream whether this attempt requested streaming events
 * @param attempt one-based attempt number
 * @param statusCode optional HTTP status code
 * @param duration elapsed attempt duration
 * @param exceptionType exception class name or failure category
 * @param message sanitized diagnostic message
 */
public record AiFailureEvent(
        String operation,
        String model,
        URI baseUri,
        String path,
        boolean stream,
        int attempt,
        Integer statusCode,
        Duration duration,
        String exceptionType,
        String message) {
    /**
     * Creates a failure event after validating required metadata.
     *
     * @param operation SDK operation
     * @param model selected model
     * @param baseUri configured provider base URI
     * @param path provider endpoint path
     * @param stream whether streaming is enabled
     * @param attempt one-based attempt number
     * @param statusCode optional HTTP status code
     * @param duration elapsed attempt duration
     * @param exceptionType exception class name or failure category
     * @param message sanitized diagnostic message
     */
    public AiFailureEvent {
        operation = requireText(operation, "operation");
        model = requireText(model, "model");
        baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        path = requireText(path, "path");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status");
        }
        duration = requireNonNegative(duration);
        exceptionType = requireText(exceptionType, "exceptionType");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Duration requireNonNegative(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        return duration;
    }
}
