package io.wangrollin.ai;

import java.util.Objects;

/**
 * Single OpenAI-compatible chat message.
 *
 * @param role provider role such as {@code system}, {@code user}, or {@code assistant}
 * @param content message text sent to the provider
 */
public record ChatMessage(String role, String content) {
    /**
     * Creates a message after validating that role and content are present.
     *
     * @param role provider role
     * @param content message text
     */
    public ChatMessage {
        role = requireText(role, "role");
        content = requireText(content, "content");
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

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
