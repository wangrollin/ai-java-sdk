package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.protocol.AiInputItem;
import io.wangrollin.ai.internal.protocol.AiOutputFormat;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiToolCall;
import io.wangrollin.ai.internal.protocol.AiToolChoice;
import io.wangrollin.ai.internal.protocol.AiToolSpec;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;
import io.wangrollin.ai.internal.protocol.AiUsage;
import io.wangrollin.ai.internal.protocol.ProtocolMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completions JSON codec.
 *
 * <p>This class is intentionally internal: public SDK types are first mapped into the
 * provider-neutral turn protocol, and only this codec knows the final chat-completions wire shape.
 */
public final class OpenAiChatCodec {
    public static final String CHAT_COMPLETIONS_PATH = "chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String serializeRequest(ChatRequest request, String defaultModel, boolean stream) {
        return serializeRequest(ProtocolMapper.fromChatRequest(request), defaultModel, stream);
    }

    public String serializeRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        try {
            return objectMapper.writeValueAsString(payload(request, defaultModel, stream));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize chat request", e);
        }
    }

    public ChatResponse parseResponse(String body) {
        return ProtocolMapper.toChatResponse(parseTurnResult(body));
    }

    public AiTurnResult parseTurnResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            JsonNode content = message.path("content");
            List<AiToolCall> toolCalls = toolCalls(message.path("tool_calls"));
            if (!content.isTextual() && toolCalls.isEmpty()) {
                throw new AiException("Chat response did not contain choices[0].message.content");
            }
            return new AiTurnResult(
                    content.isTextual() ? content.asText() : "",
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(choice.path("finish_reason")),
                    null,
                    usage(root.path("usage")),
                    toolCalls);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat response", e);
        }
    }

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

    public ChatDelta parseStreamDelta(String data) {
        return ProtocolMapper.toChatDelta(parseStreamEvent(data));
    }

    public AiStreamEvent parseStreamEvent(String data) {
        try {
            JsonNode choice = objectMapper.readTree(data).path("choices").path(0);
            JsonNode content = choice.path("delta").path("content");
            JsonNode finishReason = choice.path("finish_reason");
            return new AiStreamEvent(
                    content.isTextual() ? content.asText() : "",
                    false,
                    finishReason.isTextual() ? finishReason.asText() : null,
                    toolCalls(choice.path("delta").path("tool_calls")));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse chat stream event", e);
        }
    }

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

    private Map<String, Object> payload(AiTurnRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", request.inputItems().stream()
                .map(OpenAiChatCodec::messagePayload)
                .toList());
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        putIfPresent(payload, "max_tokens", request.maxOutputTokens());
        if (!request.stopSequences().isEmpty()) {
            payload.put("stop", request.stopSequences());
        }
        putIfPresent(payload, "response_format", responseFormatPayload(request.outputFormat()));
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(this::toolPayload).toList());
        }
        putIfPresent(payload, "tool_choice", toolChoicePayload(request.toolChoice()));
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private Map<String, Object> responseFormatPayload(AiOutputFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", responseFormat.type());
        if ("json_schema".equals(responseFormat.type())) {
            Map<String, Object> jsonSchema = new LinkedHashMap<>();
            jsonSchema.put("name", responseFormat.name());
            jsonSchema.put("schema", parseJsonObject(
                    responseFormat.schemaJson(),
                    "Failed to serialize chat response format"));
            jsonSchema.put("strict", responseFormat.strict());
            payload.put("json_schema", jsonSchema);
        }
        return payload;
    }

    private Map<String, Object> toolPayload(AiToolSpec tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        putIfPresent(function, "description", tool.description());
        function.put("parameters", parseJsonObject(tool.parametersJson(), "Failed to serialize chat tool"));
        return Map.of("type", "function", "function", function);
    }

    private Object toolChoicePayload(AiToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        if ("function".equals(toolChoice.mode())) {
            return Map.of("type", "function", "function", Map.of("name", toolChoice.functionName()));
        }
        return toolChoice.mode();
    }

    private JsonNode parseJsonObject(String json, String errorMessage) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiException(errorMessage, e);
        }
    }

    private static Map<String, Object> messagePayload(AiInputItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (item.kind() == AiInputItem.Kind.TOOL_RESULT) {
            payload.put("role", "tool");
            payload.put("content", item.toolOutput());
            payload.put("tool_call_id", item.toolCallId());
            return payload;
        }
        payload.put("role", item.role());
        payload.put("content", item.content().isEmpty() ? "" : item.content().getFirst().text());
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

    private static AiUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new AiUsage(
                optionalInt(node.path("prompt_tokens")),
                optionalInt(node.path("completion_tokens")),
                optionalInt(node.path("total_tokens")));
    }

    private static Integer optionalInt(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private static List<AiToolCall> toolCalls(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<AiToolCall> calls = new ArrayList<>();
        for (JsonNode item : node) {
            JsonNode function = item.path("function");
            String name = optionalText(function.path("name"));
            if (name == null) {
                continue;
            }
            String id = optionalText(item.path("id"));
            calls.add(new AiToolCall(id, id, name, optionalText(function.path("arguments"))));
        }
        return calls;
    }

    private static List<Map<String, Object>> toolCallsPayload(List<ChatToolCall> toolCalls) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (int index = 0; index < toolCalls.size(); index++) {
            ChatToolCall toolCall = toolCalls.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            putIfPresent(item, "id", toolCall.id());
            item.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", toolCall.name());
            function.put("arguments", toolCall.argumentsJson());
            item.put("function", function);
            payloads.add(item);
        }
        return payloads;
    }
}
