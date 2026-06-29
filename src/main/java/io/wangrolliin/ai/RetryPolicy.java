package io.wangrolliin.ai;

import java.time.Duration;
import java.util.Set;

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

    public static RetryPolicy none() {
        return builder()
                .maxAttempts(1)
                .initialDelay(Duration.ZERO)
                .maxDelay(Duration.ZERO)
                .retryableStatusCodes(Set.of())
                .build();
    }

    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    public Set<Integer> retryableStatusCodes() {
        return retryableStatusCodes;
    }

    public boolean shouldRetryStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    Duration delayForAttempt(int attemptNumber) {
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

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(200);
        private Duration maxDelay = Duration.ofSeconds(2);
        private Set<Integer> retryableStatusCodes = DEFAULT_RETRYABLE_STATUS_CODES;

        private Builder() {
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder retryableStatusCodes(Set<Integer> retryableStatusCodes) {
            if (retryableStatusCodes == null) {
                throw new IllegalArgumentException("retryableStatusCodes must not be null");
            }
            this.retryableStatusCodes = Set.copyOf(retryableStatusCodes);
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
