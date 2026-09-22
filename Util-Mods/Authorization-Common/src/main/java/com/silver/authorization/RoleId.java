package com.silver.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Data-driven role key; this is intentionally not an enum. */
public record RoleId(String value) implements Comparable<RoleId> {
    private static final Pattern VALID = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,63}");

    public RoleId {
        Objects.requireNonNull(value, "value");
        value = value.toUpperCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid role key: " + value);
        }
    }

    public static RoleId of(String value) {
        return new RoleId(value);
    }

    @Override
    public int compareTo(RoleId other) {
        return value.compareTo(other.value);
    }
}
