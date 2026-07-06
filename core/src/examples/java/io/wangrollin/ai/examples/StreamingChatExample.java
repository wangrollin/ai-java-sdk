package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;

/**
 * Streams incremental chat output and closes the response body with try-with-resources.
 */
public final class StreamingChatExample {
    private StreamingChatExample() {
    }

    public static void main(String[] args) {
        AiChatClient client = AiClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultModel("gpt-4.1-mini")
                .build();

        try (ChatStream stream = client.stream(ChatRequest.builder()
                .message(ChatMessage.user("Write a two sentence deployment checklist."))
                .build())) {
            for (ChatDelta delta : stream) {
                System.out.print(delta.text());
            }
        }
    }
}
