package io.wangrollin.ai.chat;

import io.wangrollin.ai.error.AiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Auto-closeable iterator over OpenAI-compatible server-sent chat events.
 *
 * <p>Callers should consume this type with try-with-resources so the underlying
 * HTTP response body is closed even when stream parsing fails midway.
 */
public final class ChatStream implements AutoCloseable, Iterable<ChatDelta> {
    private final BufferedReader reader;
    private final Function<String, ChatDelta> deltaParser;
    private final Consumer<AiException> failureListener;
    private boolean closed;

    /**
     * Creates a stream backed by an input body and provider-specific delta parser.
     *
     * @param inputStream raw server-sent event response body
     * @param deltaParser parser for each non-empty {@code data:} value
     */
    public ChatStream(InputStream inputStream, Function<String, ChatDelta> deltaParser) {
        this(inputStream, deltaParser, failure -> {
        });
    }

    /**
     * Creates a stream with a failure callback used by transports to emit diagnostics
     * when lazy stream consumption fails after the HTTP request has succeeded.
     *
     * @param inputStream raw server-sent event response body
     * @param deltaParser parser for each non-empty {@code data:} value
     * @param failureListener callback for stream read or parse failures
     */
    public ChatStream(
            InputStream inputStream,
            Function<String, ChatDelta> deltaParser,
            Consumer<AiException> failureListener) {
        this.reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(inputStream, "inputStream must not be null"),
                StandardCharsets.UTF_8));
        this.deltaParser = Objects.requireNonNull(deltaParser, "deltaParser must not be null");
        this.failureListener = Objects.requireNonNull(failureListener, "failureListener must not be null");
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
                if (!delta.text().isEmpty() || delta.finishReason() != null || !delta.toolCalls().isEmpty()) {
                    return delta;
                }
            }
            close();
            return null;
        } catch (IOException e) {
            AiException exception = new AiException("Failed to read chat stream", e);
            closeAfterFailure(exception);
            failureListener.accept(exception);
            throw exception;
        }
    }

    private ChatDelta parseDelta(String data) {
        try {
            return deltaParser.apply(data);
        } catch (AiException e) {
            closeAfterFailure(e);
            failureListener.accept(e);
            throw e;
        }
    }

    private void closeAfterFailure(AiException original) {
        try {
            close();
        } catch (AiException closeFailure) {
            // Preserve the stream read/parse failure as the primary signal and
            // keep any cleanup problem available for diagnostics.
            original.addSuppressed(closeFailure);
        }
    }
}
