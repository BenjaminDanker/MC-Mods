package com.silver.authorization;

import java.util.Objects;
import java.util.Optional;

/** The same policy result is used for tree visibility and execution. */
public record CommandPolicyDecision(
        CommandOrigin origin,
        CommandPath path,
        CommandClassification classification,
        Optional<PermissionPattern> requiredPermission,
        Optional<String> source,
        Optional<String> modId,
        boolean visible,
        boolean allowed,
        boolean ownerOverride) {
    public CommandPolicyDecision {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(classification, "classification");
        requiredPermission = Objects.requireNonNull(requiredPermission, "requiredPermission");
        source = Objects.requireNonNull(source, "source");
        modId = Objects.requireNonNull(modId, "modId");
        if (ownerOverride && classification == CommandClassification.INTERNAL) {
            throw new IllegalArgumentException("OWNER cannot override INTERNAL player restrictions");
        }
    }

    public Optional<CommandPolicyEntry> entry() {
        if (classification == CommandClassification.UNMAPPED) return Optional.empty();
        return Optional.of(new CommandPolicyEntry(origin, path, classification, requiredPermission, source, modId));
    }
}
