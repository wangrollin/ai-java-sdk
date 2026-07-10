package io.wangrollin.ai.chat;

import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void closeIsIdempotentAndStopsIteration() {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("""
                data: {"choices":[{"delta":{"content":"ignored"},"finish_reason":null}]}

                """);
        ChatStream stream = new ChatStream(inputStream, OPEN_AI_CODEC::parseStreamDelta);

        stream.close();
        stream.close();

        assertTrue(inputStream.closed());
        assertFalse(stream.iterator().hasNext());
    }

    @Test
    void readFailureClosesStreamAndReportsFailure() {
        FailingInputStream inputStream = new FailingInputStream(true);
        List<AiException> failures = new ArrayList<>();
        ChatStream stream = new ChatStream(inputStream, OPEN_AI_CODEC::parseStreamDelta, failures::add);

        AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

        assertTrue(inputStream.closed());
        assertEquals("Failed to read chat stream", exception.getMessage());
        assertEquals(1, failures.size());
        assertSame(exception, failures.getFirst());
        assertEquals(1, exception.getSuppressed().length);
        assertEquals("Failed to close chat stream", exception.getSuppressed()[0].getMessage());
    }

    @Test
    void parseFailureClosesStreamAndReportsFailure() {
        CloseFailingInputStream inputStream = new CloseFailingInputStream("""
                data: {"choices":[

                """);
        List<AiException> failures = new ArrayList<>();
        ChatStream stream = new ChatStream(inputStream, OPEN_AI_CODEC::parseStreamDelta, failures::add);

        AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

        assertTrue(inputStream.closed());
        assertEquals(1, failures.size());
        assertSame(exception, failures.getFirst());
        assertEquals(1, exception.getSuppressed().length);
        assertEquals("Failed to close chat stream", exception.getSuppressed()[0].getMessage());
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

    private static final class CloseFailingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseFailingInputStream(String value) {
            super(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            throw new IOException("close failed");
        }

        private boolean closed() {
            return closed;
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final boolean failOnClose;
        private boolean closed;

        private FailingInputStream(boolean failOnClose) {
            this.failOnClose = failOnClose;
        }

        @Override
        public int read() throws IOException {
            throw new IOException("read failed");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            throw new IOException("read failed");
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failOnClose) {
                throw new IOException("close failed");
            }
        }

        private boolean closed() {
            return closed;
        }
    }
}
