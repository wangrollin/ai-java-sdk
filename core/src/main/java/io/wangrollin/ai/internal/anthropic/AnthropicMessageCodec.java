package io.wangrollin.ai.internal.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.error.AiError;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.protocol.AiContentBlock;
import io.wangrollin.ai.internal.protocol.AiInputItem;
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
 * Anthropic Claude Messages API JSON codec.
 *
 * <p>Claude's native content blocks and named SSE events are mapped to the internal
 * neutral protocol here, keeping Claude wire details out of public SDK types.
 */
public final class AnthropicMessageCodec {
    public static final String MESSAGES_PATH = "messages";

    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String TEXT_DELTA_EVENT = "content_block_delta";
    private static final String MESSAGE_DELTA_EVENT = "message_delta";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String serializeRequest(ChatRequest request, String defaultModel, boolean stream) {
        return serializeRequest(ProtocolMapper.fromChatRequest(request), defaultModel, stream);
    }

    public String serializeRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        try {
            return objectMapper.writeValueAsString(payload(request, defaultModel, stream));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize Anthropic chat request", e);
        }
    }

    public ChatResponse parseResponse(String body) {
        return ProtocolMapper.toChatResponse(parseTurnResult(body));
    }

    public AiTurnResult parseTurnResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<AiToolCall> toolCalls = toolCalls(root.path("content"));
            String text = textContent(root.path("content"));
            if (text.isEmpty() && toolCalls.isEmpty()) {
                throw new AiException("Anthropic response did not contain text or tool_use content");
            }
            return new AiTurnResult(
                    text,
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(root.path("stop_reason")),
                    null,
                    usage(root.path("usage")),
                    toolCalls);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse Anthropic chat response", e);
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
            AiError parsed = new AiError(optionalText(error.path("message")), optionalText(error.path("type")), null);
            if (parsed.message() == null && parsed.type() == null) {
                return null;
            }
            return parsed;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public ChatDelta parseStreamDelta(String event, String data) {
        return ProtocolMapper.toChatDelta(parseStreamEvent(event, data));
    }

    public AiStreamEvent parseStreamEvent(String event, String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            if (TEXT_DELTA_EVENT.equals(event)) {
                JsonNode delta = root.path("delta");
                if ("text_delta".equals(optionalText(delta.path("type")))) {
                    return AiStreamEvent.text(optionalText(delta.path("text")));
                }
            }
            if (MESSAGE_DELTA_EVENT.equals(event)) {
                return AiStreamEvent.done(optionalText(root.path("delta").path("stop_reason")));
            }
            return new AiStreamEvent("", false, null, List.of());
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse Anthropic chat stream event", e);
        }
    }

    private Map<String, Object> payload(AiTurnRequest request, String defaultModel, boolean stream) {
        if (request.outputFormat() != null) {
            throw new AiException("Anthropic chat does not support ChatResponseFormat; use prompting or tools instead");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.model() == null ? defaultModel : request.model());
        payload.put("max_tokens", request.maxOutputTokens() == null ? DEFAULT_MAX_TOKENS : request.maxOutputTokens());
        putIfPresent(payload, "system", systemText(request.inputItems()));
        payload.put("messages", messagePayloads(request.inputItems()));
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        if (!request.stopSequences().isEmpty()) {
            payload.put("stop_sequences", request.stopSequences());
        }
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(this::toolPayload).toList());
        }
        putIfPresent(payload, "tool_choice", toolChoicePayload(request.toolChoice()));
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private String systemText(List<AiInputItem> items) {
        List<String> systemMessages = items.stream()
                .filter(item -> item.kind() == AiInputItem.Kind.MESSAGE)
                .filter(item -> "system".equals(item.role()))
                .map(this::textContent)
                .filter(text -> !text.isBlank())
                .toList();
        return systemMessages.isEmpty() ? null : String.join("\n\n", systemMessages);
    }

    private List<Map<String, Object>> messagePayloads(List<AiInputItem> items) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (AiInputItem item : items) {
            if (item.kind() == AiInputItem.Kind.MESSAGE && "system".equals(item.role())) {
                continue;
            }
            if (item.kind() == AiInputItem.Kind.TOOL_RESULT) {
                payloads.add(Map.of("role", "user", "content", List.of(toolResultPayload(item))));
                continue;
            }
            if (!"user".equals(item.role()) && !"assistant".equals(item.role())) {
                throw new AiException("Anthropic chat supports only system, user, assistant, and tool messages");
            }
            payloads.add(Map.of("role", item.role(), "content", contentPayload(item)));
        }
        if (payloads.isEmpty()) {
            throw new AiException("Anthropic chat requests require at least one non-system message");
        }
        return payloads;
    }

    private Map<String, Object> toolResultPayload(AiInputItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "tool_result");
        result.put("tool_use_id", item.toolCallId());
        result.put("content", item.toolOutput());
        return result;
    }

    private List<Map<String, Object>> contentPayload(AiInputItem item) {
        return item.content().stream().map(block -> {
            if (block.kind() != AiContentBlock.Kind.TEXT) {
                throw new AiException("Anthropic chat adapter currently supports text message blocks only");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "text");
            payload.put("text", block.text());
            return payload;
        }).toList();
    }

    private String textContent(AiInputItem item) {
        return item.content().stream()
                .filter(block -> block.kind() == AiContentBlock.Kind.TEXT)
                .map(AiContentBlock::text)
                .reduce("", String::concat);
    }

    private Map<String, Object> toolPayload(AiToolSpec tool) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", tool.name());
        putIfPresent(payload, "description", tool.description());
        payload.put("input_schema", parseJsonObject(tool.parametersJson(), "Failed to serialize Anthropic tool"));
        return payload;
    }

    private Object toolChoicePayload(AiToolChoice toolChoice) {
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

    private List<AiToolCall> toolCalls(JsonNode content) {
        if (!content.isArray()) {
            return List.of();
        }
        List<AiToolCall> calls = new ArrayList<>();
        for (JsonNode block : content) {
            if ("tool_use".equals(optionalText(block.path("type")))) {
                String id = optionalText(block.path("id"));
                calls.add(new AiToolCall(id, id, optionalText(block.path("name")), compactJson(block.path("input"))));
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

    private AiUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        Integer inputTokens = optionalInt(node.path("input_tokens"));
        Integer outputTokens = optionalInt(node.path("output_tokens"));
        Integer totalTokens = inputTokens == null || outputTokens == null ? null : inputTokens + outputTokens;
        return new AiUsage(inputTokens, outputTokens, totalTokens);
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
