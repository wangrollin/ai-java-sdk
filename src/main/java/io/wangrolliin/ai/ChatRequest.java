package io.wangrolliin.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChatRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final List<String> stopSequences;

    private ChatRequest(Builder builder) {
        this.model = normalizeOptionalText(builder.model);
        if (builder.messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        this.messages = List.copyOf(builder.messages);
        this.temperature = requireNonNegative(builder.temperature, "temperature");
        this.topP = requireNonNegative(builder.topP, "topP");
        this.maxTokens = requirePositive(builder.maxTokens, "maxTokens");
        this.stopSequences = List.copyOf(builder.stopSequences);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String model() {
        return model;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    /**
     * Optional sampling temperature to send with this request.
     */
    public Double temperature() {
        return temperature;
    }

    /**
     * Optional nucleus sampling value, serialized as the OpenAI-compatible top_p field.
     */
    public Double topP() {
        return topP;
    }

    /**
     * Optional output token cap for providers that support max_tokens.
     */
    public Integer maxTokens() {
        return maxTokens;
    }

    /**
     * Optional stop sequences that should end generation when matched by the provider.
     */
    public List<String> stopSequences() {
        return stopSequences;
    }

    public static final class Builder {
        private String model;
        private final List<ChatMessage> messages = new ArrayList<>();
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private final List<String> stopSequences = new ArrayList<>();

        private Builder() {
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder message(ChatMessage message) {
            this.messages.add(Objects.requireNonNull(message, "message must not be null"));
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            Objects.requireNonNull(messages, "messages must not be null");
            messages.forEach(this::message);
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stopSequence(String stopSequence) {
            this.stopSequences.add(requireText(stopSequence, "stopSequence"));
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            Objects.requireNonNull(stopSequences, "stopSequences must not be null");
            stopSequences.forEach(this::stopSequence);
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(this);
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Double requireNonNegative(Double value, String name) {
        if (value == null) {
            return null;
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static Integer requirePositive(Integer value, String name) {
        if (value == null) {
            return null;
        }
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
