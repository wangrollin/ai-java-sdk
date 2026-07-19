package io.wangrollin.ai.testing;

import io.wangrollin.ai.chat.ChatDelta;
import io.wangrollin.ai.chat.ChatRequest;
import io.wangrollin.ai.chat.ChatResponse;
import io.wangrollin.ai.chat.ChatStream;
import io.wangrollin.ai.client.AiChatClient;
import io.wangrollin.ai.client.AiEmbeddingClient;
import io.wangrollin.ai.client.AiResponseClient;
import io.wangrollin.ai.embedding.Embedding;
import io.wangrollin.ai.embedding.EmbeddingRequest;
import io.wangrollin.ai.embedding.EmbeddingResult;
import io.wangrollin.ai.error.AiException;
import io.wangrollin.ai.internal.openai.OpenAiChatCodec;
import io.wangrollin.ai.internal.openai.OpenAiResponseCodec;
import io.wangrollin.ai.response.ResponseDelta;
import io.wangrollin.ai.response.ResponseRequest;
import io.wangrollin.ai.response.ResponseResult;
import io.wangrollin.ai.response.ResponseStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

/**
 * In-memory chat, Responses, and Embeddings client for application tests.
 *
 * <p>The fake records requests and returns preconfigured outcomes without
 * reading API keys, opening sockets, or depending on provider availability.
 * Configure one outcome per expected call; missing outcomes fail fast so tests
 * do not accidentally accept unexpected AI interactions.
 */
public final class FakeAiClient implements AiChatClient, AiResponseClient, AiEmbeddingClient {
    private static final OpenAiChatCodec OPEN_AI_CODEC = new OpenAiChatCodec();
    private static final OpenAiResponseCodec OPEN_AI_RESPONSE_CODEC = new OpenAiResponseCodec();

    private final Queue<Outcome<ChatResponse>> chatOutcomes;
    private final Queue<Outcome<ChatStreamResponse>> streamOutcomes;
    private final Queue<Outcome<ResponseResult>> responseOutcomes;
    private final Queue<Outcome<ResponseStreamResponse>> responseStreamOutcomes;
    private final Queue<Outcome<EmbeddingResult>> embeddingOutcomes;
    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<ResponseRequest> responseRequests = new ArrayList<>();
    private final List<EmbeddingRequest> embeddingRequests = new ArrayList<>();

    private FakeAiClient(Builder builder) {
        this.chatOutcomes = new ArrayDeque<>(builder.chatOutcomes);
        this.streamOutcomes = new ArrayDeque<>(builder.streamOutcomes);
        this.responseOutcomes = new ArrayDeque<>(builder.responseOutcomes);
        this.responseStreamOutcomes = new ArrayDeque<>(builder.responseStreamOutcomes);
        this.embeddingOutcomes = new ArrayDeque<>(builder.embeddingOutcomes);
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
        Outcome<ChatStreamResponse> outcome = streamOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake stream response configured");
        }
        return outcome.resolve().stream();
    }

    @Override
    public ResponseResult respond(ResponseRequest request) {
        responseRequests.add(Objects.requireNonNull(request, "request must not be null"));
        Outcome<ResponseResult> outcome = responseOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake response result configured");
        }
        return outcome.resolve();
    }

    @Override
    public ResponseStream streamResponse(ResponseRequest request) {
        responseRequests.add(Objects.requireNonNull(request, "request must not be null"));
        Outcome<ResponseStreamResponse> outcome = responseStreamOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake response stream configured");
        }
        return outcome.resolve().stream();
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        embeddingRequests.add(Objects.requireNonNull(request, "request must not be null"));
        Outcome<EmbeddingResult> outcome = embeddingOutcomes.poll();
        if (outcome == null) {
            throw new AiException("No fake embedding result configured");
        }
        return outcome.resolve();
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

    /**
     * Returns all Responses API requests in call order. Chat request history
     * stays separate so existing tests that assert {@link #requests()} do not
     * need to filter by operation kind.
     *
     * @return immutable Responses API request history
     */
    public List<ResponseRequest> responseRequests() {
        return List.copyOf(responseRequests);
    }

    /**
     * Returns all embedding requests in call order.
     *
     * @return immutable embedding request history
     */
    public List<EmbeddingRequest> embeddingRequests() {
        return List.copyOf(embeddingRequests);
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

    private static ResponseStream responseStreamFrom(List<ResponseDelta> deltas) {
        StringBuilder body = new StringBuilder();
        for (ResponseDelta delta : deltas) {
            body.append("data: ")
                    .append(OPEN_AI_RESPONSE_CODEC.serializeStreamDelta(delta))
                    .append("\n\n");
        }
        body.append("data: [DONE]\n\n");
        return new ResponseStream(
                new ByteArrayInputStream(body.toString().getBytes(StandardCharsets.UTF_8)),
                OPEN_AI_RESPONSE_CODEC::parseStreamDelta);
    }

    private static ResponseStream malformedResponseStreamFrom(String data) {
        String body = "data: " + data + "\n\n";
        return new ResponseStream(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                OPEN_AI_RESPONSE_CODEC::parseStreamDelta);
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

    private record ChatStreamResponse(List<ChatDelta> deltas, String malformedEventData) {
        private ChatStream stream() {
            if (malformedEventData != null) {
                return malformedStreamFrom(malformedEventData);
            }
            return streamFrom(deltas);
        }
    }

    private record ResponseStreamResponse(List<ResponseDelta> deltas, String malformedEventData) {
        private ResponseStream stream() {
            if (malformedEventData != null) {
                return malformedResponseStreamFrom(malformedEventData);
            }
            return responseStreamFrom(deltas);
        }
    }

    /**
     * Builder for {@link FakeAiClient}.
     */
    public static final class Builder {
        private final List<Outcome<ChatResponse>> chatOutcomes = new ArrayList<>();
        private final List<Outcome<ChatStreamResponse>> streamOutcomes = new ArrayList<>();
        private final List<Outcome<ResponseResult>> responseOutcomes = new ArrayList<>();
        private final List<Outcome<ResponseStreamResponse>> responseStreamOutcomes = new ArrayList<>();
        private final List<Outcome<EmbeddingResult>> embeddingOutcomes = new ArrayList<>();

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
            streamOutcomes.add(new Outcome<>(new ChatStreamResponse(List.copyOf(deltas), null), null));
            return this;
        }

        /**
         * Adds a malformed streaming event outcome for parser-failure tests.
         *
         * @param data raw data field to emit
         * @return this builder
         */
        public Builder streamMalformedEvent(String data) {
            streamOutcomes.add(new Outcome<>(new ChatStreamResponse(null, requireText(data, "data")), null));
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
         * Adds a text-only Responses API result outcome.
         *
         * @param text response text to return
         * @return this builder
         */
        public Builder responseResult(String text) {
            return responseResult(new ResponseResult(Objects.requireNonNull(text, "text must not be null")));
        }

        /**
         * Adds a full Responses API result outcome.
         *
         * @param result result to return
         * @return this builder
         */
        public Builder responseResult(ResponseResult result) {
            responseOutcomes.add(new Outcome<>(Objects.requireNonNull(result, "result must not be null"), null));
            return this;
        }

        /**
         * Adds a Responses API failure outcome.
         *
         * @param failure exception to throw
         * @return this builder
         */
        public Builder responseFailure(RuntimeException failure) {
            responseOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        /**
         * Adds a streaming Responses API outcome from vararg deltas.
         *
         * @param deltas deltas to emit
         * @return this builder
         */
        public Builder responseStreamDeltas(ResponseDelta... deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            return responseStreamDeltas(List.of(deltas));
        }

        /**
         * Adds a streaming Responses API outcome from list deltas.
         *
         * @param deltas deltas to emit
         * @return this builder
         */
        public Builder responseStreamDeltas(List<ResponseDelta> deltas) {
            Objects.requireNonNull(deltas, "deltas must not be null");
            responseStreamOutcomes.add(new Outcome<>(new ResponseStreamResponse(List.copyOf(deltas), null), null));
            return this;
        }

        /**
         * Adds a malformed Responses API streaming event outcome for parser-failure tests.
         *
         * @param data raw data field to emit
         * @return this builder
         */
        public Builder responseStreamMalformedEvent(String data) {
            responseStreamOutcomes.add(new Outcome<>(new ResponseStreamResponse(null, requireText(data, "data")), null));
            return this;
        }

        /**
         * Adds a Responses API stream-opening failure outcome.
         *
         * @param failure exception to throw
         * @return this builder
         */
        public Builder responseStreamFailure(RuntimeException failure) {
            responseStreamOutcomes.add(new Outcome<>(
                    null,
                    Objects.requireNonNull(failure, "failure must not be null")));
            return this;
        }

        /**
         * Adds a complete embedding result outcome.
         *
         * @param result result returned by the next embedding call
         * @return this builder
         */
        public Builder embeddingResult(EmbeddingResult result) {
            embeddingOutcomes.add(new Outcome<>(Objects.requireNonNull(result, "result must not be null"), null));
            return this;
        }

        /**
         * Adds one vector as a convenient single-input embedding outcome.
         *
         * @param values vector components
         * @return this builder
         */
        public Builder embeddingVector(Double... values) {
            Objects.requireNonNull(values, "values must not be null");
            return embeddingResult(new EmbeddingResult(
                    null,
                    List.of(new Embedding(0, List.of(values))),
                    null));
        }

        /**
         * Adds an embedding failure outcome.
         *
         * @param failure exception thrown by the next embedding call
         * @return this builder
         */
        public Builder embeddingFailure(RuntimeException failure) {
            embeddingOutcomes.add(new Outcome<>(null, Objects.requireNonNull(failure, "failure must not be null")));
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
