package io.wangrollin.ai.response;

/**
 * Immutable text-first request for the OpenAI-compatible Responses API.
 */
public final class ResponseRequest {
    private final String model;
    private final String input;
    private final String instructions;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;

    private ResponseRequest(Builder builder) {
        this.model = normalizeOptionalText(builder.model);
        this.input = requireText(builder.input, "input");
        this.instructions = normalizeOptionalText(builder.instructions);
        this.temperature = requireNonNegative(builder.temperature, "temperature");
        this.topP = requireNonNegative(builder.topP, "topP");
        this.maxOutputTokens = requirePositive(builder.maxOutputTokens, "maxOutputTokens");
    }

    /**
     * Starts building a response request.
     *
     * @return request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String model() {
        return model;
    }

    public String input() {
        return input;
    }

    public String instructions() {
        return instructions;
    }

    public Double temperature() {
        return temperature;
    }

    public Double topP() {
        return topP;
    }

    public Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * Builder for {@link ResponseRequest}.
     */
    public static final class Builder {
        private String model;
        private String input;
        private String instructions;
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;

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
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
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
