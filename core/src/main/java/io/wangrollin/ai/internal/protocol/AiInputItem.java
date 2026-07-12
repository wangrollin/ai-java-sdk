package io.wangrollin.ai.internal.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral conversation/input item.
 *
 * @param kind item kind
 * @param role message role for message items
 * @param content typed message content
 * @param toolCallId tool-call id for tool-result items
 * @param toolOutput tool output for tool-result items
 */
public record AiInputItem(
        Kind kind,
        String role,
        List<AiContentBlock> content,
        String toolCallId,
        String toolOutput) {
    public AiInputItem {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        role = normalize(role);
        content = content == null ? List.of() : List.copyOf(content);
        toolCallId = normalize(toolCallId);
        toolOutput = normalize(toolOutput);
        content.forEach(block -> Objects.requireNonNull(block, "content must not contain null blocks"));
    }

    public static AiInputItem message(String role, List<AiContentBlock> content) {
        return new AiInputItem(Kind.MESSAGE, requireText(role, "role"), content, null, null);
    }

    public static AiInputItem toolResult(String toolCallId, String output) {
        return new AiInputItem(
                Kind.TOOL_RESULT,
                null,
                List.of(AiContentBlock.toolResult(toolCallId, output)),
                requireText(toolCallId, "toolCallId"),
                requireText(output, "output"));
    }

    public enum Kind {
        MESSAGE,
        TOOL_RESULT
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
