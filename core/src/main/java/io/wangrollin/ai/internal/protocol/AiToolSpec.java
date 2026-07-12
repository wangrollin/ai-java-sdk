package io.wangrollin.ai.internal.protocol;

import java.util.Objects;

/**
 * Provider-neutral function tool definition.
 *
 * @param name function name
 * @param description optional description
 * @param parametersJson JSON schema object for function arguments
 * @param strict optional strict-schema hint
 */
public record AiToolSpec(String name, String description, String parametersJson, Boolean strict) {
    public AiToolSpec {
        name = requireText(name, "name");
        parametersJson = requireText(parametersJson, "parametersJson");
        description = description == null || description.isBlank() ? null : description;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
