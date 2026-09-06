package com.silver.aipets.common.domain;

import java.util.Objects;

/** Type-safe Minecraft dimension identifier. */
public record DimensionId(ResourceId value) {
    public DimensionId {
        Objects.requireNonNull(value, "value");
    }

    public static DimensionId parse(String value) {
        return new DimensionId(ResourceId.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
