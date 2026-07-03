package io.wangrollin.ai.testing;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * In-memory {@link AiChatClient} for application tests.
 *
 * <p>The fake records requests and returns preconfigured outcomes without
 * reading API keys, opening sockets, or depending on provider availability.
 * Configure one outcome per expected call; missing outcomes fail fast so tests
 * do not accidentally accept unexpected AI interactions.
 */
public final class FakeAiClient implements AiChatClient {
    private static final OpenAiChatCodec OPEN_AI_CODEC = new OpenAiChatCodec();

    private final Queue<Outcome<ChatResponse>> chatOutcomes;
    private final Queue<Outcome<StreamResponse>> streamOutcomes;
    private final List<ChatRequest> requests = new ArrayList<>();

    private FakeAiClient(Builder builder) {
        this.chatOutcomes = new ArrayDeque<>(builder.chatOutcomes);
        this.streamOutcomes = new ArrayDeque<>(builder.streamOutcomes);
    }

    /**
     * Starts building an in-memory fake client.
     *
     * @return fake client builder
     */
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
     *
     * @return immutable request history
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
        return new ChatStream(
                new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)),
                OPEN_AI_CODEC::parseStreamDelta);
    }

    private static ChatStream malformedStreamFrom(String data) {
        String body = "data: " + data + "\n\n";
        return new ChatStream(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                OPEN_AI_CODEC::parseStreamDelta);
    }

    private static String serializeDelta(ChatDelta delta) {
        return OPEN_AI_CODEC.serializeStreamDelta(delta);
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

    /**
     * Builder for {@link FakeAiClient}.
     */
    public static final class Builder {
        private final List<Outcome<ChatResponse>> chatOutcomes = new ArrayList<>();
        private final List<Outcome<StreamResponse>> streamOutcomes = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a text-only chat response outcome.
         *
         * @param text response text to return
         * @return this builder
         */
        public Builder chatResponse(String text) {
            return chatResponse(new ChatResponse(text));
        }

        /**
         * Adds a full chat response outcome.
         *
         * @param response response to return
         * @return this builder
         */
        public Builder chatResponse(ChatResponse response) {
            chatOutcomes.add(new Outcome<>(Objects.requireNonNull(response, "response must not be null"), null));
            return this;
        }

        /**
         * Adds a chat failure outcome.
         *
         * @param failure exception to throw
         * @return this builder
         */
        public Builder chatFailure(RuntimeException failure) {
            chatOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        /**
         * Adds a streaming response outcome from vararg deltas.
         *
         * @param deltas deltas to emit
         * @return this builder
         */
        public Builder streamDeltas(ChatDelta... deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            return streamDeltas(List.of(deltas));
        }

        /**
         * Adds a streaming response outcome from list deltas.
         *
         * @param deltas deltas to emit
         * @return this builder
         */
        public Builder streamDeltas(List<ChatDelta> deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            streamOutcomes.add(new Outcome<>(new StreamResponse(List.copyOf(deltas), null), null));
            return this;
        }

        /**
         * Adds a malformed streaming event outcome for parser-failure tests.
         *
         * @param data raw data field to emit
         * @return this builder
         */
        public Builder streamMalformedEvent(String data) {
            streamOutcomes.add(new Outcome<>(new StreamResponse(null, requireText(data, "data")), null));
            return this;
        }

        /**
         * Adds a stream-opening failure outcome.
         *
         * @param failure exception to throw
         * @return this builder
         */
        public Builder streamFailure(RuntimeException failure) {
            streamOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        /**
         * Builds the fake client with the configured response queues.
         *
         * @return fake AI client
         */
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
