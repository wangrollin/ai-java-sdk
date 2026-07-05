package io.wangrollin.ai.internal.http;

import java.net.http.HttpRequest;
import java.util.Objects;

/**
 * Internal description of one SDK HTTP operation.
 *
 * <p>The request description is user-facing exception text; operation and model
 * are safe diagnostic dimensions emitted through lifecycle events.
 */
public record AiHttpRequestSpec(
        HttpRequest request,
        String requestDescription,
        String operation,
        String model,
        String path,
        boolean stream,
        String payloadBody) {
    public AiHttpRequestSpec {
        request = Objects.requireNonNull(request, "request must not be null");
        requestDescription = requireText(requestDescription, "requestDescription");
        operation = requireText(operation, "operation");
        model = requireText(model, "model");
        path = requireText(path, "path");
        payloadBody = requireText(payloadBody, "payloadBody");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
