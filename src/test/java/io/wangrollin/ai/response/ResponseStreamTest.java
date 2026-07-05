package io.wangrollin.ai.response;

import io.wangrollin.ai.internal.openai.OpenAiResponseCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseStreamTest {
    private static final OpenAiResponseCodec CODEC = new OpenAiResponseCodec();

    @Test
    void iteratesTextDeltasUntilCompleted() {
        ResponseStream stream = new ResponseStream(new ByteArrayInputStream("""
                data: {"type":"response.created"}

                data: {"type":"response.output_text.delta","delta":"Hel"}

                data: {"type":"response.output_text.delta","delta":"lo"}

                data: {"type":"response.completed"}

                """.getBytes(StandardCharsets.UTF_8)), CODEC::parseStreamDelta);

        List<ResponseDelta> deltas = new ArrayList<>();
        for (ResponseDelta delta : stream) {
            deltas.add(delta);
        }

        assertEquals(List.of(new ResponseDelta("Hel", false), new ResponseDelta("lo", false)), deltas);
    }
}
