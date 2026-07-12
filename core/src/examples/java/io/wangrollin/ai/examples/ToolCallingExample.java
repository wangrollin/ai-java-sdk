package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiClient;

/**
 * Demonstrates tool-call request plumbing while application code owns tool execution.
 */
public final class ToolCallingExample {
    private ToolCallingExample() {
    }

    public static void main(String[] args) {
        AiChatClient client = AiClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .defaultModel("gpt-4.1-mini")
                .build();

        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("What is the weather in Shanghai?"))
                .tool(ChatTool.function("lookup_weather", "Look up current weather by city.", """
                        {
                          "type": "object",
                          "properties": {
                            "city": { "type": "string" }
                          },
                          "required": ["city"],
                          "additionalProperties": false
                        }
                        """))
                .build());

        for (ChatToolCall toolCall : response.toolCalls()) {
            if ("lookup_weather".equals(toolCall.name())) {
                String resultJson = "{\"city\":\"Shanghai\",\"temperatureCelsius\":21}";
                ChatResponse finalResponse = client.chat(ChatRequest.builder()
                        .message(ChatMessage.user("What is the weather in Shanghai?"))
                        .message(ChatMessage.tool(toolCall.id(), resultJson))
                        .build());
                System.out.println(finalResponse.text());
            }
        }
    }
}
