package io.wangrollin.ai.internal.provider;

import java.util.Objects;

/**
 * Provider-specific HTTP request metadata derived from a public SDK request.
 *
 * @param operation SDK operation name used for events and diagnostics
 * @param path provider endpoint path with a leading slash
 * @param model model selected for the provider request
 * @param stream whether the request asks the provider for streaming output
 * @param body serialized provider request body
 */
public record ProviderRequestSpec(
        String operation,
        String path,
        String model,
        boolean stream,
        String body) {
    /**
     * Creates a provider request spec after validating required fields.
     *
     * @param operation SDK operation name
     * @param path provider endpoint path
     * @param model selected provider model
     * @param stream whether streaming is enabled
     * @param body serialized request body
     */
    public ProviderRequestSpec {
        operation = requireText(operation, "operation");
        path = requireText(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        model = requireText(model, "model");
        body = Objects.requireNonNull(body, "body must not be null");
    }

    /**
     * Returns the relative URI path used by {@link java.net.URI#resolve(String)}.
     *
     * @return path without the leading slash
     */
    public String relativePath() {
        return path.substring(1);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
