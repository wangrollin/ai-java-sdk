package io.wangrollin.ai.internal.protocol;

/**
 * Provider-neutral function tool call requested by the model.
 *
 * @param id optional provider item id
 * @param callId provider call id used for follow-up tool results
 * @param name function name
 * @param argumentsJson JSON argument object or stream fragment
 */
public record AiToolCall(String id, String callId, String name, String argumentsJson) {
}
