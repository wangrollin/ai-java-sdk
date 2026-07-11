package io.wangrollin.ai.examples.triage;

import java.util.Objects;

/**
 * Structured triage output that downstream backend code can route without
 * parsing free-form assistant text.
 *
 * @param category product area or issue class inferred from the ticket
 * @param priority normalized operational priority
 * @param summary short safe summary for internal queues
 * @param queue suggested handling queue
 */
public record TriageResult(String category, String priority, String summary, String queue) {
    public TriageResult {
        category = requireText(category, "category");
        priority = requireText(priority, "priority");
        summary = requireText(summary, "summary");
        queue = requireText(queue, "queue");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
