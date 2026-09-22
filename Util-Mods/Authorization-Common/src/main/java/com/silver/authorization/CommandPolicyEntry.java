package com.silver.authorization;

import java.util.Objects;
import java.util.Optional;

/** Explicit policy for one exact literal path; unlisted paths are UNMAPPED. */
public record CommandPolicyEntry(
        CommandOrigin origin,
        CommandPath path,
        CommandClassification classification,
        Optional<PermissionPattern> permission,
        Optional<String> source,
        Optional<String> modId) {
    public CommandPolicyEntry {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(classification, "classification");
        permission = Objects.requireNonNull(permission, "permission");
        source = clean(source, "source");
        modId = clean(modId, "modId");
        if ((classification == CommandClassification.PERMISSION) != permission.isPresent()) {
            throw new IllegalArgumentException("Only PERMISSION policies have a permission node");
        }
    }

    public static CommandPolicyEntry of(String path, CommandClassification classification,
                                        String permission, String source, String modId) {
        return of(CommandOrigin.FABRIC_BACKEND, path, classification, permission, source, modId);
    }

    public static CommandPolicyEntry of(CommandOrigin origin, String path, CommandClassification classification,
                                        String permission, String source, String modId) {
        return new CommandPolicyEntry(origin, CommandPath.of(path), classification,
                Optional.ofNullable(permission).map(PermissionPattern::of),
                Optional.ofNullable(source), Optional.ofNullable(modId));
    }

    private static Optional<String> clean(Optional<String> value, String label) {
        Objects.requireNonNull(value, label);
        return value.map(String::strip).filter(text -> !text.isEmpty());
    }
}
