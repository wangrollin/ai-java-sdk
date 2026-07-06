package io.wangrollin.ai.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseTextFormatTest {
    @Test
    void createsTextAndJsonObjectFormats() {
        ResponseTextFormat text = ResponseTextFormat.text();
        ResponseTextFormat jsonObject = ResponseTextFormat.jsonObject();

        assertEquals("text", text.type());
        assertNull(text.jsonSchema());
        assertEquals("json_object", jsonObject.type());
        assertNull(jsonObject.jsonSchemaName());
    }

    @Test
    void createsJsonSchemaFormatWithStrictDefaultAndDescription() {
        ResponseTextFormat strict = ResponseTextFormat.jsonSchema("answer", "{\"type\":\"object\"}");
        ResponseTextFormat described = ResponseTextFormat.jsonSchema(
                "answer",
                "Short answer shape",
                "{\"type\":\"object\"}",
                false);

        assertEquals("json_schema", strict.type());
        assertEquals("answer", strict.jsonSchemaName());
        assertTrue(strict.strict());
        assertEquals("Short answer shape", described.jsonSchemaDescription());
        assertEquals(false, described.strict());
    }

    @Test
    void rejectsInvalidJsonSchemaFormats() {
        assertThrows(IllegalArgumentException.class, () -> ResponseTextFormat.jsonSchema(" ", "{}"));
        assertThrows(IllegalArgumentException.class, () -> ResponseTextFormat.jsonSchema("answer", "{"));
        assertThrows(IllegalArgumentException.class, () -> ResponseTextFormat.jsonSchema("answer", "[]"));
    }
}
