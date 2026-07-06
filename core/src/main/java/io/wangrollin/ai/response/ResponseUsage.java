package io.wangrollin.ai.response;

/**
 * Token usage metadata returned by Responses API providers when available.
 *
 * @param inputTokens optional input token count
 * @param outputTokens optional output token count
 * @param totalTokens optional total token count
 */
public record ResponseUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
}
