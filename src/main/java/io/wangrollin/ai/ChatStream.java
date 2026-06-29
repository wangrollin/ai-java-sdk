package io.wangrollin.ai;

import io.wangrollin.ai.internal.openai.OpenAiChatCodec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Auto-closeable iterator over OpenAI-compatible server-sent chat events.
 *
 * <p>Callers should consume this type with try-with-resources so the underlying
 * HTTP response body is closed even when stream parsing fails midway.
 */
public final class ChatStream implements AutoCloseable, Iterable<ChatDelta> {
    private final BufferedReader reader;
    private final OpenAiChatCodec codec;
    private boolean closed;

    ChatStream(InputStream inputStream, OpenAiChatCodec codec) {
        this.reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(inputStream, "inputStream must not be null"),
                StandardCharsets.UTF_8));
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    /**
     * Returns a lazy iterator that reads deltas from the response body on demand.
     *
     * @return iterator over parsed streaming deltas
     */
    @Override
    public Iterator<ChatDelta> iterator() {
        return new Iterator<>() {
            private ChatDelta next;
            private boolean fetched;

            @Override
            public boolean hasNext() {
                fetchNext();
                return next != null;
            }

            @Override
            public ChatDelta next() {
                fetchNext();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                ChatDelta current = next;
                next = null;
                fetched = false;
                return current;
            }

            private void fetchNext() {
                if (fetched) {
                    return;
                }
                next = readNextDelta();
                fetched = true;
            }
        };
    }

    /**
     * Closes the response body backing this stream.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            reader.close();
        } catch (IOException e) {
            throw new AiException("Failed to close chat stream", e);
        }
    }

    private ChatDelta readNextDelta() {
        if (closed) {
            return null;
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    close();
                    return null;
                }
                ChatDelta delta = parseDelta(data);
                if (!delta.text().isEmpty() || delta.finishReason() != null) {
                    return delta;
                }
            }
            close();
            return null;
        } catch (IOException e) {
            close();
            throw new AiException("Failed to read chat stream", e);
        }
    }

    private ChatDelta parseDelta(String data) {
        try {
            return codec.parseStreamDelta(data);
        } catch (AiException e) {
            close();
            throw e;
        }
    }
}
