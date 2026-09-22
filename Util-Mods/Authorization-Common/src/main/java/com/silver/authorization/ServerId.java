package com.silver.authorization;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical network server key, normally the key configured in Velocity's server map. */
public record ServerId(String value) implements Comparable<ServerId> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?");

    public ServerId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid canonical server ID: " + value);
        }
    }

    public static ServerId of(String value) {
        Objects.requireNonNull(value, "value");
        return new ServerId(value.toLowerCase(Locale.ROOT));
    }

    @Override
    public int compareTo(ServerId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
