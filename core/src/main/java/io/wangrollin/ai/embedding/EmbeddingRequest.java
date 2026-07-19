package io.wangrollin.ai.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable request for generating one or more embedding vectors.
 *
 * <p>Batch input is first-class because backend ingestion pipelines normally
 * amortize provider latency across multiple document chunks. The public type
 * deliberately excludes provider-specific encoding formats; adapters request
 * ordinary floating-point vectors that are portable across Java applications.
 */
public final class EmbeddingRequest {
    private final String model;
    private final List<String> inputs;
    private final Integer dimensions;

    private EmbeddingRequest(Builder builder) {
        model = normalizeOptionalText(builder.model);
        if (builder.inputs.isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        inputs = List.copyOf(builder.inputs);
        dimensions = requirePositive(builder.dimensions, "dimensions");
    }

    /**
     * Starts building an embedding request.
     *
     * @return request builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return optional request-level model override */
    public String model() {
        return model;
    }

    /** @return immutable batch inputs in request order */
    public List<String> inputs() {
        return inputs;
    }

    /** @return optional requested vector dimension */
    public Integer dimensions() {
        return dimensions;
    }

    /** Builder for {@link EmbeddingRequest}. */
    public static final class Builder {
        private String model;
        private final List<String> inputs = new ArrayList<>();
        private Integer dimensions;

        private Builder() {
        }

        /** Sets an optional request-level model override. */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** Adds one non-blank text input to the batch. */
        public Builder input(String input) {
            inputs.add(requireText(input, "input"));
            return this;
        }

        /** Adds multiple text inputs in list order. */
        public Builder inputs(List<String> inputs) {
            Objects.requireNonNull(inputs, "inputs must not be null");
            inputs.forEach(this::input);
            return this;
        }

        /** Requests a positive provider-supported vector dimension. */
        public Builder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /** Builds an immutable, non-empty embedding request. */
        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Integer requirePositive(Integer value, String name) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
