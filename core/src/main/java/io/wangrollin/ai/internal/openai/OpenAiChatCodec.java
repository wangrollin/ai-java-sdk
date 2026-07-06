package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatResponseFormat;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.chat.ChatUsage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions JSON codec.
 *
 * <p>This class is intentionally internal: public SDK types stay provider-neutral
 * while this package owns the wire shape needed by OpenAI-compatible endpoints.
 */
public final class OpenAiChatCodec {
    public static final String CHAT_COMPLETIONS_PATH = "chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes a chat request into the OpenAI-compatible chat completions payload.
     *
     * @param request chat request from the public SDK API
     * @param defaultModel model to use when the request does not override it
     * @param stream whether to request server-sent streaming events
     * @return JSON request body
     */
    public String serializeRequest(ChatRequest request, String defaultModel, boolean stream) {
        try {
            return objectMapper.writeValueAsString(payload(request, defaultModel, stream));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize chat request", e);
        }
    }

    /**
     * Parses a non-streaming OpenAI-compatible chat completion response.
     *
     * @param body JSON response body
     * @return SDK chat response
     */
    public ChatResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            JsonNode content = message.path("content");
            List<ChatToolCall> toolCalls = toolCalls(message.path("tool_calls"));
            if (!content.isTextual() && toolCalls.isEmpty()) {
                throw new AiException("Chat response did not contain choices[0].message.content");
            }
            return new ChatResponse(
                    content.isTextual() ? content.asText() : "",
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(choice.path("finish_reason")),
                    usage(root.path("usage")),
                    toolCalls);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat response", e);
        }
    }

    /**
     * Parses structured OpenAI-compatible error details, returning {@code null}
     * when the body is empty, malformed, or does not contain a recognizable
     * {@code error} object. HTTP error handling still uses the raw body summary
     * as a fallback so callers get a useful diagnostic either way.
     *
     * @param body JSON error response body
     * @return structured provider error details, or {@code null}
     */
    public AiError parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            if (!error.isObject()) {
                return null;
            }
            AiError parsed = new AiError(
                    optionalText(error.path("message")),
                    optionalText(error.path("type")),
                    optionalScalarText(error.path("code")));
            if (parsed.message() == null && parsed.type() == null && parsed.code() == null) {
                return null;
            }
            return parsed;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Parses one OpenAI-compatible server-sent event data field into a chat delta.
     *
     * @param data raw SSE data value, excluding the {@code data:} prefix
     * @return SDK chat delta
     */
    public ChatDelta parseStreamDelta(String data) {
        try {
            JsonNode choice = objectMapper.readTree(data).path("choices").path(0);
            JsonNode content = choice.path("delta").path("content");
            JsonNode finishReason = choice.path("finish_reason");
            return new ChatDelta(
                    content.isTextual() ? content.asText() : "",
                    finishReason.isTextual() ? finishReason.asText() : null,
                    toolCalls(choice.path("delta").path("tool_calls")));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat stream event", e);
        }
    }

    /**
     * Serializes a fake stream delta using the same event shape consumed by the parser.
     *
     * @param delta delta to encode
     * @return JSON SSE data value
     */
    public String serializeStreamDelta(ChatDelta delta) {
        try {
            Map<String, Object> choice = new LinkedHashMap<>();
            Map<String, Object> deltaPayload = new LinkedHashMap<>();
            deltaPayload.put("content", delta.text());
            if (!delta.toolCalls().isEmpty()) {
                deltaPayload.put("tool_calls", toolCallsPayload(delta.toolCalls()));
            }
            choice.put("delta", deltaPayload);
            choice.put("finish_reason", delta.finishReason());
            return objectMapper.writeValueAsString(Map.of("choices", List.of(choice)));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize fake stream event", e);
        }
    }

    private Map<String, Object> payload(ChatRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        List<Map<String, Object>> messages = request.messages().stream()
                .map(OpenAiChatCodec::messagePayload)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        putIfPresent(payload, "max_tokens", request.maxTokens());
        if (!request.stopSequences().isEmpty()) {
            payload.put("stop", request.stopSequences());
        }
        putIfPresent(payload, "response_format", responseFormatPayload(request.responseFormat()));
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream()
                    .map(this::toolPayload)
                    .toList());
        }
        putIfPresent(payload, "tool_choice", toolChoicePayload(request.toolChoice()));
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private Map<String, Object> responseFormatPayload(ChatResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", responseFormat.type());
        if ("json_schema".equals(responseFormat.type())) {
            Map<String, Object> jsonSchema = new LinkedHashMap<>();
            jsonSchema.put("name", responseFormat.jsonSchemaName());
            jsonSchema.put("schema", parseSchema(responseFormat.jsonSchema()));
            jsonSchema.put("strict", responseFormat.strict());
            payload.put("json_schema", jsonSchema);
        }
        return payload;
    }

    private Map<String, Object> toolPayload(ChatTool tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        putIfPresent(function, "description", tool.description());
        function.put("parameters", parseJsonObject(tool.parametersJson(), "Failed to serialize chat tool"));
        return Map.of("type", "function", "function", function);
    }

    private Object toolChoicePayload(ChatToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        if ("function".equals(toolChoice.mode())) {
            return Map.of(
                    "type", "function",
                    "function", Map.of("name", toolChoice.functionName()));
        }
        return toolChoice.mode();
    }

    private JsonNode parseSchema(String schemaJson) {
        return parseJsonObject(schemaJson, "Failed to serialize chat response format");
    }

    private JsonNode parseJsonObject(String json, String errorMessage) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiException(errorMessage, e);
        }
    }

    private static Map<String, Object> messagePayload(ChatMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", message.role());
        payload.put("content", message.content());
        putIfPresent(payload, "tool_call_id", message.toolCallId());
        return payload;
    }

    private static void putIfPresent(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }

    private static String optionalText(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static String optionalScalarText(JsonNode node) {
        return node.isTextual() || node.isNumber() || node.isBoolean() ? node.asText() : null;
    }

    private static ChatUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new ChatUsage(
                optionalInt(node.path("prompt_tokens")),
                optionalInt(node.path("completion_tokens")),
                optionalInt(node.path("total_tokens")));
    }

    private static Integer optionalInt(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private static List<ChatToolCall> toolCalls(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<ChatToolCall> toolCalls = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            JsonNode function = item.path("function");
            String name = optionalText(function.path("name"));
            if (name == null) {
                continue;
            }
            toolCalls.add(new ChatToolCall(
                    optionalText(item.path("id")),
                    name,
                    optionalText(function.path("arguments"))));
        }
        return toolCalls;
    }

    private static List<Map<String, Object>> toolCallsPayload(List<ChatToolCall> toolCalls) {
        return toolCalls.stream()
                .map(OpenAiChatCodec::toolCallPayload)
                .toList();
    }

    private static Map<String, Object> toolCallPayload(ChatToolCall toolCall) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolCall.name());
        function.put("arguments", toolCall.argumentsJson());
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "id", toolCall.id());
        payload.put("type", "function");
        payload.put("function", function);
        return payload;
    }
}
