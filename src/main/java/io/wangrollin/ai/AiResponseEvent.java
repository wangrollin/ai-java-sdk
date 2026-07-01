package io.wangrollin.ai;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Safe metadata emitted when an SDK request attempt succeeds.
 *
 * @param operation SDK operation such as {@code chat} or {@code stream}
 * @param model provider model name when known
 * @param baseUri configured provider base URI
 * @param path provider endpoint path
 * @param stream whether this attempt requested streaming events
 * @param attempt one-based attempt number
 * @param statusCode HTTP status code
 * @param duration elapsed attempt duration
 * @param finishReason optional provider finish reason
 * @param usage optional token accounting metadata
 */
public record AiResponseEvent(
        String operation,
        String model,
        URI baseUri,
        String path,
        boolean stream,
        int attempt,
        int statusCode,
        Duration duration,
        String finishReason,
        ChatUsage usage) {
    /**
     * Creates a response event after validating required metadata.
     *
     * @param operation SDK operation
     * @param model provider model name when known
     * @param baseUri configured provider base URI
     * @param path provider endpoint path
     * @param stream whether streaming is enabled
     * @param attempt one-based attempt number
     * @param statusCode HTTP status code
     * @param duration elapsed attempt duration
     * @param finishReason optional finish reason
     * @param usage optional token usage
     */
    public AiResponseEvent {
        operation = requireText(operation, "operation");
        model = requireText(model, "model");
        baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        path = requireText(path, "path");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status");
        }
        duration = requireNonNegative(duration);
        finishReason = normalizeOptionalText(finishReason);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
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
