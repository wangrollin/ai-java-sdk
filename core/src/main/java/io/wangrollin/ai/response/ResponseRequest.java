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
    private final List<ResponseInputItem> inputItems;
    private final String instructions;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final ResponseTextFormat textFormat;
    private final String previousResponseId;
    private final Boolean background;
    private final List<ResponseTool> tools;

    private ResponseRequest(Builder builder) {
        this.model = normalizeOptionalText(builder.model);
        this.input = builder.input == null ? null : requireText(builder.input, "input");
        this.inputItems = List.copyOf(builder.inputItems);
        if (this.input == null && this.inputItems.isEmpty()) {
            throw new IllegalArgumentException("input or inputMessages must be configured");
        }
        if (this.input != null && !this.inputItems.isEmpty()) {
            throw new IllegalArgumentException("input and inputMessages cannot both be configured");
        }
        this.instructions = normalizeOptionalText(builder.instructions);
        this.temperature = requireNonNegative(builder.temperature, "temperature");
        this.topP = requireNonNegative(builder.topP, "topP");
        this.maxOutputTokens = requirePositive(builder.maxOutputTokens, "maxOutputTokens");
        this.textFormat = builder.textFormat;
        this.previousResponseId = normalizeOptionalText(builder.previousResponseId);
        this.background = builder.background;
        this.tools = List.copyOf(builder.tools);
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
        return inputItems.stream()
                .filter(ResponseInputMessage.class::isInstance)
                .map(ResponseInputMessage.class::cast)
                .toList();
    }

    /**
     * Returns the typed Responses API input items, or an empty list when this request uses text input.
     *
     * @return immutable input item list
     */
    public List<ResponseInputItem> inputItems() {
        return inputItems;
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
     * Optional previous response id used when continuing a Responses API turn.
     *
     * @return optional provider response id
     */
    public String previousResponseId() {
        return previousResponseId;
    }

    /**
     * Optional provider-side background execution flag.
     *
     * <p>When configured, the value is sent to the provider as-is. The SDK still exposes the same
     * synchronous and streaming Java calls; applications remain responsible for any provider-specific
     * follow-up workflow around background response ids.
     *
     * @return optional background execution flag
     */
    public Boolean background() {
        return background;
    }

    /**
     * Optional function tools that the model may request during generation.
     *
     * @return immutable tool list
     */
    public List<ResponseTool> tools() {
        return tools;
    }

    /**
     * Builder for {@link ResponseRequest}.
     */
    public static final class Builder {
        private String model;
        private String input;
        private final List<ResponseInputItem> inputItems = new ArrayList<>();
        private String instructions;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;
        private ResponseTextFormat textFormat;
        private String previousResponseId;
        private Boolean background;
        private final List<ResponseTool> tools = new ArrayList<>();

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
            this.inputItems.add(Objects.requireNonNull(message, "message must not be null"));
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
         * Adds one function-call output item to continue a prior Responses API turn.
         *
         * @param callId provider call id from {@link ResponseToolCall#callId()}
         * @param output application-provided tool result text
         * @return this builder
         */
        public Builder functionCallOutput(String callId, String output) {
            return inputItem(new ResponseFunctionCallOutput(callId, output));
        }

        /**
         * Adds one typed Responses API input item.
         *
         * @param item input item to append
         * @return this builder
         */
        public Builder inputItem(ResponseInputItem item) {
            this.inputItems.add(Objects.requireNonNull(item, "item must not be null"));
            return this;
        }

        /**
         * Adds multiple typed Responses API input items in list order.
         *
         * @param items input items to append
         * @return this builder
         */
        public Builder inputItems(List<ResponseInputItem> items) {
            Objects.requireNonNull(items, "items must not be null");
            items.forEach(this::inputItem);
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
         * Sets the previous response id when sending tool results or continuing a turn.
         *
         * @param previousResponseId provider response id; blank values are treated as absent
         * @return this builder
         */
        public Builder previousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
            return this;
        }

        /**
         * Requests provider-side background execution for this Responses API call.
         *
         * <p>This is a narrow protocol option rather than an SDK async abstraction. The immediate
         * client method still waits for the provider HTTP response according to the configured
         * timeout, retry, and streaming behavior.
         *
         * @param background whether to ask the provider to run the response in the background
         * @return this builder
         */
        public Builder background(boolean background) {
            this.background = background;
            return this;
        }

        /**
         * Adds one function tool that the provider may ask the application to execute.
         *
         * @param tool tool definition to advertise
         * @return this builder
         */
        public Builder tool(ResponseTool tool) {
            this.tools.add(Objects.requireNonNull(tool, "tool must not be null"));
            return this;
        }

        /**
         * Adds multiple tools in list order.
         *
         * @param tools tools to advertise
         * @return this builder
         */
        public Builder tools(List<ResponseTool> tools) {
            Objects.requireNonNull(tools, "tools must not be null");
            tools.forEach(this::tool);
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
