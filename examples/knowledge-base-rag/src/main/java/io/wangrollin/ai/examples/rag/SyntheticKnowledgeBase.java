package io.wangrollin.ai.examples.rag;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Small synthetic corpus that keeps the example reproducible and prevents real
 * customer content from being copied into source control or test fixtures.
 */
@Component
public class SyntheticKnowledgeBase {
    public List<KnowledgeDocument> documents() {
        return List.of(
                new KnowledgeDocument("timeouts", "Request timeouts",
                        "Configure bounded connection and request timeouts for every provider call."),
                new KnowledgeDocument("retries", "Retry policy",
                        "Retry only transient failures with bounded attempts and exponential backoff."),
                new KnowledgeDocument("telemetry", "Safe telemetry",
                        "Record request metadata, latency, status, and token usage without prompts or outputs."));
    }
}
