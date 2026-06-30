package io.wangrollin.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable chat completion request.
 */
public final class ChatRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final List<String> stopSequences;
    private final ChatResponseFormat responseFormat;
    private final List<ChatTool> tools;
    private final ChatToolChoice toolChoice;

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
        this.responseFormat = builder.responseFormat;
        this.tools = List.copyOf(builder.tools);
        this.toolChoice = builder.toolChoice;
    }

    /**
     * Starts building a chat request.
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
     * Returns the ordered conversation messages sent to the provider.
     *
     * @return immutable message list
     */
    public List<ChatMessage> messages() {
        return messages;
    }

    /**
     * Optional sampling temperature to send with this request.
     *
     * @return optional sampling temperature
     */
    public Double temperature() {
        return temperature;
    }

    /**
     * Optional nucleus sampling value, serialized as the OpenAI-compatible top_p field.
     *
     * @return optional nucleus sampling value
     */
    public Double topP() {
        return topP;
    }

    /**
     * Optional output token cap for providers that support max_tokens.
     *
     * @return optional output token cap
     */
    public Integer maxTokens() {
        return maxTokens;
    }

    /**
     * Optional stop sequences that should end generation when matched by the provider.
     *
     * @return immutable stop sequence list
     */
    public List<String> stopSequences() {
        return stopSequences;
    }

    /**
     * Optional structured-output hint for providers that support response formats.
     *
     * @return optional response format
     */
    public ChatResponseFormat responseFormat() {
        return responseFormat;
    }

    /**
     * Optional tool definitions that the model may request during generation.
     *
     * @return immutable tool list
     */
    public List<ChatTool> tools() {
        return tools;
    }

    /**
     * Optional selection policy for the tools advertised on this request.
     *
     * @return optional tool choice
     */
    public ChatToolChoice toolChoice() {
        return toolChoice;
    }

    /**
     * Builder for {@link ChatRequest}.
     */
    public static final class Builder {
        private String model;
        private final List<ChatMessage> messages = new ArrayList<>();
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private final List<String> stopSequences = new ArrayList<>();
        private ChatResponseFormat responseFormat;
        private final List<ChatTool> tools = new ArrayList<>();
        private ChatToolChoice toolChoice;

        private Builder() {
        }

        /**
         * Overrides the client's default model for this request.
         *
         * @param model provider model name
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Appends one chat message to the request.
         *
         * @param message message to append
         * @return this builder
         */
        public Builder message(ChatMessage message) {
            this.messages.add(Objects.requireNonNull(message, "message must not be null"));
            return this;
        }

        /**
         * Appends multiple chat messages in list order.
         *
         * @param messages messages to append
         * @return this builder
         */
        public Builder messages(List<ChatMessage> messages) {
            Objects.requireNonNull(messages, "messages must not be null");
            messages.forEach(this::message);
            return this;
        }

        /**
         * Sets the sampling temperature for this request.
         *
         * @param temperature non-negative finite sampling temperature
         * @return this builder
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling value for this request.
         *
         * @param topP non-negative finite nucleus sampling value
         * @return this builder
         */
        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the maximum number of output tokens.
         *
         * @param maxTokens positive token cap
         * @return this builder
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Adds a stop sequence.
         *
         * @param stopSequence non-blank stop sequence
         * @return this builder
         */
        public Builder stopSequence(String stopSequence) {
            this.stopSequences.add(requireText(stopSequence, "stopSequence"));
            return this;
        }

        /**
         * Adds multiple stop sequences in list order.
         *
         * @param stopSequences stop sequences to append
         * @return this builder
         */
        public Builder stopSequences(List<String> stopSequences) {
            Objects.requireNonNull(stopSequences, "stopSequences must not be null");
            stopSequences.forEach(this::stopSequence);
            return this;
        }

        /**
         * Sets a structured-output response format for this request.
         *
         * @param responseFormat response format hint to send to compatible providers
         * @return this builder
         */
        public Builder responseFormat(ChatResponseFormat responseFormat) {
            this.responseFormat = Objects.requireNonNull(responseFormat, "responseFormat must not be null");
            return this;
        }

        /**
         * Adds one function tool that the provider may ask the application to execute.
         *
         * @param tool tool definition to advertise
         * @return this builder
         */
        public Builder tool(ChatTool tool) {
            this.tools.add(Objects.requireNonNull(tool, "tool must not be null"));
            return this;
        }

        /**
         * Adds multiple tools in list order.
         *
         * @param tools tool definitions to advertise
         * @return this builder
         */
        public Builder tools(List<ChatTool> tools) {
            Objects.requireNonNull(tools, "tools must not be null");
            tools.forEach(this::tool);
            return this;
        }

        /**
         * Sets how the provider should choose from the advertised tools.
         *
         * @param toolChoice tool-selection policy
         * @return this builder
         */
        public Builder toolChoice(ChatToolChoice toolChoice) {
            this.toolChoice = Objects.requireNonNull(toolChoice, "toolChoice must not be null");
            return this;
        }

        /**
         * Builds an immutable request.
         *
         * @return chat request
         */
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
