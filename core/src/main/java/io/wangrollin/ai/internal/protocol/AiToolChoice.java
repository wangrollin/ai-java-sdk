package io.wangrollin.ai.internal.protocol;

/**
 * Provider-neutral tool selection policy.
 *
 * @param mode selection mode such as {@code auto}, {@code none}, {@code required}, or {@code function}
 * @param functionName function name when {@code mode} is {@code function}
 */
public record AiToolChoice(String mode, String functionName) {
}
