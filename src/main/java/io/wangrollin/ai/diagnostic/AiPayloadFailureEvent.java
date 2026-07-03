package io.wangrollin.ai.diagnostic;

import java.net.URI;
import java.util.Objects;

/**
 * Redacted HTTP error payload metadata emitted by opt-in diagnostics.
 *
 * @param operation SDK operation such as {@code chat} or {@code stream}
 * @param model model selected for this request
 * @param baseUri configured provider base URI
 * @param path provider endpoint path
 * @param stream whether this response is for a streaming request
 * @param statusCode HTTP status code
 * @param redactedBody error response body after applying the configured redaction policy
 */
public record AiPayloadFailureEvent(
        String operation,
        String model,
        URI baseUri,
        String path,
        boolean stream,
        int statusCode,
        String redactedBody) {
    /**
     * Creates a failure payload diagnostic after validating required metadata.
     *
     * @param operation SDK operation
     * @param model selected model
     * @param baseUri configured provider base URI
     * @param path provider endpoint path
     * @param stream whether streaming is enabled
     * @param statusCode HTTP status code
     * @param redactedBody redacted failure body
     */
    public AiPayloadFailureEvent {
        operation = AiPayloadRequestEvent.requireText(operation, "operation");
        model = AiPayloadRequestEvent.requireText(model, "model");
        baseUri = Objects.requireNonNull(baseUri, "baseUri must not be null");
        path = AiPayloadRequestEvent.requireText(path, "path");
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status");
        }
        redactedBody = AiPayloadRequestEvent.requireText(redactedBody, "redactedBody");
    }
}
