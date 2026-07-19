package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.embedding.EmbeddingUsage;
import io.wangrollin.ai.error.AiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiEmbeddingCodecTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OpenAiEmbeddingCodec codec = new OpenAiEmbeddingCodec();

    @Test
    void serializesBatchInputAndDimensions() throws Exception {
        JsonNode json = OBJECT_MAPPER.readTree(codec.serializeRequest(EmbeddingRequest.builder()
                .inputs(List.of("alpha", "beta"))
                .dimensions(128)
                .build(), "default-model"));

        assertEquals("default-model", json.path("model").asText());
        assertEquals("alpha", json.path("input").path(0).asText());
        assertEquals("beta", json.path("input").path(1).asText());
        assertEquals(128, json.path("dimensions").asInt());
        assertEquals("float", json.path("encoding_format").asText());
    }

    @Test
    void parsesAndOrdersEmbeddingResult() {
        EmbeddingResult result = codec.parseResult("""
                {
                  "model": "text-embedding-test",
                  "data": [
                    {"index": 1, "embedding": [0.3, 0.4]},
                    {"index": 0, "embedding": [0.1, 0.2]}
                  ],
                  "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """);

        assertEquals("text-embedding-test", result.model());
        assertEquals(List.of(
                new Embedding(0, List.of(0.1, 0.2)),
                new Embedding(1, List.of(0.3, 0.4))), result.embeddings());
        assertEquals(new EmbeddingUsage(5, 5), result.usage());
    }

    @Test
    void rejectsMalformedVectorData() {
        assertThrows(AiException.class, () -> codec.parseResult("""
                {"data":[{"index":0,"embedding":["not-a-number"]}]}
                """));
    }
}
