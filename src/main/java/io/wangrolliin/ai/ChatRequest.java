package io.wangrolliin.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChatRequest {
    private final String model;
    private final List<ChatMessage> messages;

    private ChatRequest(Builder builder) {
        this.model = normalizeOptionalText(builder.model);
        if (builder.messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        this.messages = List.copyOf(builder.messages);
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

    public static final class Builder {
        private String model;
        private final List<ChatMessage> messages = new ArrayList<>();

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
}
