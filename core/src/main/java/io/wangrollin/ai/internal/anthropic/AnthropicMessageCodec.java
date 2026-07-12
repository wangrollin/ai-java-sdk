package io.wangrollin.ai.internal.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatTool;
import io.wangrollin.ai.chat.ChatToolCall;
import io.wangrollin.ai.chat.ChatToolChoice;
import io.wangrollin.ai.chat.ChatUsage;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Claude Messages API JSON codec.
 *
 * <p>This codec deliberately maps only the provider-neutral chat surface that
 * the SDK can represent faithfully. Claude-specific conversation features stay
 * internal until the public API has a stable abstraction for them.
 */
public final class AnthropicMessageCodec {
    public static final String MESSAGES_PATH = "messages";

    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String TEXT_DELTA_EVENT = "content_block_delta";
    private static final String MESSAGE_DELTA_EVENT = "message_delta";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes a chat request into the Claude Messages payload.
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
            throw new AiException("Failed to serialize Anthropic chat request", e);
        }
    }

    /**
     * Parses a non-streaming Claude Messages response.
     *
     * @param body JSON response body
     * @return SDK chat response
     */
    public ChatResponse parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<ChatToolCall> toolCalls = toolCalls(root.path("content"));
            String text = textContent(root.path("content"));
            if (text.isEmpty() && toolCalls.isEmpty()) {
                throw new AiException("Anthropic response did not contain text or tool_use content");
            }
            return new ChatResponse(
                    text,
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(root.path("stop_reason")),
                    usage(root.path("usage")),
                    toolCalls);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse Anthropic chat response", e);
        }
    }

    /**
     * Parses structured Anthropic error details when available.
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
                    null);
            if (parsed.message() == null && parsed.type() == null) {
                return null;
            }
            return parsed;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Parses one Claude streaming event.
     *
     * @param event SSE event name
     * @param data raw SSE data value
     * @return SDK chat delta
     */
    public ChatDelta parseStreamDelta(String event, String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if (TEXT_DELTA_EVENT.equals(event)) {
                JsonNode delta = root.path("delta");
                if ("text_delta".equals(optionalText(delta.path("type")))) {
                    return new ChatDelta(optionalText(delta.path("text")), null);
                }
            }
            if (MESSAGE_DELTA_EVENT.equals(event)) {
                return new ChatDelta("", optionalText(root.path("delta").path("stop_reason")));
            }
            return new ChatDelta("", null);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse Anthropic chat stream event", e);
        }
    }

    private Map<String, Object> payload(ChatRequest request, String defaultModel, boolean stream) {
        if (request.responseFormat() != null) {
            throw new AiException("Anthropic chat does not support ChatResponseFormat; use prompting or tools instead");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model() == null ? defaultModel : request.model());
        payload.put("max_tokens", request.maxTokens() == null ? DEFAULT_MAX_TOKENS : request.maxTokens());
        putIfPresent(payload, "system", systemText(request.messages()));
        payload.put("messages", messagePayloads(request.messages()));
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        if (!request.stopSequences().isEmpty()) {
            payload.put("stop_sequences", request.stopSequences());
        }
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

    private String systemText(List<ChatMessage> messages) {
        List<String> systemMessages = messages.stream()
                .filter(message -> "system".equals(message.role()))
                .map(ChatMessage::content)
                .toList();
        return systemMessages.isEmpty() ? null : String.join("\n\n", systemMessages);
    }

    private List<Map<String, Object>> messagePayloads(List<ChatMessage> messages) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (ChatMessage message : messages) {
            if ("system".equals(message.role())) {
                continue;
            }
            if ("tool".equals(message.role())) {
                payloads.add(toolResultMessagePayload(message));
                continue;
            }
            if (!"user".equals(message.role()) && !"assistant".equals(message.role())) {
                throw new AiException("Anthropic chat supports only system, user, assistant, and tool messages");
            }
            payloads.add(Map.of(
                    "role", message.role(),
                    "content", List.of(Map.of("type", "text", "text", message.content()))));
        }
        if (payloads.isEmpty()) {
            throw new AiException("Anthropic chat requests require at least one non-system message");
        }
        return payloads;
    }

    private Map<String, Object> toolResultMessagePayload(ChatMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "tool_result");
        result.put("tool_use_id", message.toolCallId());
        result.put("content", message.content());
        return Map.of("role", "user", "content", List.of(result));
    }

    private Map<String, Object> toolPayload(ChatTool tool) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", tool.name());
        putIfPresent(payload, "description", tool.description());
        payload.put("input_schema", parseJsonObject(tool.parametersJson(), "Failed to serialize Anthropic tool"));
        return payload;
    }

    private Object toolChoicePayload(ChatToolChoice toolChoice) {
        if (toolChoice == null) {
            return null;
        }
        return switch (toolChoice.mode()) {
            case "auto" -> Map.of("type", "auto");
            case "none" -> Map.of("type", "none");
            case "required" -> Map.of("type", "any");
            case "function" -> Map.of("type", "tool", "name", toolChoice.functionName());
            default -> throw new AiException("Unsupported Anthropic tool choice: " + toolChoice.mode());
        };
    }

    private JsonNode parseJsonObject(String json, String errorMessage) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiException(errorMessage, e);
        }
    }

    private String textContent(JsonNode content) {
        if (!content.isArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode block : content) {
            if ("text".equals(optionalText(block.path("type"))) && block.path("text").isTextual()) {
                parts.add(block.path("text").asText());
            }
        }
        return String.join("", parts);
    }

    private List<ChatToolCall> toolCalls(JsonNode content) {
        if (!content.isArray()) {
            return List.of();
        }
        List<ChatToolCall> calls = new ArrayList<>();
        for (JsonNode block : content) {
            if ("tool_use".equals(optionalText(block.path("type")))) {
                calls.add(new ChatToolCall(
                        optionalText(block.path("id")),
                        optionalText(block.path("name")),
                        compactJson(block.path("input"))));
            }
        }
        return calls;
    }

    private String compactJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse Anthropic tool input", e);
        }
    }

    private ChatUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        Integer inputTokens = optionalInt(node.path("input_tokens"));
        Integer outputTokens = optionalInt(node.path("output_tokens"));
        Integer totalTokens = inputTokens == null || outputTokens == null ? null : inputTokens + outputTokens;
        return new ChatUsage(inputTokens, outputTokens, totalTokens);
    }

    private static void putIfPresent(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }

    private static String optionalText(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static Integer optionalInt(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }
}
