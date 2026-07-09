package io.wangrollin.ai.response;

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
 * Auto-closeable iterator over OpenAI-compatible Responses API stream events.
 */
public final class ResponseStream implements AutoCloseable, Iterable<ResponseDelta> {
    private final BufferedReader reader;
    private final Function<String, ResponseDelta> deltaParser;
    private final Consumer<AiException> failureListener;
    private boolean closed;

    public ResponseStream(InputStream inputStream, Function<String, ResponseDelta> deltaParser) {
        this(inputStream, deltaParser, failure -> {
        });
    }

    public ResponseStream(
            InputStream inputStream,
            Function<String, ResponseDelta> deltaParser,
            Consumer<AiException> failureListener) {
        this.reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(inputStream, "inputStream must not be null"),
                StandardCharsets.UTF_8));
        this.deltaParser = Objects.requireNonNull(deltaParser, "deltaParser must not be null");
        this.failureListener = Objects.requireNonNull(failureListener, "failureListener must not be null");
    }

    @Override
    public Iterator<ResponseDelta> iterator() {
        return new Iterator<>() {
            private ResponseDelta next;
            private boolean fetched;

            @Override
            public boolean hasNext() {
                fetchNext();
                return next != null;
            }

            @Override
            public ResponseDelta next() {
                fetchNext();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                ResponseDelta current = next;
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

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            reader.close();
        } catch (IOException e) {
            throw new AiException("Failed to close response stream", e);
        }
    }

    private ResponseDelta readNextDelta() {
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
                ResponseDelta delta = parseDelta(data);
                if (delta.done()) {
                    close();
                    return null;
                }
                if (!delta.text().isEmpty() || !delta.toolCalls().isEmpty()) {
                    return delta;
                }
            }
            close();
            return null;
        } catch (IOException e) {
            AiException exception = new AiException("Failed to read response stream", e);
            closeAfterFailure(exception);
            failureListener.accept(exception);
            throw exception;
        }
    }

    private ResponseDelta parseDelta(String data) {
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
            original.addSuppressed(closeFailure);
        }
    }
}
