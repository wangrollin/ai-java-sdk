package io.wangrollin.ai.chat;

import java.util.Objects;

/**
 * Tool-selection policy for providers that support chat tool calling.
 *
 * @param mode provider-neutral selection mode
 * @param functionName function name when {@code mode} is {@code function}
 */
public record ChatToolChoice(String mode, String functionName) {
    private static final String AUTO = "auto";
    private static final String NONE = "none";
    private static final String REQUIRED = "required";
    private static final String FUNCTION = "function";

    /**
     * Creates a tool choice after validating mode-specific fields.
     *
     * @param mode selection mode
     * @param functionName optional function name for explicit function selection
     */
    public ChatToolChoice {
        mode = requireText(mode, "mode");
        if (FUNCTION.equals(mode)) {
            functionName = requireText(functionName, "functionName");
        } else if (functionName != null) {
            throw new IllegalArgumentException("functionName is only supported for function tool choice");
        } else if (!AUTO.equals(mode) && !NONE.equals(mode) && !REQUIRED.equals(mode)) {
            throw new IllegalArgumentException("mode must be auto, none, required, or function");
        }
    }

    /**
     * Lets the provider decide whether to call a tool.
     *
     * @return automatic tool choice
     */
    public static ChatToolChoice auto() {
        return new ChatToolChoice(AUTO, null);
    }

    /**
     * Prevents the provider from calling tools for this request.
     *
     * @return no-tool choice
     */
    public static ChatToolChoice none() {
        return new ChatToolChoice(NONE, null);
    }

    /**
     * Requires the provider to call at least one available tool.
     *
     * @return required-tool choice
     */
    public static ChatToolChoice required() {
        return new ChatToolChoice(REQUIRED, null);
    }

    /**
     * Requires the provider to call a specific function tool.
     *
     * @param functionName function name to require
     * @return explicit function tool choice
     */
    public static ChatToolChoice function(String functionName) {
        return new ChatToolChoice(FUNCTION, functionName);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
