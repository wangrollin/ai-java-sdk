package io.wangrollin.ai.internal.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicMessageCodecTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AnthropicMessageCodec codec = new AnthropicMessageCodec();

    @Test
    void serializesMessagesWithSystemAndDefaults() throws Exception {
        String body = codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.system("Be concise."))
                .message(ChatMessage.user("Hello"))
                .temperature(0.2)
                .stopSequence("END")
                .build(), "claude-test", false);

        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertEquals("claude-test", json.path("model").asText());
        assertEquals(1024, json.path("max_tokens").asInt());
        assertEquals("Be concise.", json.path("system").asText());
        assertEquals("user", json.path("messages").path(0).path("role").asText());
        assertEquals("text", json.path("messages").path(0).path("content").path(0).path("type").asText());
        assertEquals("Hello", json.path("messages").path(0).path("content").path(0).path("text").asText());
        assertEquals(0.2, json.path("temperature").asDouble());
        assertEquals("END", json.path("stop_sequences").path(0).asText());
        assertFalse(json.has("stream"));
    }

    @Test
    void serializesToolsToolChoiceAndToolResultMessages() throws Exception {
        String body = codec.serializeRequest(ChatRequest.builder()
                .model("claude-request")
                .message(ChatMessage.user("Use a tool"))
                .message(ChatMessage.tool("toolu_1", "{\"temperature\":21}"))
                .tool(ChatTool.function("lookup_weather", "Look up weather", """
                        {"type":"object","properties":{"city":{"type":"string"}}}
                        """))
                .toolChoice(ChatToolChoice.function("lookup_weather"))
                .maxTokens(256)
                .build(), "claude-default", true);

        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertEquals("claude-request", json.path("model").asText());
        assertEquals(256, json.path("max_tokens").asInt());
        assertTrue(json.path("stream").asBoolean());
        assertEquals("lookup_weather", json.path("tools").path(0).path("name").asText());
        assertEquals("object", json.path("tools").path(0).path("input_schema").path("type").asText());
        assertEquals("tool", json.path("tool_choice").path("type").asText());
        assertEquals("lookup_weather", json.path("tool_choice").path("name").asText());
        assertEquals("tool_result", json.path("messages").path(1).path("content").path(0).path("type").asText());
        assertEquals("toolu_1", json.path("messages").path(1).path("content").path(0).path("tool_use_id").asText());
    }

    @Test
    void parsesTextUsageAndToolCalls() {
        ChatResponse response = codec.parseResponse("""
                {
                  "id": "msg_123",
                  "model": "claude-test",
                  "stop_reason": "tool_use",
                  "usage": {"input_tokens": 7, "output_tokens": 11},
                  "content": [
                    {"type": "text", "text": "Need weather."},
                    {
                      "type": "tool_use",
                      "id": "toolu_1",
                      "name": "lookup_weather",
                      "input": {"city": "Shanghai"}
                    }
                  ]
                }
                """);

        assertEquals("Need weather.", response.text());
        assertEquals("msg_123", response.id());
        assertEquals("claude-test", response.model());
        assertEquals("tool_use", response.finishReason());
        assertEquals(new ChatUsage(7, 11, 18), response.usage());
        assertEquals(List.of(new ChatToolCall("toolu_1", "lookup_weather", "{\"city\":\"Shanghai\"}")),
                response.toolCalls());
    }

    @Test
    void parsesStreamingTextAndStopEvents() {
        ChatDelta text = codec.parseStreamDelta("content_block_delta", """
                {"delta":{"type":"text_delta","text":"Hel"}}
                """);
        ChatDelta stop = codec.parseStreamDelta("message_delta", """
                {"delta":{"stop_reason":"end_turn"}}
                """);
        ChatDelta ping = codec.parseStreamDelta("ping", "{}");

        assertEquals(new ChatDelta("Hel", null), text);
        assertEquals(new ChatDelta("", "end_turn"), stop);
        assertEquals(new ChatDelta("", null), ping);
    }

    @Test
    void parsesAnthropicErrorsAndRejectsUnsupportedShapes() {
        AiError error = codec.parseError("""
                {"error":{"type":"authentication_error","message":"invalid x-api-key"}}
                """);
        AiException responseFormat = assertThrows(AiException.class, () -> codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.user("JSON please"))
                .responseFormat(ChatResponseFormat.jsonObject())
                .build(), "claude-test", false));
        AiException onlySystem = assertThrows(AiException.class, () -> codec.serializeRequest(ChatRequest.builder()
                .message(ChatMessage.system("Only instructions"))
                .build(), "claude-test", false));

        assertEquals(new AiError("invalid x-api-key", "authentication_error", null), error);
        assertTrue(responseFormat.getMessage().contains("does not support ChatResponseFormat"));
        assertEquals("Anthropic chat requests require at least one non-system message", onlySystem.getMessage());
    }
}
