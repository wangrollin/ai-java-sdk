package io.wangrollin.ai.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable request for the OpenAI-compatible Responses API.
 */
public final class ResponseRequest {
    private final String model;
    private final String input;
    private final List<ResponseInputMessage> inputMessages;
    private final String instructions;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final ResponseTextFormat textFormat;

    private ResponseRequest(Builder builder) {
        this.model = normalizeOptionalText(builder.model);
        this.input = builder.input == null ? null : requireText(builder.input, "input");
        this.inputMessages = List.copyOf(builder.inputMessages);
        if (this.input == null && this.inputMessages.isEmpty()) {
            throw new IllegalArgumentException("input or inputMessages must be configured");
        }
        if (this.input != null && !this.inputMessages.isEmpty()) {
            throw new IllegalArgumentException("input and inputMessages cannot both be configured");
        }
        this.instructions = normalizeOptionalText(builder.instructions);
        this.temperature = requireNonNegative(builder.temperature, "temperature");
        this.topP = requireNonNegative(builder.topP, "topP");
        this.maxOutputTokens = requirePositive(builder.maxOutputTokens, "maxOutputTokens");
        this.textFormat = builder.textFormat;
    }

    /**
     * Starts building a response request.
     *
     * @return request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the request-specific model override, or {@code null} to use the client default.
     *
     * @return optional model override
     */
    public String model() {
        return model;
    }

    /**
     * Returns the text input, or {@code null} when this request uses typed input messages.
     *
     * @return optional text input
     */
    public String input() {
        return input;
    }

    /**
     * Returns the typed input messages, or an empty list when this request uses text input.
     *
     * @return immutable input message list
     */
    public List<ResponseInputMessage> inputMessages() {
        return inputMessages;
    }

    /**
     * Optional developer instructions for the model.
     *
     * @return optional instruction text
     */
    public String instructions() {
        return instructions;
    }

    /**
     * Optional sampling temperature.
     *
     * @return optional sampling temperature
     */
    public Double temperature() {
        return temperature;
    }

    /**
     * Optional nucleus sampling probability mass.
     *
     * @return optional top-p value
     */
    public Double topP() {
        return topP;
    }

    /**
     * Optional output token cap.
     *
     * @return optional output token cap
     */
    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Optional structured-output text format.
     *
     * @return optional text format
     */
    public ResponseTextFormat textFormat() {
        return textFormat;
    }

    /**
     * Builder for {@link ResponseRequest}.
     */
    public static final class Builder {
        private String model;
        private String input;
        private final List<ResponseInputMessage> inputMessages = new ArrayList<>();
        private String instructions;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private ResponseTextFormat textFormat;

        private Builder() {
        }

        /**
         * Overrides the client's default model for this request.
         *
         * @param model provider model name; blank values are treated as absent
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the required text input sent to the Responses API.
         *
         * @param input user input text
         * @return this builder
         */
        public Builder input(String input) {
            this.input = input;
            return this;
        }

        /**
         * Adds one multimodal input message to the request.
         *
         * @param message input message to append
         * @return this builder
         */
        public Builder inputMessage(ResponseInputMessage message) {
            this.inputMessages.add(Objects.requireNonNull(message, "message must not be null"));
            return this;
        }

        /**
         * Adds multiple multimodal input messages in list order.
         *
         * @param messages input messages to append
         * @return this builder
         */
        public Builder inputMessages(List<ResponseInputMessage> messages) {
            Objects.requireNonNull(messages, "messages must not be null");
            messages.forEach(this::inputMessage);
            return this;
        }

        /**
         * Sets optional developer instructions for the model.
         *
         * @param instructions instruction text; blank values are treated as absent
         * @return this builder
         */
        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Sets the sampling temperature.
         *
         * @param temperature non-negative finite sampling temperature
         * @return this builder
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets nucleus sampling probability mass.
         *
         * @param topP non-negative finite top-p value
         * @return this builder
         */
        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the provider-side cap for generated output tokens.
         *
         * @param maxOutputTokens positive output token limit
         * @return this builder
         */
        public Builder maxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        /**
         * Sets the provider text format for structured Responses API output.
         *
         * @param textFormat text format hint to send
         * @return this builder
         */
        public Builder textFormat(ResponseTextFormat textFormat) {
            this.textFormat = requireNonNull(textFormat, "textFormat");
            return this;
        }

        /**
         * Builds the immutable request after validating required fields.
         *
         * @return response request
         */
        public ResponseRequest build() {
            return new ResponseRequest(this);
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

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
