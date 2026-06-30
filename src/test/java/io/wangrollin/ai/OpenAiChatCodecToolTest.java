package io.wangrollin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatCodecToolTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OpenAiChatCodec codec = new OpenAiChatCodec();

    @Test
    void serializesToolsAndAutoToolChoice() throws Exception {
        String body = codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.user("What is the weather?"))
                .tool(ChatTool.function("lookup_weather", "Look up current weather", """
                        {
                          "type": "object",
                          "properties": {
                            "city": { "type": "string" }
                          },
                          "required": ["city"],
                          "additionalProperties": false
                        }
                        """))
                .toolChoice(ChatToolChoice.auto())
                .build(), "test-model", false);

        JsonNode request = OBJECT_MAPPER.readTree(body);
        JsonNode tool = request.path("tools").path(0);
        assertEquals("function", tool.path("type").asText());
        assertEquals("lookup_weather", tool.path("function").path("name").asText());
        assertEquals("Look up current weather", tool.path("function").path("description").asText());
        assertEquals("object", tool.path("function").path("parameters").path("type").asText());
        assertEquals("auto", request.path("tool_choice").asText());
    }

    @Test
    void serializesMultipleToolsAndExplicitToolChoice() throws Exception {
        String body = codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.user("Find and book a room"))
                .tools(List.of(
                        ChatTool.function("search_rooms", "{\"type\":\"object\",\"properties\":{}}"),
                        ChatTool.function("book_room", "{\"type\":\"object\",\"properties\":{}}")))
                .toolChoice(ChatToolChoice.function("search_rooms"))
                .build(), "test-model", false);

        JsonNode request = OBJECT_MAPPER.readTree(body);
        assertEquals(2, request.path("tools").size());
        assertEquals("search_rooms", request.path("tools").path(0).path("function").path("name").asText());
        assertEquals("book_room", request.path("tools").path(1).path("function").path("name").asText());
        assertEquals("function", request.path("tool_choice").path("type").asText());
        assertEquals("search_rooms", request.path("tool_choice").path("function").path("name").asText());
    }

    @Test
    void serializesToolResultMessages() throws Exception {
        String body = codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.tool("call-1", "{\"temperature\":21}"))
                .build(), "test-model", false);

        JsonNode message = OBJECT_MAPPER.readTree(body).path("messages").path(0);
        assertEquals("tool", message.path("role").asText());
        assertEquals("call-1", message.path("tool_call_id").asText());
        assertEquals("{\"temperature\":21}", message.path("content").asText());
    }

    @Test
    void parsesToolCallsWithoutTextContent() {
        ChatResponse response = codec.parseResponse("""
                {
                  "id": "chatcmpl-tools",
                  "model": "test-model",
                  "choices": [
                    {
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call-1",
                            "type": "function",
                            "function": {
                              "name": "lookup_weather",
                              "arguments": "{\\"city\\":\\"Shanghai\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        assertEquals("", response.text());
        assertEquals("tool_calls", response.finishReason());
        assertEquals(List.of(new ChatToolCall("call-1", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                response.toolCalls());
    }

    @Test
    void parsesStreamingToolCallFragments() {
        ChatDelta delta = codec.parseStreamDelta("""
                {
                  "choices": [
                    {
                      "delta": {
                        "tool_calls": [
                          {
                            "id": "call-1",
                            "type": "function",
                            "function": {
                              "name": "lookup_weather",
                              "arguments": "{\\"city\\""
                            }
                          }
                        ]
                      },
                      "finish_reason": null
                    }
                  ]
                }
                """);

        assertEquals("", delta.text());
        assertEquals(List.of(new ChatToolCall("call-1", "lookup_weather", "{\"city\"")), delta.toolCalls());
    }

    @Test
    void validatesToolTypes() {
        IllegalArgumentException blankToolName = assertThrows(IllegalArgumentException.class, () ->
                ChatTool.function(" ", "{}"));
        IllegalArgumentException malformedParameters = assertThrows(IllegalArgumentException.class, () ->
                ChatTool.function("lookup_weather", "{"));
        IllegalArgumentException nonObjectParameters = assertThrows(IllegalArgumentException.class, () ->
                ChatTool.function("lookup_weather", "[]"));
        IllegalArgumentException blankChoiceFunction = assertThrows(IllegalArgumentException.class, () ->
                ChatToolChoice.function(" "));
        IllegalArgumentException toolMessageWithoutId = assertThrows(IllegalArgumentException.class, () ->
                new ChatMessage("tool", "{}", null));
        IllegalArgumentException userMessageWithToolId = assertThrows(IllegalArgumentException.class, () ->
                new ChatMessage("user", "Hello", "call-1"));

        assertEquals("name must not be blank", blankToolName.getMessage());
        assertEquals("parametersJson must be valid JSON", malformedParameters.getMessage());
        assertEquals("parametersJson must be a JSON object", nonObjectParameters.getMessage());
        assertEquals("functionName must not be blank", blankChoiceFunction.getMessage());
        assertEquals("toolCallId must not be blank for tool messages", toolMessageWithoutId.getMessage());
        assertEquals("toolCallId is only supported for tool messages", userMessageWithToolId.getMessage());
    }

    @Test
    void keepsToolCallCollectionsImmutable() {
        ChatResponse response = new ChatResponse("ok", null, null, null, null,
                List.of(new ChatToolCall("call-1", "lookup_weather", "{}")));
        ChatDelta delta = new ChatDelta("", null,
                List.of(new ChatToolCall("call-1", "lookup_weather", "{}")));

        assertThrows(UnsupportedOperationException.class, () -> response.toolCalls().clear());
        assertThrows(UnsupportedOperationException.class, () -> delta.toolCalls().clear());
        assertFalse(response.toolCalls().isEmpty());
        assertTrue(delta.text().isEmpty());
    }
}
