package io.wangrollin.ai.event;

import io.wangrollin.ai.chat.ChatUsage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAiMetricsListenerTest {
    private static final URI BASE_URI = URI.create("https://example.test/v1/");

    @Test
    void aggregatesSafeLifecycleMetrics() {
        InMemoryAiMetricsListener listener = InMemoryAiMetricsListener.create();

        listener.requestStarted(new AiRequestEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1));
        listener.requestFailed(new AiFailureEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                1,
                503,
                Duration.ofMillis(25),
                "provider",
                "HTTP status 503"));
        listener.requestStarted(new AiRequestEvent(
                "chat",
                "test-model",
                BASE_URI,
                "/chat/completions",
                false,
                2));
        listener.requestSucceeded(new AiResponseEvent(
                "chat",
                "provider-model",
                BASE_URI,
                "/chat/completions",
                false,
                2,
                200,
                Duration.ofMillis(75),
                "stop",
                new ChatUsage(3, 4, 7)));

        AiMetricsSnapshot snapshot = listener.snapshot();

        assertEquals(2, snapshot.startedCount());
        assertEquals(1, snapshot.succeededCount());
        assertEquals(1, snapshot.failedCount());
        assertEquals(Duration.ofMillis(100), snapshot.totalDuration());
        assertEquals(3, snapshot.promptTokens());
        assertEquals(4, snapshot.completionTokens());
        assertEquals(7, snapshot.totalTokens());
        assertEquals(2, snapshot.startedByOperation().get("chat"));
        assertEquals(1, snapshot.succeededByOperation().get("chat"));
        assertEquals(1, snapshot.failedByOperation().get("chat"));
        assertEquals(1, snapshot.terminalByModel().get("test-model"));
        assertEquals(1, snapshot.terminalByModel().get("provider-model"));
        assertEquals(1, snapshot.terminalByStatusCode().get(503));
        assertEquals(1, snapshot.terminalByStatusCode().get(200));
        assertEquals(1, snapshot.failuresByExceptionType().get("provider"));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.startedByOperation().put("stream", 1L));
    }

    @Test
    void resetClearsCollectedMetrics() {
        InMemoryAiMetricsListener listener = InMemoryAiMetricsListener.create();
        listener.requestStarted(new AiRequestEvent(
                "stream",
                "test-model",
                BASE_URI,
                "/chat/completions",
                true,
                1));

        listener.reset();

        AiMetricsSnapshot snapshot = listener.snapshot();
        assertEquals(0, snapshot.startedCount());
        assertEquals(0, snapshot.succeededCount());
        assertEquals(0, snapshot.failedCount());
        assertEquals(Duration.ZERO, snapshot.totalDuration());
        assertEquals(0, snapshot.startedByOperation().size());
    }
}
