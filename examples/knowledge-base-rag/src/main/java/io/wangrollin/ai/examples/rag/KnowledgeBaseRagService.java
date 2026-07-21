package io.wangrollin.ai.examples.rag;

import io.wangrollin.ai.chat.ChatMessage;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiEmbeddingClient;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * End-to-end retrieval-augmented generation workflow using an in-memory index.
 *
 * <p>The index is intentionally example-owned rather than an SDK abstraction:
 * production applications can replace it with their vector database without
 * coupling the core client API to one storage or chunking strategy.
 */
@Service
public class KnowledgeBaseRagService {
    private static final String SYSTEM_PROMPT = """
            Answer only from the supplied synthetic knowledge-base context.
            If the context is insufficient, say that the knowledge base does not contain the answer.
            """;

    private final AiEmbeddingClient embeddingClient;
    private final AiChatClient chatClient;
    private final RagProperties properties;
    private final List<KnowledgeDocument> documents;
    private List<IndexedDocument> index = List.of();

    public KnowledgeBaseRagService(
            AiEmbeddingClient embeddingClient,
            AiChatClient chatClient,
            RagProperties properties,
            SyntheticKnowledgeBase knowledgeBase) {
        this.embeddingClient = Objects.requireNonNull(embeddingClient, "embeddingClient must not be null");
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.documents = List.copyOf(Objects.requireNonNull(knowledgeBase, "knowledgeBase must not be null").documents());
    }

    @PostConstruct
    void initializeIndex() {
        EmbeddingResult result = embeddingClient.embed(EmbeddingRequest.builder()
                .model(properties.getEmbeddingModel())
                .inputs(documents.stream().map(KnowledgeDocument::content).toList())
                .build());
        if (result.embeddings().size() != documents.size()) {
            throw new RagWorkflowException("Embedding provider returned a different vector count than the document batch");
        }
        List<IndexedDocument> built = new ArrayList<>();
        for (Embedding embedding : result.embeddings()) {
            if (embedding.index() >= documents.size()) {
                throw new RagWorkflowException("Embedding provider returned an out-of-range document index");
            }
            built.add(new IndexedDocument(documents.get(embedding.index()), embedding.vector()));
        }
        index = List.copyOf(built);
    }

    public RagAnswer answer(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        EmbeddingResult queryResult = embeddingClient.embed(EmbeddingRequest.builder()
                .model(properties.getEmbeddingModel())
                .input(question)
                .build());
        List<Double> queryVector = queryResult.embeddings().get(0).vector();
        List<KnowledgeDocument> selected = index.stream()
                .map(document -> new ScoredDocument(document.document(), cosineSimilarity(queryVector, document.vector())))
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(Math.min(properties.getTopK(), index.size()))
                .map(ScoredDocument::document)
                .toList();

        String answer = chatClient.chat(ChatRequest.builder()
                .model(properties.getChatModel())
                .message(ChatMessage.system(SYSTEM_PROMPT))
                .message(ChatMessage.user(userPrompt(question, selected)))
                .temperature(0.1)
                .build()).text();
        return new RagAnswer(answer, selected.stream().map(KnowledgeDocument::id).toList());
    }

    private static String userPrompt(String question, List<KnowledgeDocument> documents) {
        StringBuilder context = new StringBuilder();
        for (KnowledgeDocument document : documents) {
            context.append("[source:").append(document.id()).append("] ")
                    .append(document.title()).append("\n")
                    .append(document.content()).append("\n\n");
        }
        return "Context:\n" + context + "Question:\n" + question;
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new RagWorkflowException("Query and document embeddings must have matching dimensions");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            throw new RagWorkflowException("Embedding vectors must have a non-zero magnitude");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record IndexedDocument(KnowledgeDocument document, List<Double> vector) {
    }

    private record ScoredDocument(KnowledgeDocument document, double score) {
    }
}
