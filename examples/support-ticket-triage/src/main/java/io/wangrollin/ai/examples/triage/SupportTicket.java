package io.wangrollin.ai.examples.triage;

import java.util.Objects;

/**
 * Input collected from a backend support system before AI-assisted triage.
 *
 * @param id stable ticket identifier used by application code
 * @param title short human-authored ticket title
 * @param body detailed customer report
 * @param customerTier business tier used only as triage context
 */
public record SupportTicket(String id, String title, String body, String customerTier) {
    public SupportTicket {
        id = requireText(id, "id");
        title = requireText(title, "title");
        body = requireText(body, "body");
        customerTier = requireText(customerTier, "customerTier");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
