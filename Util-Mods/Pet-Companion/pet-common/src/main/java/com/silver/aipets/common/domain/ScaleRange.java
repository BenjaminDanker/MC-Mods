package com.silver.aipets.common.domain;

/** Inclusive, positive bounds for an entity scale. */
public record ScaleRange(double minimum, double maximum) {
    public ScaleRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("Scale bounds must be finite");
        }
        if (minimum <= 0.0 || maximum < minimum) {
            throw new IllegalArgumentException("Scale range must be positive and ordered");
        }
    }

    public boolean contains(double value) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}
