package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;

import java.time.Duration;

/**
 * Minimal synchronous chat example for an OpenAI-compatible endpoint.
 */
public final class BasicChatExample {
    private BasicChatExample() {
    }

    public static void main(String[] args) {
        AiChatClient client = AiClient.builder()
                // Read credentials at runtime so examples never encourage committing secrets.
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultModel("gpt-4.1-mini")
                .timeout(Duration.ofSeconds(30))
                .build();

        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.system("Answer with concise engineering guidance."))
                .message(ChatMessage.user("How should I handle retries for AI calls?"))
                .build());

        System.out.println(response.text());
    }
}
