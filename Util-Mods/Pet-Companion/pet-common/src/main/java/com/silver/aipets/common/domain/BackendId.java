package com.silver.aipets.common.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical network backend identifier. */
public record BackendId(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public BackendId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid canonical backend identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
