package io.wangrollin.ai.internal.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseInputMessage;
import io.wangrollin.ai.response.ResponseInputPart;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseTextFormat;
import io.wangrollin.ai.response.ResponseUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponseCodecTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OpenAiResponseCodec codec = new OpenAiResponseCodec();

    @Test
    void serializesResponseRequest() throws Exception {
        String body = codec.serializeRequest(ResponseRequest.builder()
                .model("request-model")
                .input("Hello")
                .instructions("Answer briefly.")
                .temperature(0.2)
                .topP(0.9)
                .maxOutputTokens(64)
                .build(), "default-model", true);

        JsonNode json = OBJECT_MAPPER.readTree(body);
        assertEquals("request-model", json.path("model").asText());
        assertEquals("Hello", json.path("input").asText());
        assertEquals("Answer briefly.", json.path("instructions").asText());
        assertEquals(0.2, json.path("temperature").asDouble());
        assertEquals(0.9, json.path("top_p").asDouble());
        assertEquals(64, json.path("max_output_tokens").asInt());
        assertTrue(json.path("stream").asBoolean());
    }

    @Test
    void serializesResponseTextJsonSchemaFormat() throws Exception {
        String body = codec.serializeRequest(ResponseRequest.builder()
                .input("Summarize risk")
                .textFormat(ResponseTextFormat.jsonSchema(
                        "risk_summary",
                        "Risk summary response",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "risk": { "type": "string" }
                                  },
                                  "required": ["risk"],
                                  "additionalProperties": false
                                }
                                """,
                        true))
                .build(), "default-model", false);

        JsonNode format = OBJECT_MAPPER.readTree(body).path("text").path("format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals("risk_summary", format.path("name").asText());
        assertEquals("Risk summary response", format.path("description").asText());
        assertEquals("object", format.path("schema").path("type").asText());
        assertEquals("risk", format.path("schema").path("required").path(0).asText());
        assertTrue(format.path("strict").asBoolean());
    }

    @Test
    void serializesResponseTextJsonObjectFormat() throws Exception {
        String body = codec.serializeRequest(ResponseRequest.builder()
                .input("Return JSON")
                .textFormat(ResponseTextFormat.jsonObject())
                .build(), "default-model", false);

        JsonNode format = OBJECT_MAPPER.readTree(body).path("text").path("format");
        assertEquals("json_object", format.path("type").asText());
    }

    @Test
    void serializesMultimodalImageUrlInput() throws Exception {
        String body = codec.serializeRequest(ResponseRequest.builder()
                .inputMessage(ResponseInputMessage.user(
                        ResponseInputPart.text("Describe this image."),
                        ResponseInputPart.imageUrl(
                                "https://example.com/image.png",
                                ResponseInputPart.ImageDetail.HIGH)))
                .build(), "default-model", false);

        JsonNode content = OBJECT_MAPPER.readTree(body).path("input").path(0).path("content");
        assertEquals("user", OBJECT_MAPPER.readTree(body).path("input").path(0).path("role").asText());
        assertEquals("input_text", content.path(0).path("type").asText());
        assertEquals("Describe this image.", content.path(0).path("text").asText());
        assertEquals("input_image", content.path(1).path("type").asText());
        assertEquals("https://example.com/image.png", content.path(1).path("image_url").asText());
        assertEquals("high", content.path(1).path("detail").asText());
    }

    @Test
    void serializesMultimodalDataUrlWithoutRewriting() throws Exception {
        String dataUrl = "data:image/png;base64,iVBORw0KGgo=";

        String body = codec.serializeRequest(ResponseRequest.builder()
                .inputMessage(ResponseInputMessage.user(ResponseInputPart.imageUrl(dataUrl)))
                .build(), "default-model", false);

        JsonNode image = OBJECT_MAPPER.readTree(body).path("input").path(0).path("content").path(0);
        assertEquals("input_image", image.path("type").asText());
        assertEquals(dataUrl, image.path("image_url").asText());
    }

    @Test
    void serializesMultimodalImageFileIdInput() throws Exception {
        String body = codec.serializeRequest(ResponseRequest.builder()
                .inputMessage(ResponseInputMessage.user(
                        ResponseInputPart.text("Extract invoice fields."),
                        ResponseInputPart.imageFileId(
                                "file-abc123",
                                ResponseInputPart.ImageDetail.ORIGINAL)))
                .build(), "default-model", false);

        JsonNode image = OBJECT_MAPPER.readTree(body).path("input").path(0).path("content").path(1);
        assertEquals("input_image", image.path("type").asText());
        assertEquals("file-abc123", image.path("file_id").asText());
        assertEquals("original", image.path("detail").asText());
    }

    @Test
    void parsesOutputTextAndUsage() {
        ResponseResult result = codec.parseResponse("""
                {
                  "id": "resp_123",
                  "model": "test-model",
                  "status": "completed",
                  "output_text": "Hello from responses",
                  "usage": {
                    "input_tokens": 2,
                    "output_tokens": 3,
                    "total_tokens": 5
                  }
                }
                """);

        assertEquals(new ResponseResult(
                "Hello from responses",
                "resp_123",
                "test-model",
                "completed",
                new ResponseUsage(2, 3, 5)), result);
    }

    @Test
    void fallsBackToOutputContentText() {
        ResponseResult result = codec.parseResponse("""
                {
                  "output": [
                    {
                      "content": [
                        {"type": "output_text", "text": "Hel"},
                        {"type": "output_text", "text": "lo"}
                      ]
                    }
                  ]
                }
                """);

        assertEquals("Hello", result.text());
    }

    @Test
    void rejectsMissingText() {
        AiException exception = assertThrows(AiException.class, () -> codec.parseResponse("""
                {"output":[]}
                """));

        assertEquals("Response did not contain output text", exception.getMessage());
    }

    @Test
    void parsesStreamDeltaAndCompletion() {
        assertEquals(new ResponseDelta("Hel", false), codec.parseStreamDelta("""
                {"type":"response.output_text.delta","delta":"Hel"}
                """));
        assertEquals(new ResponseDelta("", true), codec.parseStreamDelta("""
                {"type":"response.completed"}
                """));
        assertEquals(new ResponseDelta("", false), codec.parseStreamDelta("""
                {"type":"response.created"}
                """));
    }

    @Test
    void serializesFakeStreamDeltas() {
        assertEquals(
                new ResponseDelta("Hel", false),
                codec.parseStreamDelta(codec.serializeStreamDelta(new ResponseDelta("Hel", false))));
        assertEquals(
                new ResponseDelta("", true),
                codec.parseStreamDelta(codec.serializeStreamDelta(new ResponseDelta("", true))));
    }
}
