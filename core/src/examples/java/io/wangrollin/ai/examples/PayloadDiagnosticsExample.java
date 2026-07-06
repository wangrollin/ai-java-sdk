package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.diagnostic.LoggingAiPayloadDiagnosticsListener;

/**
 * Enables opt-in redacted payload diagnostics for controlled troubleshooting.
 */
public final class PayloadDiagnosticsExample {
    private PayloadDiagnosticsExample() {
    }

    public static void main(String[] args) {
        AiChatClient client = AiClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultModel("gpt-4.1-mini")
                // Payload diagnostics are disabled by default; enable them only in controlled logs.
                .payloadDiagnosticsListener(LoggingAiPayloadDiagnosticsListener.create())
                .build();

        client.chat(ChatRequest.builder()
                .message(ChatMessage.user("Summarize the current deployment risk."))
                .build());
    }
}
