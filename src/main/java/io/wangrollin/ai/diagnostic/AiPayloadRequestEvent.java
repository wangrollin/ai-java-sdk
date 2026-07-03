package io.wangrollin.ai.diagnostic;

import java.net.URI;
import java.util.Objects;

/**
 * Redacted request payload metadata emitted by opt-in diagnostics.
 *
 * @param operation SDK operation such as {@code chat} or {@code stream}
 * @param model model selected for this request
 * @param baseUri configured provider base URI
 * @param path provider endpoint path
 * @param stream whether this request asks for streaming events
 * @param redactedBody request JSON after applying the configured redaction policy
 */
public record AiPayloadRequestEvent(
        String operation,
        String model,
        URI baseUri,
        String path,
        boolean stream,
        String redactedBody) {
    /**
     * Creates a request payload diagnostic after validating required metadata.
     *
     * @param operation SDK operation
     * @param model selected model
     * @param baseUri configured provider base URI
     * @param path provider endpoint path
     * @param stream whether streaming is enabled
     * @param redactedBody redacted request body
     */
    public AiPayloadRequestEvent {
        operation = requireText(operation, "operation");
        model = requireText(model, "model");
        baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        path = requireText(path, "path");
        redactedBody = requireText(redactedBody, "redactedBody");
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
