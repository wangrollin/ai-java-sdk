package io.wangrollin.ai.examples.triage;

import io.wangrollin.ai.chat.ChatResponseFormat;

final class TriageSchemas {
    static final ChatResponseFormat SUPPORT_TICKET_TRIAGE = ChatResponseFormat.jsonSchema(
            "support_ticket_triage",
            """
                    {
                      "type": "object",
                      "properties": {
                        "category": { "type": "string" },
                        "priority": { "type": "string", "enum": ["low", "medium", "high", "urgent"] },
                        "summary": { "type": "string" },
                        "queue": { "type": "string" }
                      },
                      "required": ["category", "priority", "summary", "queue"],
                      "additionalProperties": false
                    }
                    """);

    private TriageSchemas() {
    }
}
