package io.wangrollin.ai;

/**
 * Token usage metadata returned by providers that report accounting details.
 *
 * @param promptTokens optional number of input tokens
 * @param completionTokens optional number of output tokens
 * @param totalTokens optional total token count
 */
public record ChatUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
