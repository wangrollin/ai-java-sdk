package io.wangrollin.ai.examples;

import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;

/**
 * Minimal text-first Responses API example.
 */
public final class ResponsesExample {
    private ResponsesExample() {
    }

    public static void main(String[] args) {
        AiResponseClient client = AiClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultModel("gpt-4.1-mini")
                .build();

        ResponseResult result = client.respond(ResponseRequest.builder()
                .instructions("Answer with concise engineering guidance.")
                .input("How should I prepare an AI SDK for production?")
                .build());
        System.out.println(result.text());

        try (ResponseStream stream = client.streamResponse(ResponseRequest.builder()
                .input("Give me two short rollout checks.")
                .build())) {
            for (ResponseDelta delta : stream) {
                System.out.print(delta.text());
            }
        }
    }
}
