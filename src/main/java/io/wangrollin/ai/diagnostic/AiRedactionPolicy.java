package io.wangrollin.ai.diagnostic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redacts provider payloads before they are emitted through opt-in diagnostics.
 *
 * <p>The default policy is intentionally conservative: message content, tool
 * arguments, error messages, and authorization-like fields are replaced even
 * though diagnostics are disabled unless an application explicitly enables
 * them. Malformed JSON is never emitted verbatim.
 */
public final class AiRedactionPolicy {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED = "<redacted>";
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "authorization",
            "api_key",
            "apikey",
            "access_token",
            "refresh_token",
            "token",
            "secret",
            "content",
            "arguments",
            "message");

    private AiRedactionPolicy() {
    }

    /**
     * Creates the default conservative redaction policy.
     *
     * @return redaction policy
     */
    public static AiRedactionPolicy defaultPolicy() {
        return new AiRedactionPolicy();
    }

    /**
     * Returns redacted JSON suitable for controlled diagnostics.
     *
     * @param body raw provider payload
     * @return redacted payload or a redacted parse-failure marker
     */
    public String redactJson(String body) {
        if (body == null || body.isBlank()) {
            return "<empty body>";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            redact(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return "{\"redacted\":\"malformed_json\"}";
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode child = objectNode.get(fieldName);
                if (isSensitive(fieldName)) {
                    objectNode.put(fieldName, REDACTED);
                    continue;
                }
                redact(child);
            }
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            for (JsonNode item : arrayNode) {
                redact(item);
            }
        }
    }

    private static boolean isSensitive(String fieldName) {
        return SENSITIVE_FIELD_NAMES.contains(fieldName.toLowerCase());
    }
}
