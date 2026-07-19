package io.wangrollin.ai.client;

import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.error.AiException;

/**
 * Minimal synchronous embedding contract for retrieval and similarity workflows.
 */
public interface AiEmbeddingClient {
    /**
     * Generates one embedding per request input.
     *
     * @param request embedding request to send
     * @return vectors ordered by input index
     * @throws AiException when transport, provider, or response parsing fails
     */
    EmbeddingResult embed(EmbeddingRequest request);
}
