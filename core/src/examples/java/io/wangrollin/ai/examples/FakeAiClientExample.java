package io.wangrollin.ai.examples;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.testing.FakeAiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shows application-level test patterns that use the SDK contract without API
 * keys, sockets, or provider availability.
 *
 * <p>The examples use small assertion helpers instead of a test framework so
 * this source stays compilable as part of the normal Maven build. Application
 * tests can express the same checks with JUnit, AssertJ, or their existing
 * assertion library.
 */
public final class FakeAiClientExample {
    private static final String TRIAGE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "queue": { "type": "string" }
              },
              "required": ["queue"],
              "additionalProperties": false
            }
            """;

    private FakeAiClientExample() {
    }

    public static void main(String[] args) {
        assertStructuredRequestAssembly();
        returnToolCallFixture();
        exerciseFallbackAfterFailure();
        distinguishStreamOpeningAndConsumptionFailures();
    }

    private static void assertStructuredRequestAssembly() {
        FakeAiClient fake = FakeAiClient.builder()
                .chatResponse("{\"queue\":\"billing\"}")
                .build();
        AiChatClient client = fake;
        ChatResponseFormat format = ChatResponseFormat.jsonSchema("ticket_triage", TRIAGE_SCHEMA);

        client.chat(ChatRequest.builder()
                .message(ChatMessage.system("Return only the requested JSON object."))
                .message(ChatMessage.user("The invoice export is failing."))
                .responseFormat(format)
                .build());

        ChatRequest recorded = fake.requests().get(0);
        requireEquals(format, recorded.responseFormat(), "structured output format");
        requireEquals(
                "The invoice export is failing.",
                recorded.messages().get(1).content(),
                "assembled user prompt");
    }

    private static void returnToolCallFixture() {
        ChatToolCall expectedCall = new ChatToolCall(
                "call-1",
                "lookup_account",
                "{\"accountId\":\"account-42\"}");
        FakeAiClient fake = FakeAiClient.builder()
                .chatResponse(new ChatResponse("", null, null, "tool_calls", null, List.of(expectedCall)))
                .build();

        ChatResponse response = fake.chat(ChatRequest.builder()
                .message(ChatMessage.user("Check account 42."))
                .tool(ChatTool.function(
                        "lookup_account",
                        "Look up an account by identifier.",
                        "{\"type\":\"object\",\"properties\":{\"accountId\":{\"type\":\"string\"}}}"))
                .build());

        requireEquals(List.of(expectedCall), response.toolCalls(), "tool-call fixture");
        requireEquals("lookup_account", fake.requests().get(0).tools().get(0).name(), "advertised tool");
    }

    private static void exerciseFallbackAfterFailure() {
        FakeAiClient fake = FakeAiClient.builder()
                .chatFailure(new AiException("primary model unavailable"))
                .chatResponse("fallback response")
                .build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Summarize the incident."))
                .build();

        String result;
        try {
            result = fake.chat(request).text();
        } catch (AiException failure) {
            // The second scripted outcome represents application-owned fallback
            // behavior; FakeAiClient itself deliberately does not hide failures.
            result = fake.chat(request).text();
        }

        requireEquals("fallback response", result, "fallback result");
        requireEquals(2, fake.requests().size(), "fallback attempt count");
    }

    private static void distinguishStreamOpeningAndConsumptionFailures() {
        FakeAiClient fake = FakeAiClient.builder()
                .streamFailure(new AiException("stream could not be opened"))
                .streamMalformedEvent("{\"choices\":[")
                .streamDeltas(new ChatDelta("recovered", "stop"))
                .build();
        ChatRequest request = ChatRequest.builder()
                .message(ChatMessage.user("Stream a short answer."))
                .build();

        expectAiException(() -> fake.stream(request), "stream opening failure");
        expectAiException(() -> {
            try (ChatStream stream = fake.stream(request)) {
                stream.iterator().hasNext();
            }
        }, "stream consumption failure");

        List<String> text = new ArrayList<>();
        try (ChatStream stream = fake.stream(request)) {
            for (ChatDelta delta : stream) {
                text.add(delta.text());
            }
        }
        requireEquals(List.of("recovered"), text, "stream recovery result");
    }

    private static void expectAiException(Runnable action, String description) {
        try {
            action.run();
            throw new IllegalStateException("Expected " + description);
        } catch (AiException expected) {
            // Expected failures are part of the scripted application test.
        }
    }

    private static void requireEquals(Object expected, Object actual, String description) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    "Unexpected " + description + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
