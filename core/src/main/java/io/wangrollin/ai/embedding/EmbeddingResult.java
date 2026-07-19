package io.wangrollin.ai.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Complete result of a synchronous embedding request.
 *
 * @param model optional provider model name
 * @param embeddings vectors ordered by their input index
 * @param usage optional token accounting metadata
 */
public record EmbeddingResult(String model, List<Embedding> embeddings, EmbeddingUsage usage) {
    /** Validates and sorts vectors by their stable provider index. */
    public EmbeddingResult {
        Objects.requireNonNull(embeddings, "embeddings must not be null");
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("embeddings must not be empty");
        }
        List<Embedding> ordered = new ArrayList<>(embeddings);
        ordered.forEach(embedding -> Objects.requireNonNull(
                embedding, "embeddings must not contain null values"));
        ordered.sort(Comparator.comparingInt(Embedding::index));
        Set<Integer> indices = new HashSet<>();
        for (Embedding embedding : ordered) {
            if (!indices.add(embedding.index())) {
                throw new IllegalArgumentException("embedding indices must be unique");
            }
        }
        embeddings = List.copyOf(ordered);
        model = model == null || model.isBlank() ? null : model;
    }
}
