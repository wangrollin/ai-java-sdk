package io.wangrollin.ai.event;

import java.time.Duration;
import java.util.Map;

/**
 * Immutable point-in-time view of safe SDK request metrics.
 *
 * <p>Counts are based on lifecycle events, so retries are counted as separate
 * attempts. The snapshot intentionally contains only operational metadata and
 * token accounting; it does not include prompts, generated text, tool
 * arguments, API keys, authorization headers, or raw provider bodies.
 *
 * @param startedCount number of request attempts started
 * @param succeededCount number of request attempts completed with success events
 * @param failedCount number of request attempts completed with failure events
 * @param totalDuration total duration observed on success and failure events
 * @param promptTokens total prompt tokens reported by successful responses
 * @param completionTokens total completion tokens reported by successful responses
 * @param totalTokens total tokens reported by successful responses
 * @param startedByOperation started attempts grouped by operation
 * @param succeededByOperation successful attempts grouped by operation
 * @param failedByOperation failed attempts grouped by operation
 * @param terminalByModel success and failure events grouped by model
 * @param terminalByStatusCode success and failure events that include an HTTP status code
 * @param failuresByExceptionType failure events grouped by exception type
 */
public record AiMetricsSnapshot(
        long startedCount,
        long succeededCount,
        long failedCount,
        Duration totalDuration,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        Map<String, Long> startedByOperation,
        Map<String, Long> succeededByOperation,
        Map<String, Long> failedByOperation,
        Map<String, Long> terminalByModel,
        Map<Integer, Long> terminalByStatusCode,
        Map<String, Long> failuresByExceptionType) {
    /**
     * Creates a snapshot and defensively copies map fields.
     *
     * @param startedCount number of request attempts started
     * @param succeededCount number of request attempts completed successfully
     * @param failedCount number of request attempts that failed
     * @param totalDuration total observed terminal duration
     * @param promptTokens prompt token total
     * @param completionTokens completion token total
     * @param totalTokens token total
     * @param startedByOperation started attempts grouped by operation
     * @param succeededByOperation successful attempts grouped by operation
     * @param failedByOperation failed attempts grouped by operation
     * @param terminalByModel terminal attempts grouped by model
     * @param terminalByStatusCode terminal attempts grouped by HTTP status code
     * @param failuresByExceptionType failures grouped by exception type
     */
    public AiMetricsSnapshot {
        totalDuration = totalDuration == null ? Duration.ZERO : totalDuration;
        startedByOperation = Map.copyOf(startedByOperation);
        succeededByOperation = Map.copyOf(succeededByOperation);
        failedByOperation = Map.copyOf(failedByOperation);
        terminalByModel = Map.copyOf(terminalByModel);
        terminalByStatusCode = Map.copyOf(terminalByStatusCode);
        failuresByExceptionType = Map.copyOf(failuresByExceptionType);
    }
}
