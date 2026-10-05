package com.silver.aipets.service.memory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Relational source of truth. Every point lookup requires both pet and memory IDs. */
public interface LongTermMemoryStore {
    Optional<LongTermMemoryCard> findActive(UUID petId, UUID memoryId);

    MemoryPage listActive(Optional<UUID> afterMemoryId, int limit);

    /** Returns a bounded active page for one pet without scanning other owners' cards. */
    MemoryPage listActiveForPet(UUID petId, int limit);

    /** Active relational cards whose vector work is still pending, scoped to one pet. */
    default MemoryPage listPendingEmbeddingsForPet(UUID petId, int limit) {
        return new MemoryPage(java.util.List.of(), Optional.empty());
    }

    void markEmbeddingReady(
            UUID petId, UUID memoryId, long expectedVersion, String model, String reference, Instant at);

    void markEmbeddingFailed(UUID petId, UUID memoryId, long expectedVersion, Instant at);

    void recordRecalled(UUID petId, UUID memoryId, Instant at);

    /** Tombstones retain an embedding reference until Qdrant confirms deletion. */
    default java.util.List<LongTermMemoryCard> inactiveForVectorDeletion(int limit) {
        return java.util.List.of();
    }

    default void markVectorDeleted(UUID petId, UUID memoryId, Instant at) { }
}
