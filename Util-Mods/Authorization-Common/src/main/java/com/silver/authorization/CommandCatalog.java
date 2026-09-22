package com.silver.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Signed-source metadata for the runtime command inventory on one canonical backend. */
public record CommandCatalog(int protocolVersion, ServerId serverId, UUID backendEpoch,
                             long generation, UUID nonce, Instant issuedAt,
                             List<CommandCatalogEntry> entries) {
    public static final int CURRENT_PROTOCOL_VERSION = 1;
    public static final int MAX_ENTRIES = 20_000;

    public CommandCatalog {
        if (protocolVersion != CURRENT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported command-catalog protocol version");
        }
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(backendEpoch, "backendEpoch");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(issuedAt, "issuedAt");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Command catalog is too large");
        if (entries.stream().map(CommandCatalogEntry::path).distinct().count() != entries.size()) {
            throw new IllegalArgumentException("Duplicate command path in catalog");
        }
    }
}
