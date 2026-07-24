package io.wangrollin.ai.examples.rag;

import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.testing.FakeAiClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseRagServiceTest {
    @Test
    void indexesBatchRetrievesNearestDocumentsAndBuildsGroundedPrompt() {
        FakeAiClient client = FakeAiClient.builder()
                .embeddingResult(new EmbeddingResult("embedding-model", List.of(
                        new Embedding(0, List.of(0.0, 1.0)),
                        new Embedding(1, List.of(1.0, 0.0)),
                        new Embedding(2, List.of(0.8, 0.2))), null))
                .embeddingVector(1.0, 0.0)
                .chatResponse("Retry transient failures with bounded backoff.")
                .build();
        KnowledgeBaseRagService service = serviceUsing(client);

        service.initializeIndex();
        RagAnswer answer = service.answer("How should retries work?");

        assertEquals("Retry transient failures with bounded backoff.", answer.answer());
        assertEquals(List.of("retries", "telemetry"), answer.sourceIds());
        assertEquals(2, client.embeddingRequests().size());
        assertEquals(3, client.embeddingRequests().get(0).inputs().size());
        assertNull(client.embeddingRequests().get(0).model());
        String prompt = client.requests().get(0).messages().get(1).content();
        assertTrue(prompt.indexOf("[source:retries]") < prompt.indexOf("[source:telemetry]"));
        assertNull(client.requests().get(0).model());
    }

    @Test
    void failsWhenProviderReturnsWrongDocumentVectorCount() {
        FakeAiClient client = FakeAiClient.builder()
                .embeddingVector(1.0, 0.0)
                .build();

        RagWorkflowException failure = assertThrows(
                RagWorkflowException.class,
                () -> serviceUsing(client).initializeIndex());

        assertTrue(failure.getMessage().contains("different vector count"));
    }

    @Test
    void rejectsMismatchedQueryDimensions() {
        FakeAiClient client = FakeAiClient.builder()
                .embeddingResult(new EmbeddingResult("embedding-model", List.of(
                        new Embedding(0, List.of(0.0, 1.0)),
                        new Embedding(1, List.of(1.0, 0.0)),
                        new Embedding(2, List.of(0.8, 0.2))), null))
                .embeddingVector(1.0, 0.0, 0.0)
                .build();
        KnowledgeBaseRagService service = serviceUsing(client);
        service.initializeIndex();

        assertThrows(RagWorkflowException.class, () -> service.answer("question"));
    }

    private static KnowledgeBaseRagService serviceUsing(FakeAiClient client) {
        RagProperties properties = new RagProperties();
        properties.setTopK(2);
        return new KnowledgeBaseRagService(client, client, properties, new SyntheticKnowledgeBase());
    }
}
