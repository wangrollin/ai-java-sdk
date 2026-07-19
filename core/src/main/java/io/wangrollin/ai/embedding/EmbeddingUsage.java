package io.wangrollin.ai.embedding;

/**
 * Token accounting metadata for an embedding request.
 *
 * @param inputTokens optional number of tokens consumed by all inputs
 * @param totalTokens optional total token count reported by the provider
 */
public record EmbeddingUsage(Integer inputTokens, Integer totalTokens) {
    /** Validates that provider token counts are non-negative when present. */
    public EmbeddingUsage {
        if (inputTokens != null && inputTokens < 0) {
            throw new IllegalArgumentException("inputTokens must not be negative");
        }
        if (totalTokens != null && totalTokens < 0) {
            throw new IllegalArgumentException("totalTokens must not be negative");
        }
    }
}
