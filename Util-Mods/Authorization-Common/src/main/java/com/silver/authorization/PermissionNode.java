package com.silver.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A canonical, concrete permission capability such as {@code server.restart}. */
public record PermissionNode(String value) implements Comparable<PermissionNode> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*");
    public static final int MAX_LENGTH = 128;

    public PermissionNode {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAX_LENGTH || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid canonical permission node: " + value);
        }
    }

    /** Normalizes caller-provided identifiers before applying canonical validation. */
    public static PermissionNode of(String value) {
        Objects.requireNonNull(value, "value");
        return new PermissionNode(value.toLowerCase(Locale.ROOT));
    }

    @Override
    public int compareTo(PermissionNode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
