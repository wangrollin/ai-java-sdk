package io.wangrollin.ai.internal.protocol;

/**
 * Provider-neutral structured-output hint.
 *
 * @param type provider-compatible format type such as {@code json_object} or {@code json_schema}
 * @param name optional schema name
 * @param description optional schema description
 * @param schemaJson optional JSON schema object
 * @param strict optional strict-schema hint
 */
public record AiOutputFormat(String type, String name, String description, String schemaJson, Boolean strict) {
}
