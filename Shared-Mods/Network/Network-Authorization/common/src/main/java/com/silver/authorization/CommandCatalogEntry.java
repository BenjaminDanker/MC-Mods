package com.silver.authorization;

import java.util.Objects;
import java.util.Optional;

/** One literal Brigadier path observed in a backend's live dispatcher. */
public record CommandCatalogEntry(CommandPath path, boolean executable,
                                  Optional<String> source, Optional<String> modId) {
    public CommandCatalogEntry {
        Objects.requireNonNull(path, "path");
        source = clean(source);
        modId = clean(modId);
    }

    private static Optional<String> clean(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return value.map(String::strip).filter(text -> !text.isEmpty());
    }
}
