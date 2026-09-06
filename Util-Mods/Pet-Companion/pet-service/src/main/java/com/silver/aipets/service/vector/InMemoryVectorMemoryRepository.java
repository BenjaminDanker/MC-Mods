package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Exact-scoping development vector adapter with cosine similarity and metadata prefilters. */
public final class InMemoryVectorMemoryRepository implements VectorMemoryRepository {
    private final Object monitor = new Object();
    private final Map<UUID, Map<UUID, VectorMemoryDocument>> byPet = new HashMap<>();
    private volatile boolean available = true;

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String upsert(VectorMemoryDocument document) {
        Objects.requireNonNull(document, "document");
        requireAvailable();
        synchronized (monitor) {
            LongTermMemoryCard card = document.card();
            byPet.computeIfAbsent(card.petId(), ignored -> new HashMap<>())
                    .put(card.memoryId(), document);
            return "memory:" + card.petId() + ":" + card.memoryId() + ":" + card.version();
        }
    }

    @Override
    public void delete(UUID petId, UUID memoryId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        requireAvailable();
        synchronized (monitor) {
            Map<UUID, VectorMemoryDocument> memories = byPet.get(petId);
            if (memories != null) {
                memories.remove(memoryId);
            }
        }
    }

    @Override
    public List<VectorMemoryMatch> search(
            MemorySearchQuery query, EmbeddingVector queryEmbedding) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding");
        requireAvailable();
        synchronized (monitor) {
            // Select the pet namespace before any similarity work.
            Map<UUID, VectorMemoryDocument> scoped = byPet.getOrDefault(query.petId(), Map.of());
            List<VectorMemoryMatch> matches = new ArrayList<>();
            for (VectorMemoryDocument document : scoped.values()) {
                LongTermMemoryCard card = document.card();
                if (!metadataMatches(card, query)) {
                    continue;
                }
                double similarity = cosine(queryEmbedding.values(), document.embedding().values());
                if (similarity >= query.minimumSimilarity()) {
                    matches.add(new VectorMemoryMatch(
                            card.petId(), card.memoryId(), card.version(), similarity));
                }
            }
            matches.sort(Comparator.comparingDouble(VectorMemoryMatch::similarity).reversed()
                    .thenComparing(match -> match.memoryId().toString()));
            return List.copyOf(matches.subList(0, Math.min(query.maximumResults(), matches.size())));
        }
    }

    private static boolean metadataMatches(LongTermMemoryCard card, MemorySearchQuery query) {
        if (!query.namedEntities().isEmpty()
                && java.util.Collections.disjoint(card.entityTags(), query.namedEntities())) {
            return false;
        }
        return query.dimension().isEmpty()
                || card.locationTags().contains(query.dimension().orElseThrow());
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("Embedding dimensions do not match");
        }
        double dot = 0.0;
        double leftMagnitude = 0.0;
        double rightMagnitude = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private void requireAvailable() {
        if (!available) {
            throw new VectorStoreUnavailableException("Vector repository is unavailable");
        }
    }
}
