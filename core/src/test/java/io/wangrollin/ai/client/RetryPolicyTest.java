package io.wangrollin.ai.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyTest {
    @Test
    void validatesRetryPolicy() {
        IllegalArgumentException maxAttempts = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .maxAttempts(0)
                .build());
        IllegalArgumentException initialDelay = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .initialDelay(Duration.ofMillis(-1))
                .build());
        IllegalArgumentException maxDelay = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .maxDelay(Duration.ofMillis(-1))
                .build());
        IllegalArgumentException delayOrder = assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder()
                .initialDelay(Duration.ofSeconds(2))
                .maxDelay(Duration.ofSeconds(1))
                .build());

        assertEquals("maxAttempts must be positive", maxAttempts.getMessage());
        assertEquals("initialDelay must not be negative", initialDelay.getMessage());
        assertEquals("maxDelay must not be negative", maxDelay.getMessage());
        assertEquals("initialDelay must not be greater than maxDelay", delayOrder.getMessage());
    }

    @Test
    void computesExponentialBackoffWithMaximumDelay() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(5)
                .initialDelay(Duration.ofMillis(100))
                .maxDelay(Duration.ofMillis(250))
                .build();

        assertEquals(Duration.ZERO, policy.delayForAttempt(1));
        assertEquals(Duration.ofMillis(100), policy.delayForAttempt(2));
        assertEquals(Duration.ofMillis(200), policy.delayForAttempt(3));
        assertEquals(Duration.ofMillis(250), policy.delayForAttempt(4));
    }
}
