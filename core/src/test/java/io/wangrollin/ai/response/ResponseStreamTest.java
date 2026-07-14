package io.wangrollin.ai.response;

import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.openai.OpenAiResponseCodec;
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

    @Test
    void closeIsIdempotentAndStopsIteration() {
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream("""
                data: {"type":"response.output_text.delta","delta":"ignored"}

                """);
        ResponseStream stream = new ResponseStream(inputStream, CODEC::parseStreamDelta);

        stream.close();
        stream.close();

        assertTrue(inputStream.closed());
        assertFalse(stream.iterator().hasNext());
    }

    @Test
    void readFailureClosesStreamAndReportsFailure() {
        FailingInputStream inputStream = new FailingInputStream(true);
        List<AiException> failures = new ArrayList<>();
        ResponseStream stream = new ResponseStream(inputStream, CODEC::parseStreamDelta, failures::add);

        AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

        assertTrue(inputStream.closed());
        assertEquals("Failed to read response stream", exception.getMessage());
        assertEquals(1, failures.size());
        assertSame(exception, failures.get(0));
        assertEquals(1, exception.getSuppressed().length);
        assertEquals("Failed to close response stream", exception.getSuppressed()[0].getMessage());
    }

    @Test
    void parseFailureClosesStreamAndReportsFailure() {
        CloseFailingInputStream inputStream = new CloseFailingInputStream("""
                data: {"type":

                """);
        List<AiException> failures = new ArrayList<>();
        ResponseStream stream = new ResponseStream(inputStream, CODEC::parseStreamDelta, failures::add);

        AiException exception = assertThrows(AiException.class, () -> stream.iterator().hasNext());

        assertTrue(inputStream.closed());
        assertEquals(1, failures.size());
        assertSame(exception, failures.get(0));
        assertEquals(1, exception.getSuppressed().length);
        assertEquals("Failed to close response stream", exception.getSuppressed()[0].getMessage());
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
