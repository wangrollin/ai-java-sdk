package io.wangrollin.ai.event;

import io.wangrollin.ai.chat.ChatUsage;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe in-memory metrics collector backed by {@link AiEventListener}.
 *
 * <p>This listener is intentionally dependency-free. Applications that already
 * use Micrometer, OpenTelemetry, or another telemetry system can either read
 * {@link #snapshot()} periodically or implement {@link AiEventListener}
 * directly and translate the same safe lifecycle fields into their own metric
 * instruments.
 */
public final class InMemoryAiMetricsListener implements AiEventListener {
    private long startedCount;
    private long succeededCount;
    private long failedCount;
    private Duration totalDuration = Duration.ZERO;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private final Map<String, Long> startedByOperation = new HashMap<>();
    private final Map<String, Long> succeededByOperation = new HashMap<>();
    private final Map<String, Long> failedByOperation = new HashMap<>();
    private final Map<String, Long> terminalByModel = new HashMap<>();
    private final Map<Integer, Long> terminalByStatusCode = new HashMap<>();
    private final Map<String, Long> failuresByExceptionType = new HashMap<>();

    /**
     * Creates an empty in-memory metrics listener.
     *
     * @return metrics listener
     */
    public static InMemoryAiMetricsListener create() {
        return new InMemoryAiMetricsListener();
    }

    @Override
    public synchronized void requestStarted(AiRequestEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        startedCount++;
        increment(startedByOperation, event.operation());
    }

    @Override
    public synchronized void requestSucceeded(AiResponseEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        succeededCount++;
        totalDuration = totalDuration.plus(event.duration());
        increment(succeededByOperation, event.operation());
        increment(terminalByModel, event.model());
        increment(terminalByStatusCode, event.statusCode());
        addUsage(event.usage());
    }

    @Override
    public synchronized void requestFailed(AiFailureEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        failedCount++;
        totalDuration = totalDuration.plus(event.duration());
        increment(failedByOperation, event.operation());
        increment(terminalByModel, event.model());
        if (event.statusCode() != null) {
            increment(terminalByStatusCode, event.statusCode());
        }
        increment(failuresByExceptionType, event.exceptionType());
    }

    /**
     * Returns an immutable point-in-time snapshot of all collected metrics.
     *
     * @return current metrics snapshot
     */
    public synchronized AiMetricsSnapshot snapshot() {
        return new AiMetricsSnapshot(
                startedCount,
                succeededCount,
                failedCount,
                totalDuration,
                promptTokens,
                completionTokens,
                totalTokens,
                startedByOperation,
                succeededByOperation,
                failedByOperation,
                terminalByModel,
                terminalByStatusCode,
                failuresByExceptionType);
    }

    /**
     * Clears all counters. This is useful for tests and short-lived diagnostic
     * windows where the same listener instance is reused.
     */
    public synchronized void reset() {
        startedCount = 0;
        succeededCount = 0;
        failedCount = 0;
        totalDuration = Duration.ZERO;
        promptTokens = 0;
        completionTokens = 0;
        totalTokens = 0;
        startedByOperation.clear();
        succeededByOperation.clear();
        failedByOperation.clear();
        terminalByModel.clear();
        terminalByStatusCode.clear();
        failuresByExceptionType.clear();
    }

    private void addUsage(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        promptTokens += usage.promptTokens();
        completionTokens += usage.completionTokens();
        totalTokens += usage.totalTokens();
    }

    private static <K> void increment(Map<K, Long> counts, K key) {
        counts.merge(key, 1L, Long::sum);
    }
}
