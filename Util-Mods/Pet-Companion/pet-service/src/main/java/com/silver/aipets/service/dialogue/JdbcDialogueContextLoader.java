package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.http.PetSleepStateReader;
import com.silver.aipets.service.memory.JdbcLongTermMemoryStore;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral JDBC context loader. Identity and sleep are authoritative SQL reads;
 * long-term cards are bounded and pet-scoped. The retriever may use vectors but must
 * return authoritative relational cards.
 */
public final class JdbcDialogueContextLoader implements DialogueContextLoader {
    private final PetRepository pets;
    private final PetSleepStateReader sleep;
    private final DialogueMemoryRetriever memories;
    private final DialogueHistoryReader history;
    private final SubscriptionAccess access;

    public JdbcDialogueContextLoader(
            PetRepository pets,
            PetSleepStateReader sleep,
            JdbcLongTermMemoryStore memories,
            DialogueHistoryReader history,
            SubscriptionAccess access) {
        this(pets, sleep, new RelationalDialogueMemoryRetriever(memories), history, access);
    }

    public JdbcDialogueContextLoader(
            PetRepository pets,
            PetSleepStateReader sleep,
            DialogueMemoryRetriever memories,
            DialogueHistoryReader history,
            SubscriptionAccess access) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.sleep = Objects.requireNonNull(sleep, "sleep");
        this.memories = Objects.requireNonNull(memories, "memories");
        this.history = Objects.requireNonNull(history, "history");
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public Optional<DialogueContext> load(
            UUID ownerUuid, UUID petId, String backend, String dimension, Instant now) {
        return load(ownerUuid, petId, backend, dimension, now, "");
    }

    @Override
    public Optional<DialogueContext> load(
            UUID ownerUuid, UUID petId, String backend, String dimension,
            Instant now, String playerMessage) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(now, "now");
        Optional<Pet> found = pets.findById(petId)
                .filter(pet -> pet.ownerUuid().equals(ownerUuid));
        if (found.isEmpty()) return Optional.empty();
        Pet pet = found.orElseThrow();
        DialogueHistoryReader.DialogueHistory recent = history.read(petId, now, 8);
        List<LongTermMemorySnippet> longTerm = memories.retrieve(
                petId, playerMessage, backend, dimension, 3);
        return Optional.of(new DialogueContext(
                pet,
                sleep.isSleeping(petId),
                access.canAdopt(ownerUuid),
                recent.shortTermMemories(),
                longTerm,
                recent.recentTurns(),
                new DialogueGameContext(backend, dimension, "PLAYER_MESSAGE"),
                now));
    }
}
