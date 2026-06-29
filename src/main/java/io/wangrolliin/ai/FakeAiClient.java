package io.wangrolliin.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

/**
 * In-memory {@link AiChatClient} for application tests.
 *
 * <p>The fake records requests and returns preconfigured outcomes without
 * reading API keys, opening sockets, or depending on provider availability.
 */
public final class FakeAiClient implements AiChatClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Queue<Outcome<ChatResponse>> chatOutcomes;
    private final Queue<Outcome<StreamResponse>> streamOutcomes;
    private final List<ChatRequest> requests = new ArrayList<>();

    private FakeAiClient(Builder builder) {
        this.chatOutcomes = new ArrayDeque<>(builder.chatOutcomes);
        this.streamOutcomes = new ArrayDeque<>(builder.streamOutcomes);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        requests.add(Objects.requireNonNull(request, "request must not be null"));
        Outcome<ChatResponse> outcome = chatOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake chat response configured");
        }
        return outcome.resolve();
    }

    @Override
    public ChatStream stream(ChatRequest request) {
        requests.add(Objects.requireNonNull(request, "request must not be null"));
        Outcome<StreamResponse> outcome = streamOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake stream response configured");
        }
        return outcome.resolve().stream();
    }

    /**
     * Returns all requests in call order. The list is defensive so tests can
     * inspect calls without mutating the fake's internal history.
     */
    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    private static ChatStream streamFrom(List<ChatDelta> deltas) {
        StringBuilder body = new StringBuilder();
        for (ChatDelta delta : deltas) {
            body.append("data: ")
                    .append(serializeDelta(delta))
                    .append("\n\n");
        }
        body.append("data: [DONE]\n\n");
        return new ChatStream(new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)), OBJECT_MAPPER);
    }

    private static ChatStream malformedStreamFrom(String data) {
        String body = "data: " + data + "\n\n";
        return new ChatStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), OBJECT_MAPPER);
    }

    private static String serializeDelta(ChatDelta delta) {
        try {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("delta", Map.of("content", delta.text()));
            choice.put("finish_reason", delta.finishReason());
            return OBJECT_MAPPER.writeValueAsString(Map.of("choices", List.of(choice)));
        } catch (JsonProcessingException e) {
            throw new AiException("Failed to serialize fake stream event", e);
        }
    }

    private record Outcome<T>(T value, RuntimeException failure) {
        private T resolve() {
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }

    private record StreamResponse(List<ChatDelta> deltas, String malformedEventData) {
        private ChatStream stream() {
            if (malformedEventData != null) {
                return malformedStreamFrom(malformedEventData);
            }
            return streamFrom(deltas);
        }
    }

    public static final class Builder {
        private final List<Outcome<ChatResponse>> chatOutcomes = new ArrayList<>();
        private final List<Outcome<StreamResponse>> streamOutcomes = new ArrayList<>();

        private Builder() {
        }

        public Builder chatResponse(String text) {
            return chatResponse(new ChatResponse(text));
        }

        public Builder chatResponse(ChatResponse response) {
            chatOutcomes.add(new Outcome<>(Objects.requireNonNull(response, "response must not be null"), null));
            return this;
        }

        public Builder chatFailure(RuntimeException failure) {
            chatOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        public Builder streamDeltas(ChatDelta... deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            return streamDeltas(List.of(deltas));
        }

        public Builder streamDeltas(List<ChatDelta> deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            streamOutcomes.add(new Outcome<>(new StreamResponse(List.copyOf(deltas), null), null));
            return this;
        }

        public Builder streamMalformedEvent(String data) {
            streamOutcomes.add(new Outcome<>(new StreamResponse(null, requireText(data, "data")), null));
            return this;
        }

        public Builder streamFailure(RuntimeException failure) {
            streamOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        public FakeAiClient build() {
            return new FakeAiClient(this);
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
