package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs only when a local Qdrant endpoint is supplied by the verification command. */
final class QdrantVectorMemoryRepositoryTest {
    @Test
    void indexesAndSearchesOnlyWithinThePetNamespace() {
        String endpoint = System.getenv("PET_TEST_QDRANT_URL");
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        UUID petId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        String collection = "aipets_test_" + UUID.randomUUID().toString().replace('-', '_');
        QdrantVectorMemoryRepository repository = new QdrantVectorMemoryRepository(
                URI.create(endpoint), collection, 3, Duration.ofSeconds(5), Optional.empty());
        LongTermMemoryCard card = new LongTermMemoryCard(
                memoryId, petId, 1, "Mochi likes the garden.", MemoryImportance.MEDIUM,
                Set.of("happy"), Set.of("Mochi"), Set.of("garden"), true);

        assertEquals(memoryId.toString(), repository.upsert(
                new VectorMemoryDocument(card, new EmbeddingVector("local-test", new float[]{1, 0, 0}))));
        MemorySearchQuery query = new MemorySearchQuery(
                petId, "garden", Optional.empty(), Optional.empty(), Optional.empty(),
                Set.of(), 0.5, 3);
        assertEquals(List.of(memoryId), repository.search(
                query, new EmbeddingVector("local-test", new float[]{1, 0, 0}))
                .stream().map(VectorMemoryMatch::memoryId).toList());

        MemorySearchQuery otherPet = new MemorySearchQuery(
                UUID.randomUUID(), "garden", Optional.empty(), Optional.empty(), Optional.empty(),
                Set.of(), -1.0, 3);
        assertTrue(repository.search(
                otherPet, new EmbeddingVector("local-test", new float[]{1, 0, 0})).isEmpty());
        repository.delete(petId, memoryId);
        assertTrue(repository.search(
                query, new EmbeddingVector("local-test", new float[]{1, 0, 0})).isEmpty());
    }
}
