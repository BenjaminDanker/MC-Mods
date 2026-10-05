package com.silver.aipets.service.dialogue;

import java.util.List;
import java.util.UUID;

/** Bounded, pet-scoped long-term memory lookup used while assembling dialogue context. */
@FunctionalInterface
public interface DialogueMemoryRetriever {
    List<LongTermMemorySnippet> retrieve(
            UUID petId, String playerMessage, String backend, String dimension, int limit);

    default List<LongTermMemorySnippet> retrieve(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, limit);
    }

    default List<LongTermMemorySnippet> retrieveSpatial(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit,
            java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> location,
            java.time.Instant now, boolean speculative) {
        return speculative ? retrieveSpeculatively(petId, playerMessage, backend, dimension, biome, limit)
                : retrieve(petId, playerMessage, backend, dimension, biome, limit);
    }

    default List<LongTermMemorySnippet> retrieveSpeculatively(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, biome, limit);
    }
}
