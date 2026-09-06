package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.LongTermMemoryStore;
import com.silver.aipets.service.memory.MemoryPage;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Full relational reindex queues every active card exactly once for its version/model. */
public final class MemoryReindexService {
    private final LongTermMemoryStore memories;
    private final MemoryEmbeddingWorker worker;

    public MemoryReindexService(LongTermMemoryStore memories, MemoryEmbeddingWorker worker) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public MemoryReindexResult enqueueAll(int pageSize) {
        if (pageSize < 1 || pageSize > 1_000) {
            throw new IllegalArgumentException("pageSize must be between 1 and 1000");
        }
        int scanned = 0;
        int enqueued = 0;
        int alreadyQueued = 0;
        Optional<UUID> cursor = Optional.empty();
        Set<UUID> observedCursors = new HashSet<>();
        do {
            MemoryPage page = memories.listActive(cursor, pageSize);
            for (LongTermMemoryCard card : page.cards()) {
                EmbeddingJobStore.EnqueueResult result = worker.enqueue(card);
                scanned++;
                if (result.created()) {
                    enqueued++;
                } else {
                    alreadyQueued++;
                }
            }
            cursor = page.nextAfterMemoryId();
            if (cursor.isPresent() && !observedCursors.add(cursor.orElseThrow())) {
                throw new IllegalStateException("Relational memory pagination did not advance");
            }
        } while (cursor.isPresent());
        return new MemoryReindexResult(scanned, enqueued, alreadyQueued);
    }
}
