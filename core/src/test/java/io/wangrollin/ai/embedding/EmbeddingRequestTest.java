package io.wangrollin.ai.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingRequestTest {
    @Test
    void buildsBatchRequestWithOptionalOverrides() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("embedding-model")
                .inputs(List.of("first", "second"))
                .dimensions(256)
                .build();

        assertEquals("embedding-model", request.model());
        assertEquals(List.of("first", "second"), request.inputs());
        assertEquals(256, request.dimensions());
    }

    @Test
    void rejectsEmptyOrInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingRequest.builder().build());
        assertThrows(IllegalArgumentException.class, () -> EmbeddingRequest.builder().input(" "));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingRequest.builder().input("ok").dimensions(0).build());
    }

    @Test
    void resultOrdersVectorsByProviderIndex() {
        EmbeddingResult result = new EmbeddingResult(
                "model",
                List.of(new Embedding(1, List.of(0.3)), new Embedding(0, List.of(0.1))),
                new EmbeddingUsage(3, 3));

        assertEquals(List.of(0, 1), result.embeddings().stream().map(Embedding::index).toList());
    }
}
