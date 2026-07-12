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
import java.util.function.BiFunction;
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
    private final BiFunction<String, String, ChatDelta> deltaParser;
    private final Consumer<AiException> failureListener;
    private boolean closed;

    /**
     * Creates a stream backed by an input body and provider-specific delta parser.
     *
     * @param inputStream raw server-sent event response body
     * @param deltaParser parser for each non-empty {@code data:} value
     */
    public ChatStream(InputStream inputStream, Function<String, ChatDelta> deltaParser) {
        this(inputStream, (event, data) -> deltaParser.apply(data), failure -> {
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
        this(inputStream, (event, data) -> deltaParser.apply(data), failureListener);
    }

    /**
     * Creates a stream with a parser that can inspect both the SSE event name
     * and data payload. OpenAI-compatible streams usually omit event names,
     * while Anthropic streams use them to identify text, stop, and ping events.
     *
     * @param inputStream raw server-sent event response body
     * @param deltaParser parser for each completed SSE event
     * @param failureListener callback for stream read or parse failures
     */
    public ChatStream(
            InputStream inputStream,
            BiFunction<String, String, ChatDelta> deltaParser,
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
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    ChatDelta delta = parseBufferedDelta(event, data);
                    if (closed) {
                        return null;
                    }
                    event = null;
                    data.setLength(0);
                    if (delta != null) {
                        return delta;
                    }
                    continue;
                }
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
            ChatDelta delta = parseBufferedDelta(event, data);
            if (closed) {
                return null;
            }
            if (delta != null) {
                return delta;
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

    private ChatDelta parseBufferedDelta(String event, StringBuilder data) {
        if (data.isEmpty()) {
            return null;
        }
        String payload = data.toString();
        if ("[DONE]".equals(payload)) {
            close();
            return null;
        }
        ChatDelta delta = parseDelta(event, payload);
        if (!delta.text().isEmpty() || delta.finishReason() != null || !delta.toolCalls().isEmpty()) {
            return delta;
        }
        return null;
    }

    private ChatDelta parseDelta(String event, String data) {
        try {
            return deltaParser.apply(event, data);
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
