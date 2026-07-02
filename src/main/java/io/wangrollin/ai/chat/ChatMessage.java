package io.wangrollin.ai.chat;

import java.util.Objects;

/**
 * Single OpenAI-compatible chat message.
 *
 * @param role provider role such as {@code system}, {@code user}, {@code assistant}, or {@code tool}
 * @param content message text sent to the provider
 * @param toolCallId provider tool-call identifier when sending a tool result
 */
public record ChatMessage(String role, String content, String toolCallId) {
    /**
     * Creates a message after validating that role and content are present.
     *
     * @param role provider role
     * @param content message text
     */
    public ChatMessage {
        role = requireText(role, "role");
        content = requireText(content, "content");
        toolCallId = normalizeOptionalText(toolCallId);
        if ("tool".equals(role) && toolCallId == null) {
            throw new IllegalArgumentException("toolCallId must not be blank for tool messages");
        }
        if (!"tool".equals(role) && toolCallId != null) {
            throw new IllegalArgumentException("toolCallId is only supported for tool messages");
        }
    }

    /**
     * Creates a regular message with no tool-call identifier.
     *
     * @param role provider role
     * @param content message text
     */
    public ChatMessage(String role, String content) {
        this(role, content, null);
    }

    /**
     * Creates a system instruction message.
     *
     * @param content instruction text
     * @return system message
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /**
     * Creates a user message.
     *
     * @param content user text
     * @return user message
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /**
     * Creates an assistant message, typically for conversation history.
     *
     * @param content assistant text
     * @return assistant message
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * Creates a tool result message for a prior model-requested tool call.
     *
     * @param toolCallId provider tool-call identifier returned by the model
     * @param content tool result content to send back to the model
     * @return tool result message
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
