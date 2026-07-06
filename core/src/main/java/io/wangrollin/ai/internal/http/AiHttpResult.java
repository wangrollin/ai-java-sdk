package io.wangrollin.ai.internal.http;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Result of a completed HTTP attempt after retry handling.
 *
 * @param response final HTTP response returned to the caller
 * @param attempt one-based attempt number that produced the response
 * @param duration duration of the final attempt
 * @param <T> response body type
 */
public record AiHttpResult<T>(HttpResponse<T> response, int attempt, Duration duration) {
    public AiHttpResult {
        response = Objects.requireNonNull(response, "response must not be null");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        duration = Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }
}
