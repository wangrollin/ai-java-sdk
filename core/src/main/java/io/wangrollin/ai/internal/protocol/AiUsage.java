package io.wangrollin.ai.internal.protocol;

/**
 * Provider-neutral token accounting.
 *
 * @param inputTokens prompt/input token count
 * @param outputTokens completion/output token count
 * @param totalTokens total token count
 */
public record AiUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
}
