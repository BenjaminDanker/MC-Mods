package com.silver.aipets.common.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** A validated Minecraft namespaced identifier such as {@code minecraft:overworld}. */
public record ResourceId(String value) {
    private static final Pattern PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ResourceId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid namespaced resource identifier: " + value);
        }
    }

    public static ResourceId parse(String value) {
        return new ResourceId(value);
    }

    public String namespace() {
        return value.substring(0, value.indexOf(':'));
    }

    public String path() {
        return value.substring(value.indexOf(':') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
