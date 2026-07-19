package io.wangrollin.ai.embedding;

import java.util.List;
import java.util.Objects;

/**
 * One embedding vector returned for an input at a stable batch index.
 *
 * @param index zero-based input index assigned by the provider
 * @param vector immutable floating-point embedding vector
 */
public record Embedding(int index, List<Double> vector) {
    /** Validates the stable index and creates an immutable finite vector. */
    public Embedding {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        Objects.requireNonNull(vector, "vector must not be null");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("vector values must be finite");
            }
        }
        vector = List.copyOf(vector);
    }
}
