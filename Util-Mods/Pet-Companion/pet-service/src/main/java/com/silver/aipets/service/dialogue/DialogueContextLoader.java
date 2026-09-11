package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Loads authoritative, owner-scoped context before a dialogue provider is called. */
@FunctionalInterface
public interface DialogueContextLoader {
    Optional<DialogueContext> load(
            UUID ownerUuid, UUID petId, String backend, String dimension, Instant now);

    /** Optional query-aware overload; legacy loaders retain their existing behaviour. */
    default Optional<DialogueContext> load(
            UUID ownerUuid, UUID petId, String backend, String dimension,
            Instant now, String playerMessage) {
        return load(ownerUuid, petId, backend, dimension, now);
    }
}
