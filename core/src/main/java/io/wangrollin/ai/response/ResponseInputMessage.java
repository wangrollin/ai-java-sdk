package io.wangrollin.ai.response;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One Responses API input message composed from typed content parts.
 *
 * <p>Only user messages are exposed for the first multimodal slice. That keeps
 * the API narrow until the SDK supports richer Responses conversation state and
 * tool-result flows deliberately.
 *
 * @param role message role
 * @param content ordered content parts
 */
public record ResponseInputMessage(String role, List<ResponseInputPart> content) implements ResponseInputItem {
    private static final String USER_ROLE = "user";

    /**
     * Creates a user message from one or more content parts.
     *
     * @param parts content parts to send in order
     * @return user input message
     */
    public static ResponseInputMessage user(ResponseInputPart... parts) {
        Objects.requireNonNull(parts, "parts must not be null");
        return user(Arrays.asList(parts));
    }

    /**
     * Creates a user message from one or more content parts.
     *
     * @param parts content parts to send in order
     * @return user input message
     */
    public static ResponseInputMessage user(List<ResponseInputPart> parts) {
        return new ResponseInputMessage(USER_ROLE, parts);
    }

    /**
     * Validates the message role and freezes content parts.
     *
     * @param role message role
     * @param content ordered content parts
     */
    public ResponseInputMessage {
        if (!USER_ROLE.equals(role)) {
            throw new IllegalArgumentException("role must be user");
        }
        Objects.requireNonNull(content, "content must not be null");
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        content = List.copyOf(content);
        content.forEach(part -> Objects.requireNonNull(part, "content must not contain null parts"));
    }
}
