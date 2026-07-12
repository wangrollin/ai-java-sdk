package io.wangrollin.ai.internal.protocol;

import java.util.Objects;

/**
 * Provider-neutral typed content block used between public SDK models and wire adapters.
 *
 * <p>The internal protocol intentionally models only stable SDK concepts. Provider-specific
 * JSON details are introduced later by the adapter that owns the target wire shape.
 *
 * @param kind block kind
 * @param text text value for text and tool-result blocks
 * @param imageUrl image URL or data URL for image URL blocks
 * @param fileId provider file id for image file blocks
 * @param detail optional image detail hint
 * @param toolCallId provider tool-call id for tool-result blocks
 */
public record AiContentBlock(
        Kind kind,
        String text,
        String imageUrl,
        String fileId,
        String detail,
        String toolCallId) {
    public AiContentBlock {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        text = normalize(text);
        imageUrl = normalize(imageUrl);
        fileId = normalize(fileId);
        detail = normalize(detail);
        toolCallId = normalize(toolCallId);
    }

    public static AiContentBlock text(String text) {
        return new AiContentBlock(Kind.TEXT, requireText(text, "text"), null, null, null, null);
    }

    public static AiContentBlock imageUrl(String imageUrl, String detail) {
        return new AiContentBlock(Kind.IMAGE_URL, null, requireText(imageUrl, "imageUrl"), null, detail, null);
    }

    public static AiContentBlock imageFileId(String fileId, String detail) {
        return new AiContentBlock(Kind.IMAGE_FILE_ID, null, null, requireText(fileId, "fileId"), detail, null);
    }

    public static AiContentBlock toolResult(String toolCallId, String output) {
        return new AiContentBlock(
                Kind.TOOL_RESULT,
                requireText(output, "output"),
                null,
                null,
                null,
                requireText(toolCallId, "toolCallId"));
    }

    public enum Kind {
        TEXT,
        IMAGE_URL,
        IMAGE_FILE_ID,
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
