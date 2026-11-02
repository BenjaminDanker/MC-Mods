package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.service.http.PetSleepStateReader;
import com.silver.aipets.service.memory.JdbcLongTermMemoryStore;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.silver.aipets.common.transport.PetSceneSnapshot;

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
    private final JdbcOrganicPetContextReader organic;

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
        this(pets, sleep, memories, history, access, null);
    }

    public JdbcDialogueContextLoader(
            PetRepository pets, PetSleepStateReader sleep, DialogueMemoryRetriever memories,
            DialogueHistoryReader history, SubscriptionAccess access,
            JdbcOrganicPetContextReader organic) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.sleep = Objects.requireNonNull(sleep, "sleep");
        this.memories = Objects.requireNonNull(memories, "memories");
        this.history = Objects.requireNonNull(history, "history");
        this.access = Objects.requireNonNull(access, "access");
        this.organic = organic;
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
        return load(ownerUuid, petId, backend, dimension, now, playerMessage,
                PetSceneSnapshot.unknown());
    }

    @Override
    public Optional<DialogueContext> load(
            UUID ownerUuid, UUID petId, String backend, String dimension,
            Instant now, String playerMessage, PetSceneSnapshot scene) {
        return load(ownerUuid, petId, backend, dimension, now, playerMessage, scene, false);
    }

    @Override
    public Optional<DialogueContext> loadSpeculatively(UUID ownerUuid, UUID petId,
            String backend, String dimension, Instant now, String playerMessage,
            PetSceneSnapshot scene) {
        return load(ownerUuid, petId, backend, dimension, now, playerMessage, scene, true);
    }

    private Optional<DialogueContext> load(UUID ownerUuid, UUID petId, String backend,
            String dimension, Instant now, String playerMessage, PetSceneSnapshot scene,
            boolean speculative) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(now, "now");
        long identityStart=System.nanoTime();
        Optional<Pet> found = pets.findById(petId)
                .filter(pet -> pet.ownerUuid().equals(ownerUuid));
        DialogueDiagnostics.elapsed("sql_pet_identity",identityStart);
        if (found.isEmpty()) return Optional.empty();
        Pet pet = found.orElseThrow();
        if (!(pet.placement() instanceof PlacedPlacement placed)
                || !placed.isMaterialized()
                || !placed.backendId().value().equals(backend)
                || !placed.dimensionId().toString().equals(dimension)) return Optional.empty();
        if (scene.location().isPresent() && (!scene.location().get().server().equals(backend)
                || !scene.location().get().dimension().equals(dimension))) return Optional.empty();
        long historyStart=System.nanoTime();
        DialogueHistoryReader.DialogueHistory recent = history.read(petId, now, 8);
        DialogueDiagnostics.elapsed("sql_recent_events",historyStart);
        long memoryStart=System.nanoTime();
        List<LongTermMemorySnippet> longTerm = memories.retrieveSpatial(petId, playerMessage, backend, dimension,
                        scene.petBiome(), 3, scene.location(), now, speculative);
        DialogueDiagnostics.elapsed("memory_retrieval_all",memoryStart);
        long stateStart=System.nanoTime();
        boolean sleeping=sleep.isSleeping(petId);
        boolean entitled=access.canAdopt(ownerUuid);
        DialogueDiagnostics.elapsed("sql_sleep_and_entitlement",stateStart);
        OrganicPetContext organicContext = organic == null ? OrganicPetContext.empty(now)
                : organic.read(petId, backend, dimension, DialoguePromptBuilder.memoryCue(playerMessage, recent.recentTurns()), now);
        return Optional.of(new DialogueContext(
                pet,
                sleeping,
                entitled,
                recent.shortTermMemories(),
                longTerm,
                recent.recentTurns(),
                new DialogueGameContext(backend, dimension, "PLAYER_MESSAGE", scene),
                now,
                recent.relationshipEvidence(), organicContext));
    }
}
