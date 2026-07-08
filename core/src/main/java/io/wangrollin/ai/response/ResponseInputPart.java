package io.wangrollin.ai.response;

import java.util.Locale;
import java.util.Objects;

/**
 * One content part inside a Responses API input message.
 *
 * <p>The public type stays provider-neutral enough for application code while
 * still preserving the distinctions the OpenAI-compatible adapter must encode:
 * text, an image URL or data URL, and a previously uploaded provider file id.
 *
 * @param kind content part kind
 * @param text text value for text parts
 * @param imageUrl image URL or data URL for image URL parts
 * @param fileId provider file id for image file parts
 * @param detail optional image-detail hint
 */
public record ResponseInputPart(
        Kind kind,
        String text,
        String imageUrl,
        String fileId,
        ImageDetail detail) {
    /**
     * Creates a text content part.
     *
     * @param text text to send to the model
     * @return text input part
     */
    public static ResponseInputPart text(String text) {
        return new ResponseInputPart(Kind.TEXT, requireText(text, "text"), null, null, null);
    }

    /**
     * Creates an image content part from an HTTP URL or data URL.
     *
     * @param imageUrl image URL or data URL to send to the provider
     * @return image input part
     */
    public static ResponseInputPart imageUrl(String imageUrl) {
        return imageUrl(imageUrl, null);
    }

    /**
     * Creates an image content part from an HTTP URL or data URL with a detail hint.
     *
     * @param imageUrl image URL or data URL to send to the provider
     * @param detail optional image-detail hint
     * @return image input part
     */
    public static ResponseInputPart imageUrl(String imageUrl, ImageDetail detail) {
        return new ResponseInputPart(Kind.IMAGE_URL, null, requireText(imageUrl, "imageUrl"), null, detail);
    }

    /**
     * Creates an image content part from a provider file id.
     *
     * @param fileId provider file id for a previously uploaded image
     * @return image input part
     */
    public static ResponseInputPart imageFileId(String fileId) {
        return imageFileId(fileId, null);
    }

    /**
     * Creates an image content part from a provider file id with a detail hint.
     *
     * @param fileId provider file id for a previously uploaded image
     * @param detail optional image-detail hint
     * @return image input part
     */
    public static ResponseInputPart imageFileId(String fileId, ImageDetail detail) {
        return new ResponseInputPart(Kind.IMAGE_FILE_ID, null, null, requireText(fileId, "fileId"), detail);
    }

    /**
     * Validates that exactly the fields required by the selected part kind are present.
     *
     * @param kind input part kind
     * @param text text value for text parts
     * @param imageUrl URL or data URL for image URL parts
     * @param fileId provider file id for image file parts
     * @param detail optional image detail hint
     */
    public ResponseInputPart {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        text = normalizeRequiredForKind(kind == Kind.TEXT, text, "text");
        imageUrl = normalizeRequiredForKind(kind == Kind.IMAGE_URL, imageUrl, "imageUrl");
        fileId = normalizeRequiredForKind(kind == Kind.IMAGE_FILE_ID, fileId, "fileId");
        if (kind == Kind.TEXT) {
            detail = null;
        }
    }

    /**
     * Returns the provider wire value for the optional image-detail hint.
     *
     * @return lower-case provider value
     */
    public String detailValue() {
        return detail == null ? null : detail.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Public input part category.
     */
    public enum Kind {
        /**
         * Plain text content.
         */
        TEXT,

        /**
         * Image content referenced by URL or data URL.
         */
        IMAGE_URL,

        /**
         * Image content referenced by a provider file id.
         */
        IMAGE_FILE_ID
    }

    /**
     * Image-detail hint accepted by OpenAI-compatible Responses API providers.
     */
    public enum ImageDetail {
        /**
         * Let the provider choose the detail level.
         */
        AUTO,

        /**
         * Prefer lower-cost, lower-resolution image processing.
         */
        LOW,

        /**
         * Prefer higher-resolution image processing.
         */
        HIGH,

        /**
         * Request original image detail when supported by the provider.
         */
        ORIGINAL
    }

    private static String normalizeRequiredForKind(boolean required, String value, String name) {
        if (required) {
            return requireText(value, name);
        }
        return null;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
