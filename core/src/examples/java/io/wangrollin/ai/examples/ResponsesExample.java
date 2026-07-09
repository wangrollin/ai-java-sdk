package io.wangrollin.ai.examples;

import io.wangrollin.ai.client.AiClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseInputMessage;
import io.wangrollin.ai.response.ResponseInputPart;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseTool;
import io.wangrollin.ai.response.ResponseToolCall;

/**
 * Minimal Responses API example for text, structured output, image input, tools, and streaming.
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

        ResponseResult structured = client.respond(ResponseRequest.builder()
                .input("Summarize this launch risk in one JSON object.")
                .textFormat(ResponseTextFormat.jsonSchema("risk_summary", """
                        {
                          "type": "object",
                          "properties": {
                            "risk": { "type": "string", "enum": ["low", "medium", "high"] },
                            "summary": { "type": "string" }
                          },
                          "required": ["risk", "summary"],
                          "additionalProperties": false
                        }
                        """))
                .build());
        System.out.println(structured.text());

        ResponseResult imageSummary = client.respond(ResponseRequest.builder()
                .inputMessage(ResponseInputMessage.user(
                        ResponseInputPart.text("Describe the operational risk visible in this image."),
                        ResponseInputPart.imageUrl(
                                "https://example.com/dashboard.png",
                                ResponseInputPart.ImageDetail.LOW)))
                .build());
        System.out.println(imageSummary.text());

        ResponseResult toolPlanning = client.respond(ResponseRequest.builder()
                .input("What is the weather in Shanghai?")
                .tool(ResponseTool.function("lookup_weather", "Look up current weather by city.", """
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
        for (ResponseToolCall toolCall : toolPlanning.toolCalls()) {
            if ("lookup_weather".equals(toolCall.name())) {
                ResponseResult finalResult = client.respond(ResponseRequest.builder()
                        .previousResponseId(toolPlanning.id())
                        .functionCallOutput(toolCall.callId(), "{\"temperatureCelsius\":21}")
                        .build());
                System.out.println(finalResult.text());
            }
        }

        try (ResponseStream stream = client.streamResponse(ResponseRequest.builder()
                .input("Give me two short rollout checks.")
                .build())) {
            for (ResponseDelta delta : stream) {
                System.out.print(delta.text());
            }
        }
    }
}
