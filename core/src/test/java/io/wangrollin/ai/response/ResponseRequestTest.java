package io.wangrollin.ai.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseRequestTest {
    @Test
    void buildsTextInputRequest() {
        ResponseRequest request = ResponseRequest.builder()
                .input("Describe the release risk.")
                .build();

        assertEquals("Describe the release risk.", request.input());
        assertEquals(List.of(), request.inputMessages());
    }

    @Test
    void buildsMultimodalInputRequest() {
        ResponseInputMessage message = ResponseInputMessage.user(
                ResponseInputPart.text("What is in this image?"),
                ResponseInputPart.imageUrl("https://example.com/image.png", ResponseInputPart.ImageDetail.LOW));

        ResponseRequest request = ResponseRequest.builder()
                .inputMessage(message)
                .build();

        assertNull(request.input());
        assertEquals(List.of(message), request.inputMessages());
        assertEquals("low", message.content().get(1).detailValue());
    }

    @Test
    void rejectsMissingInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ResponseRequest.builder().build());

        assertEquals("input or inputMessages must be configured", exception.getMessage());
    }

    @Test
    void rejectsBlankTextInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ResponseRequest.builder().input(" ").build());

        assertEquals("input must not be blank", exception.getMessage());
    }

    @Test
    void rejectsTextAndMultimodalInputTogether() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ResponseRequest.builder()
                        .input("Describe this image.")
                        .inputMessage(ResponseInputMessage.user(ResponseInputPart.imageFileId("file-123")))
                        .build());

        assertEquals("input and inputMessages cannot both be configured", exception.getMessage());
    }

    @Test
    void rejectsInvalidMultimodalParts() {
        assertThrows(IllegalArgumentException.class, () -> ResponseInputPart.text(" "));
        assertThrows(IllegalArgumentException.class, () -> ResponseInputPart.imageUrl(""));
        assertThrows(IllegalArgumentException.class, () -> ResponseInputPart.imageFileId(" "));
        assertThrows(IllegalArgumentException.class, () -> ResponseInputMessage.user(List.of()));
    }

    @Test
    void protectsInputMessageContentFromMutation() {
        ResponseInputMessage message = ResponseInputMessage.user(ResponseInputPart.text("Hello"));

        assertThrows(UnsupportedOperationException.class, () -> message.content().clear());
    }
}
