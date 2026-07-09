package io.wangrollin.ai.response;

/**
 * One item in the Responses API {@code input} list.
 *
 * <p>The Responses API accepts both user-visible messages and provider
 * conversation items such as function-call outputs. Keeping them behind this
 * small marker lets the SDK add new item kinds without falling back to raw maps
 * in application code.
 */
public sealed interface ResponseInputItem permits ResponseInputMessage, ResponseFunctionCallOutput {
}
