package com.silver.aipets.service.vector;

import java.util.List;
import java.util.UUID;

/** Provider-neutral vector boundary. Implementations must filter by query.petId before ranking. */
public interface VectorMemoryRepository {
    String upsert(VectorMemoryDocument document);

    void delete(UUID petId, UUID memoryId);

    List<VectorMemoryMatch> search(MemorySearchQuery query, EmbeddingVector queryEmbedding);
}
