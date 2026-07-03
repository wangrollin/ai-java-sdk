package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.testing.FakeAiClient;

/**
 * Shows how application tests can use the SDK contract without opening sockets.
 */
public final class FakeAiClientExample {
    private FakeAiClientExample() {
    }

    public static void main(String[] args) {
        AiChatClient client = FakeAiClient.builder()
                .chatResponse("Use small retry budgets and log only safe metadata.")
                .streamDeltas(
                        new ChatDelta("Use small retry budgets", null),
                        new ChatDelta(" and log only safe metadata.", "stop"))
                .build();

        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("How should I test AI retries?"))
                .build();

        System.out.println(client.chat(request).text());
        try (ChatStream stream = client.stream(request)) {
            for (ChatDelta delta : stream) {
                System.out.print(delta.text());
            }
        }
    }
}
