package io.wangrolliin.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class ChatStream implements AutoCloseable, Iterable<ChatDelta> {
    private final BufferedReader reader;
    private final ObjectMapper objectMapper;
    private boolean closed;

    ChatStream(InputStream inputStream, ObjectMapper objectMapper) {
        this.reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(inputStream, "inputStream must not be null"),
                StandardCharsets.UTF_8));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

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
            JsonNode choice = objectMapper.readTree(data).path("choices").path(0);
            JsonNode content = choice.path("delta").path("content");
            JsonNode finishReason = choice.path("finish_reason");
            return new ChatDelta(
                    content.isTextual() ? content.asText() : "",
                    finishReason.isTextual() ? finishReason.asText() : null);
        } catch (JsonProcessingException e) {
            close();
            throw new AiException("Failed to parse chat stream event", e);
        }
    }
}
