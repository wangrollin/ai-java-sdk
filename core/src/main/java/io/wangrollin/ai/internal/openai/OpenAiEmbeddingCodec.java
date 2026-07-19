package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.embedding.EmbeddingUsage;
import io.wangrollin.ai.error.AiException;

import java.util.ArrayList;
import java.util.List;

/** Provider wire codec for the OpenAI-compatible Embeddings API. */
public final class OpenAiEmbeddingCodec {
    public static final String PATH = "embeddings";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String serializeRequest(EmbeddingRequest request, String defaultModel) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", request.model() == null ? defaultModel : request.model());
        ArrayNode input = root.putArray("input");
        request.inputs().forEach(input::add);
        if (request.dimensions() != null) {
            root.put("dimensions", request.dimensions());
        }
        root.put("encoding_format", "float");
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize embedding request", e);
        }
    }

    public EmbeddingResult parseResult(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new AiException("Embedding response data must be a non-empty array");
            }
            List<Embedding> embeddings = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode vectorNode = item.path("embedding");
                if (!item.path("index").canConvertToInt() || !vectorNode.isArray() || vectorNode.isEmpty()) {
                    throw new AiException("Embedding response item is missing index or vector data");
                }
                List<Double> vector = new ArrayList<>();
                for (JsonNode value : vectorNode) {
                    if (!value.isNumber()) {
                        throw new AiException("Embedding vector values must be numeric");
                    }
                    vector.add(value.doubleValue());
                }
                embeddings.add(new Embedding(item.path("index").intValue(), vector));
            }
            JsonNode usageNode = root.path("usage");
            EmbeddingUsage usage = usageNode.isObject()
                    ? new EmbeddingUsage(nullableInteger(usageNode, "prompt_tokens"), nullableInteger(usageNode, "total_tokens"))
                    : null;
            return new EmbeddingResult(nullableText(root, "model"), embeddings, usage);
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to parse embedding response", e);
        } catch (IllegalArgumentException e) {
            throw new AiException("Failed to parse embedding response", e);
        }
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToInt() ? value.intValue() : null;
    }
}
