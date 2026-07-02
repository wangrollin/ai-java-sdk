package io.wangrollin.ai.event;

import java.net.URI;
import java.util.Objects;

/**
 * Safe metadata emitted before an SDK request attempt is sent.
 *
 * @param operation SDK operation such as {@code chat} or {@code stream}
 * @param model model selected for this request
 * @param baseUri configured provider base URI
 * @param path provider endpoint path
 * @param stream whether this attempt requests streaming events
 * @param attempt one-based attempt number
 */
public record AiRequestEvent(
        String operation,
        String model,
        URI baseUri,
        String path,
        boolean stream,
        int attempt) {
    /**
     * Creates a request event after validating required metadata.
     *
     * @param operation SDK operation
     * @param model selected model
     * @param baseUri configured provider base URI
     * @param path provider endpoint path
     * @param stream whether streaming is enabled
     * @param attempt one-based attempt number
     */
    public AiRequestEvent {
        operation = requireText(operation, "operation");
        model = requireText(model, "model");
        baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        path = requireText(path, "path");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
