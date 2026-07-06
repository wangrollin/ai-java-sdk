package io.wangrollin.ai.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRedactionPolicyTest {
    @Test
    void redactsSensitiveProviderPayloadFields() {
        AiRedactionPolicy policy = AiRedactionPolicy.defaultPolicy();

        String redacted = policy.redactJson("""
                {
                  "authorization": "Bearer test-key",
                  "messages": [
                    {
                      "role": "user",
                      "content": "secret prompt"
                    }
                  ],
                  "tool_calls": [
                    {
                      "function": {
                        "name": "lookup_weather",
                        "arguments": "{\\"city\\":\\"Shanghai\\"}"
                      }
                    }
                  ],
                  "error": {
                    "message": "raw provider detail",
                    "code": "rate_limit"
                  },
                  "input": "secret response input",
                  "output_text": "secret response output"
                }
                """);

        assertFalse(redacted.contains("test-key"));
        assertFalse(redacted.contains("secret prompt"));
        assertFalse(redacted.contains("Shanghai"));
        assertFalse(redacted.contains("raw provider detail"));
        assertFalse(redacted.contains("secret response input"));
        assertFalse(redacted.contains("secret response output"));
        assertTrue(redacted.contains("<redacted>"));
        assertTrue(redacted.contains("lookup_weather"));
        assertTrue(redacted.contains("rate_limit"));
    }

    @Test
    void doesNotEmitMalformedJsonVerbatim() {
        String redacted = AiRedactionPolicy.defaultPolicy().redactJson("{secret prompt");

        assertFalse(redacted.contains("secret prompt"));
        assertTrue(redacted.contains("malformed_json"));
    }
}
