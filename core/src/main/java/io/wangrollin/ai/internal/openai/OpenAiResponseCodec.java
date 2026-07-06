package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseUsage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI-compatible Responses API JSON codec.
 */
public final class OpenAiResponseCodec {
    public static final String RESPONSES_PATH = "responses";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes a text-first Responses API request.
     *
     * @param request public SDK request
     * @param defaultModel model to use when the request does not override it
     * @param stream whether to request server-sent stream events
     * @return JSON request body
     */
    public String serializeRequest(ResponseRequest request, String defaultModel, boolean stream) {
        try {
            return objectMapper.writeValueAsString(payload(request, defaultModel, stream));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize response request", e);
        }
    }

    /**
     * Parses a complete Responses API response.
     *
     * @param body JSON response body
     * @return SDK response result
     */
    public ResponseResult parseResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String text = outputText(root);
            if (text == null) {
                throw new AiException("Response did not contain output text");
            }
            return new ResponseResult(
                    text,
                    optionalText(root.path("id")),
                    optionalText(root.path("model")),
                    optionalText(root.path("status")),
                    usage(root.path("usage")));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse response", e);
        }
    }

    /**
     * Parses one Responses API server-sent event data field.
     *
     * @param data raw SSE data value, excluding the {@code data:} prefix
     * @return SDK response delta
     */
    public ResponseDelta parseStreamDelta(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = optionalText(root.path("type"));
            if ("response.output_text.delta".equals(type)) {
                return new ResponseDelta(optionalText(root.path("delta")), false);
            }
            if ("response.completed".equals(type)) {
                return new ResponseDelta("", true);
            }
            return new ResponseDelta("", false);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse response stream event", e);
        }
    }

    /**
     * Serializes a fake stream delta using the same event shape consumed by the parser.
     *
     * @param delta delta to encode
     * @return JSON SSE data value
     */
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

    private Map<String, Object> payload(ResponseRequest request, String defaultModel, boolean stream) {
        String model = request.model() == null ? defaultModel : request.model();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", request.input());
        putIfPresent(payload, "instructions", request.instructions());
        putIfPresent(payload, "temperature", request.temperature());
        putIfPresent(payload, "top_p", request.topP());
        putIfPresent(payload, "max_output_tokens", request.maxOutputTokens());
        putIfPresent(payload, "text", textPayload(request.textFormat()));
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    private Map<String, Object> textPayload(ResponseTextFormat textFormat) {
        if (textFormat == null) {
            return null;
        }
        return Map.of("format", textFormatPayload(textFormat));
    }

    private Map<String, Object> textFormatPayload(ResponseTextFormat textFormat) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", textFormat.type());
        putIfPresent(payload, "name", textFormat.jsonSchemaName());
        putIfPresent(payload, "description", textFormat.jsonSchemaDescription());
        putIfPresent(payload, "schema", parseJsonObject(textFormat.jsonSchema()));
        if ("json_schema".equals(textFormat.type())) {
            payload.put("strict", textFormat.strict());
        }
        return payload;
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

    private static ResponseUsage usage(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new ResponseUsage(
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

    private static void putIfPresent(Map<String, Object> payload, String name, Object value) {
        if (value != null) {
            payload.put(name, value);
        }
    }
}
