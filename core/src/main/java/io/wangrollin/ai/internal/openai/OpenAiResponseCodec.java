package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.protocol.AiContentBlock;
import io.wangrollin.ai.internal.protocol.AiInputItem;
import io.wangrollin.ai.internal.protocol.AiOutputFormat;
import io.wangrollin.ai.internal.protocol.AiStreamEvent;
import io.wangrollin.ai.internal.protocol.AiToolCall;
import io.wangrollin.ai.internal.protocol.AiToolSpec;
import io.wangrollin.ai.internal.protocol.AiTurnRequest;
import io.wangrollin.ai.internal.protocol.AiTurnResult;
import io.wangrollin.ai.internal.protocol.AiUsage;
import io.wangrollin.ai.internal.protocol.ProtocolMapper;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Responses API JSON codec.
 */
public final class OpenAiResponseCodec {
    public static final String RESPONSES_PATH = "responses";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String serializeRequest(ResponseRequest request, String defaultModel, boolean stream) {
        return serializeRequest(ProtocolMapper.fromResponseRequest(request), defaultModel, stream);
    }

    public String serializeRequest(AiTurnRequest request, String defaultModel, boolean stream) {
        try {
            return objectMapper.writeValueAsString(payload(request, defaultModel, stream));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize response request", e);
        }
    }

    public ResponseResult parseResponse(String body) {
        return ProtocolMapper.toResponseResult(parseTurnResult(body));
    }

    public AiTurnResult parseTurnResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String text = outputText(root);
            List<AiToolCall> toolCalls = toolCalls(root.path("output"));
            if (text == null && toolCalls.isEmpty()) {
                throw new AiException("Response did not contain output text");
            }
            return new AiTurnResult(
                    text == null ? "" : text,
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    null,
                    optionalText(root.path("status")),
                    usage(root.path("usage")),
                    toolCalls);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse response", e);
        }
    }

    public ResponseDelta parseStreamDelta(String data) {
        return ProtocolMapper.toResponseDelta(parseStreamEvent(data));
    }

    public AiStreamEvent parseStreamEvent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = optionalText(root.path("type"));
            if ("response.output_text.delta".equals(type)) {
                return AiStreamEvent.text(optionalText(root.path("delta")));
            }
            if ("response.completed".equals(type)) {
                return new AiStreamEvent("", true, null, List.of());
            }
            if ("response.output_item.done".equals(type)) {
                AiToolCall toolCall = toolCall(root.path("item"));
                if (toolCall != null) {
                    return new AiStreamEvent("", false, null, List.of(toolCall));
                }
            }
            return new AiStreamEvent("", false, null, List.of());
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse response stream event", e);
        }
    }

    public String serializeStreamDelta(ResponseDelta delta) {
        try {
            if (delta.done()) {
                return objectMapper.writeValueAsString(Map.of("type", "response.completed"));
            }
            return objectMapper.writeValueAsString(Map.of(
                    "type", "response.output_text.delta",
                    "delta", delta.text()));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize fake response stream event", e);
        }
    }

    private Map<String, Object> payload(AiTurnRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", inputPayload(request));
        putIfPresent(payload, "instructions", request.instructions());
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        putIfPresent(payload, "max_output_tokens", request.maxOutputTokens());
        putIfPresent(payload, "text", textPayload(request.outputFormat()));
        putIfPresent(payload, "previous_response_id", request.previousResponseId());
        putIfPresent(payload, "background", request.background());
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(this::toolPayload).toList());
        }
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private Object inputPayload(AiTurnRequest request) {
        if (request.inputText() != null) {
            return request.inputText();
        }
        return request.inputItems().stream().map(OpenAiResponseCodec::inputItemPayload).toList();
    }

    private static Map<String, Object> inputItemPayload(AiInputItem item) {
        if (item.kind() == AiInputItem.Kind.TOOL_RESULT) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "function_call_output");
            payload.put("call_id", item.toolCallId());
            payload.put("output", item.toolOutput());
            return payload;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", item.role());
        payload.put("content", item.content().stream().map(OpenAiResponseCodec::inputPartPayload).toList());
        return payload;
    }

    private static Map<String, Object> inputPartPayload(AiContentBlock block) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (block.kind()) {
            case TEXT -> {
                payload.put("type", "input_text");
                payload.put("text", block.text());
            }
            case IMAGE_URL -> {
                payload.put("type", "input_image");
                payload.put("image_url", block.imageUrl());
                putIfPresent(payload, "detail", block.detail());
            }
            case IMAGE_FILE_ID -> {
                payload.put("type", "input_image");
                payload.put("file_id", block.fileId());
                putIfPresent(payload, "detail", block.detail());
            }
            case TOOL_RESULT -> {
                payload.put("type", "input_text");
                payload.put("text", block.text());
            }
        }
        return payload;
    }

    private Map<String, Object> toolPayload(AiToolSpec tool) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "function");
        payload.put("name", tool.name());
        putIfPresent(payload, "description", tool.description());
        payload.put("parameters", parseJsonObject(tool.parametersJson()));
        putIfPresent(payload, "strict", tool.strict());
        return payload;
    }

    private Map<String, Object> textPayload(AiOutputFormat outputFormat) {
        if (outputFormat == null) {
            return null;
        }
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", outputFormat.type());
        putIfPresent(format, "name", outputFormat.name());
        putIfPresent(format, "description", outputFormat.description());
        putIfPresent(format, "schema", parseJsonObject(outputFormat.schemaJson()));
        if ("json_schema".equals(outputFormat.type())) {
            format.put("strict", outputFormat.strict());
        }
        return Map.of("format", format);
    }

    private JsonNode parseJsonObject(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize response text format", e);
        }
    }

    private static String outputText(JsonNode root) {
        String direct = optionalText(root.path("output_text"));
        if (direct != null) {
            return direct;
        }
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                if ("output_text".equals(optionalText(contentItem.path("type")))) {
                    String value = optionalText(contentItem.path("text"));
                    if (value != null) {
                        text.append(value);
                    }
                }
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    private static AiUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new AiUsage(
                optionalInt(node.path("input_tokens")),
                optionalInt(node.path("output_tokens")),
                optionalInt(node.path("total_tokens")));
    }

    private static Integer optionalInt(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private static String optionalText(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static List<AiToolCall> toolCalls(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (JsonNode item : node) {
            AiToolCall toolCall = toolCall(item);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        return List.copyOf(toolCalls);
    }

    private static AiToolCall toolCall(JsonNode item) {
        if (!"function_call".equals(optionalText(item.path("type")))) {
            return null;
        }
        String callId = optionalText(item.path("call_id"));
        String name = optionalText(item.path("name"));
        if (callId == null || name == null) {
            return null;
        }
        return new AiToolCall(
                optionalText(item.path("id")),
                callId,
                name,
                optionalText(item.path("arguments")));
    }

    private static void putIfPresent(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }
}
