package io.wangrollin.ai.client;

import java.time.Duration;
import java.util.Set;

/**
 * Retry settings for transient chat request failures.
 */
public final class RetryPolicy {
    private static final Set<Integer> DEFAULT_RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Set<Integer> retryableStatusCodes;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelay = builder.initialDelay;
        this.maxDelay = builder.maxDelay;
        this.retryableStatusCodes = Set.copyOf(builder.retryableStatusCodes);

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        requireNonNegative(initialDelay, "initialDelay");
        requireNonNegative(maxDelay, "maxDelay");
        if (initialDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("initialDelay must not be greater than maxDelay");
        }
    }

    /**
     * Creates a policy that performs no retries.
     *
     * @return retry-disabled policy
     */
    public static RetryPolicy none() {
        return builder()
                .maxAttempts(1)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .retryableStatusCodes(Set.of())
                .build();
    }

    /**
     * Creates the default retry policy for common transient provider responses.
     *
     * @return default retry policy
     */
    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    /**
     * Starts building a retry policy.
     *
     * @return retry policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the total number of attempts, including the first request.
     *
     * @return total attempt count
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Returns the delay before the first retry.
     *
     * @return initial retry delay
     */
    public Duration initialDelay() {
        return initialDelay;
    }

    /**
     * Returns the maximum exponential backoff delay.
     *
     * @return maximum retry delay
     */
    public Duration maxDelay() {
        return maxDelay;
    }

    /**
     * Returns HTTP status codes that should be retried before the attempt limit.
     *
     * @return immutable retryable status code set
     */
    public Set<Integer> retryableStatusCodes() {
        return retryableStatusCodes;
    }

    /**
     * Checks whether a response status is retryable.
     *
     * @param statusCode HTTP response status
     * @return {@code true} when this status should be retried
     */
    public boolean shouldRetryStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    /**
     * Returns the backoff delay before the given attempt number.
     *
     * <p>Attempt {@code 1} is the original request and never waits. Attempts
     * {@code 2+} use exponential backoff capped at {@link #maxDelay()}.
     *
     * @param attemptNumber one-based attempt number
     * @return delay before that attempt
     */
    public Duration delayForAttempt(int attemptNumber) {
        if (attemptNumber <= 1 || initialDelay.isZero()) {
            return Duration.ZERO;
        }
        long multiplier = 1L << Math.min(attemptNumber - 2, 30);
        Duration delay = initialDelay.multipliedBy(multiplier);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    /**
     * Builder for {@link RetryPolicy}.
     */
    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(200);
        private Duration maxDelay = Duration.ofSeconds(2);
        private Set<Integer> retryableStatusCodes = DEFAULT_RETRYABLE_STATUS_CODES;

        private Builder() {
        }

        /**
         * Sets the total number of attempts, including the first request.
         *
         * @param maxAttempts positive total attempt count
         * @return this builder
         */
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the delay before the first retry.
         *
         * @param initialDelay non-negative retry delay
         * @return this builder
         */
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         * Sets the maximum exponential backoff delay.
         *
         * @param maxDelay non-negative maximum delay
         * @return this builder
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Replaces the retryable HTTP status code set.
         *
         * @param retryableStatusCodes statuses that should be retried
         * @return this builder
         */
        public Builder retryableStatusCodes(Set<Integer> retryableStatusCodes) {
            if (retryableStatusCodes == null) {
                throw new IllegalArgumentException("retryableStatusCodes must not be null");
            }
            this.retryableStatusCodes = Set.copyOf(retryableStatusCodes);
            return this;
        }

        /**
         * Builds an immutable retry policy.
         *
         * @return retry policy
         */
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
