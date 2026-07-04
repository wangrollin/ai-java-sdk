package io.wangrollin.ai.chat;

import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamTest {
    private static final OpenAiChatCodec OPEN_AI_CODEC = new OpenAiChatCodec();

    @Test
    void closesStreamInput() {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("""
                data: [DONE]

                """);
        ChatStream stream = new ChatStream(inputStream, OPEN_AI_CODEC::parseStreamDelta);

        stream.close();

        assertTrue(inputStream.closed());
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean closed() {
            return closed;
        }
    }
}
