package com.silver.aipets.service.vector;

import java.util.Arrays;
import java.util.Objects;

public record EmbeddingVector(String model, float[] values) {
    public EmbeddingVector {
        Objects.requireNonNull(model, "model");
        model = model.strip();
        if (model.isEmpty() || model.length() > 191) {
            throw new IllegalArgumentException("model length is invalid");
        }
        values = Arrays.copyOf(Objects.requireNonNull(values, "values"), values.length);
        if (values.length < 1 || values.length > 16_384) {
            throw new IllegalArgumentException("embedding dimension is invalid");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
        }
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
