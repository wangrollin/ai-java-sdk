package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.event.AiMetricsSnapshot;
import io.wangrollin.ai.event.InMemoryAiMetricsListener;

/**
 * Collects safe request metrics without exposing prompts, responses, or credentials.
 */
public final class MetricsListenerExample {
    private MetricsListenerExample() {
    }

    public static void main(String[] args) {
        InMemoryAiMetricsListener metrics = InMemoryAiMetricsListener.create();
        AiChatClient client = AiClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .defaultModel("gpt-4.1-mini")
                .eventListener(metrics)
                .build();

        client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Give me one production-readiness check."))
                .build());

        AiMetricsSnapshot snapshot = metrics.snapshot();
        System.out.printf(
                "started=%d succeeded=%d failed=%d totalDuration=%s%n",
                snapshot.startedCount(),
                snapshot.succeededCount(),
                snapshot.failedCount(),
                snapshot.totalDuration());
    }
}
