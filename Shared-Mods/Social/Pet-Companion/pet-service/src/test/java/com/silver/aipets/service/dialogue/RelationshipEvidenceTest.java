package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;
import com.silver.aipets.common.transport.PetSceneSnapshot;
import com.silver.aipets.service.consolidation.CandidateSelection;
import com.silver.aipets.service.consolidation.ConsolidationConfig;
import com.silver.aipets.service.consolidation.ConsolidationEvent;
import com.silver.aipets.service.consolidation.ConsolidationPromptBuilder;
import com.silver.aipets.service.memory.MemoryProvenance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipEvidenceTest {
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void neutralKindnessBoundaryAndRepairSignalsAreEvidenceGated() {
        assertSignal("NONE", 0, "What did you see today?", RelationshipSignal.Type.NONE);
        assertSignal("WARMTH", 1, "Thanks for waiting for me.", RelationshipSignal.Type.WARMTH);
        assertSignal("TRUST", 2, "I trust you; I know you will keep me safe.", RelationshipSignal.Type.TRUST);
        assertSignal("BOUNDARY", 1, "Stop following me while I build.", RelationshipSignal.Type.BOUNDARY);
        assertSignal("FEAR", 2, "You hurt me when you struck me.", RelationshipSignal.Type.FEAR);
        assertSignal("REPAIR", 1, "I'm sorry I pushed you away.", RelationshipSignal.Type.REPAIR);
        assertEquals(2,validated("WARMTH",2,"That meant more to me than I expected.").relationshipSignal().strength(),
                "Luna's contextual signal is not rejected or weakened by a keyword allowlist");
    }

    @Test
    void conflictingEventsPersistAsSeparateEvidenceAndRetryIsIdempotent() {
        Pet pet = pet();
        InMemoryDialogueStateStore store = new InMemoryDialogueStateStore();
        store.put(pet);
        DialoguePersistRequest warmth = request(pet, UUID.randomUUID(), UUID.randomUUID(),
                new RelationshipSignal(RelationshipSignal.Type.WARMTH, 1), "Thanks for waiting.");
        store.commit(warmth);
        Pet afterWarmth = store.pet(pet.petId());
        DialoguePersistRequest boundary = request(afterWarmth, UUID.randomUUID(), UUID.randomUUID(),
                new RelationshipSignal(RelationshipSignal.Type.BOUNDARY, 2), "Stop following me; stay away.");
        store.commit(boundary);

        List<RelationshipEvidence> evidence = store.relationshipEvidence(pet.petId());
        assertEquals(java.util.Set.of(RelationshipSignal.Type.WARMTH, RelationshipSignal.Type.BOUNDARY),
                evidence.stream().map(value -> value.signal().type()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(pet.traits().relationshipSummary(), store.pet(pet.petId()).traits().relationshipSummary(),
                "per-turn evidence must not rewrite the deep relationship summary");

        DialoguePersistRequest retry = request(store.pet(pet.petId()), UUID.randomUUID(),
                warmth.usage().requestId(), new RelationshipSignal(RelationshipSignal.Type.WARMTH, 1),
                "Thanks for waiting.");
        assertThrows(IllegalStateException.class, () -> store.commit(retry));
        assertEquals(2, store.relationshipEvidence(pet.petId()).size());
    }

    @Test
    void staleInteractionCannotCommitAfterNewerStateAndRecentEvidenceIsPromptRelevantOnly() {
        Pet pet = pet();
        InMemoryDialogueStateStore store = new InMemoryDialogueStateStore();
        store.put(pet);
        store.commit(requestAt(pet, UUID.randomUUID(), UUID.randomUUID(),
                new RelationshipSignal(RelationshipSignal.Type.FEAR, 2), "You hurt me.", NOW.plusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> store.commit(request(pet,
                UUID.randomUUID(), UUID.randomUUID(),
                new RelationshipSignal(RelationshipSignal.Type.REPAIR, 1), "I'm sorry.")));
        assertEquals(List.of(RelationshipSignal.Type.FEAR),
                store.relationshipEvidence(pet.petId()).stream()
                        .map(value -> value.signal().type()).toList());

        List<RelationshipEvidence> mixed = List.of(
                new RelationshipEvidence(UUID.randomUUID(), UUID.randomUUID(),
                        new RelationshipSignal(RelationshipSignal.Type.FEAR, 2), NOW.minusSeconds(30)),
                new RelationshipEvidence(UUID.randomUUID(), UUID.randomUUID(),
                        new RelationshipSignal(RelationshipSignal.Type.REPAIR, 1), NOW.minusSeconds(5)));
        DialogueContext context = new DialogueContext(pet, false, true, List.of(), List.of(), List.of(),
                new DialogueGameContext("survival", "minecraft:overworld", "dialogue"), NOW, mixed);
        DialoguePromptBuilder builder = new DialoguePromptBuilder(DialogueLimits.defaultsWithoutReplyCap(),
                new JTokkitDialogueTokenCounter());
        String relevant = builder.build(context, "I'm sorry. Can I stay close? Please forgive me.").text();
        assertTrue(relevant.contains("you were afraid of your owner (strongly)"));
        assertTrue(relevant.contains("you and your owner started making up"));
        // Fresh evidence is part of the pet's feelings whatever the owner says next ...
        String unrelated = builder.build(context, "What happened on the spruce bridge?").text();
        assertTrue(unrelated.contains("you were afraid of your owner (strongly)"));
        // ... while older evidence returns only when the conversation turns to the relationship.
        DialogueContext later = new DialogueContext(pet, false, true, List.of(), List.of(), List.of(),
                new DialogueGameContext("survival", "minecraft:overworld", "dialogue"), NOW.plus(Duration.ofDays(2)), mixed);
        assertFalse(builder.build(later, "What happened on the spruce bridge?").text().contains("afraid of your owner"));
        assertTrue(builder.build(later, "I'm sorry about before.").text().contains("afraid of your owner"));
    }

    @Test
    void nextQueuedInteractionReadsCommittedEvidenceAndSleepPromptCanAggregateIt() throws Exception {
        Pet pet = pet();
        InMemoryDialogueStateStore store = new InMemoryDialogueStateStore();
        store.put(pet);
        UUID petId = pet.petId();
        CompletableFuture<Void> releaseFirst = new CompletableFuture<>();
        AtomicReference<List<RelationshipEvidence>> nextPromptEvidence = new AtomicReference<>(List.of());
        PetInteractionQueue<String, String> queue = new PetInteractionQueue<>(3,
                Duration.ofSeconds(1), Runnable::run);
        var first = queue.submit(petId, "thanks", input -> {
            store.commit(request(store.pet(petId), UUID.randomUUID(), UUID.randomUUID(),
                    new RelationshipSignal(RelationshipSignal.Type.WARMTH, 1), "Thanks for waiting."));
            return CompletableFuture.completedFuture(new PetInteractionQueue.Interaction<>(
                    CompletableFuture.completedFuture("warmth committed"), releaseFirst));
        });
        var second = queue.submit(petId, "sorry", input -> {
            nextPromptEvidence.set(store.relationshipEvidence(petId));
            return CompletableFuture.completedFuture(new PetInteractionQueue.Interaction<>(
                    CompletableFuture.completedFuture("prompt built"), CompletableFuture.completedFuture(null)));
        });
        assertTrue(second.accepted());
        assertEquals("warmth committed", first.response().get(1, TimeUnit.SECONDS));
        assertFalse(second.response().isDone());
        releaseFirst.complete(null);
        assertEquals("prompt built", second.response().get(1, TimeUnit.SECONDS));
        assertEquals(RelationshipSignal.Type.WARMTH, nextPromptEvidence.get().getFirst().signal().type());

        RelationshipEvidence boundary = nextPromptEvidence.get().getFirst();
        ConsolidationEvent consolidated = new ConsolidationEvent(boundary.eventId(), petId,
                DialogueImportance.MEDIUM,
                "Owner said: Stop following me. Relationship evidence type=BOUNDARY strength=2 "
                        + "interaction_id=" + boundary.interactionId() + ".",
                "DIALOGUE", NOW, MemoryProvenance.OWNER_STATEMENT);
        ConsolidationEvent repaired = new ConsolidationEvent(UUID.randomUUID(), petId,
                DialogueImportance.MEDIUM,
                "Owner said: I'm sorry I pushed you away. Relationship evidence type=REPAIR strength=1 "
                        + "interaction_id=" + UUID.randomUUID() + ".",
                "DIALOGUE", NOW.plusSeconds(1), MemoryProvenance.OWNER_STATEMENT);
        var prompt = new ConsolidationPromptBuilder(ConsolidationConfig.defaults(),
                new JTokkitDialogueTokenCounter()).build(pet,
                new CandidateSelection(List.of(consolidated, repaired), List.of(), 20));
        assertTrue(prompt.prompt().text().contains("type=BOUNDARY strength=2"));
        assertTrue(prompt.prompt().text().contains("type=REPAIR strength=1"));
        queue.close();
    }

    private static void assertSignal(String type, int strength, String ownerMessage,
            RelationshipSignal.Type expected) {
        assertEquals(expected, validated(type, strength, ownerMessage).relationshipSignal().type());
    }

    private static ValidatedDialogueOutput validated(String type, int strength, String ownerMessage) {
        String json = "{\"reply\":\"Understood.\",\"action\":\"NONE\","
                + "\"attention\":\"CALM\",\"memory_candidate\":null,\"affect_delta\":\"NONE\","
                + "\"relationship_event\":{\"type\":\"" + type + "\",\"strength\":" + strength + "}}";
        var limits = DialogueLimits.defaultsWithoutReplyCap();
        return new DialogueResponseMapper(limits).map(new DialogueOutputCodec().decode(json), ownerMessage);
    }

    private static DialoguePersistRequest request(Pet expectedPet, UUID eventId, UUID requestId,
            RelationshipSignal signal, String ownerMessage) {
        EnumMap<TraitName, Integer> traits = new EnumMap<>(TraitName.class);
        for (TraitName name : TraitName.values()) traits.put(name, 0);
        EnumMap<MoodDimension, Integer> moods = new EnumMap<>(MoodDimension.class);
        for (MoodDimension name : MoodDimension.values()) moods.put(name, 0);
        var output = new ValidatedDialogueOutput("Okay.", DialogueImportance.MEDIUM,
                Optional.empty(), traits, moods, PetDialogueAction.NONE,
                PetDialogueAttention.CALM, DialogueAffect.NONE, signal);
        var usage = new DialogueUsage(UUID.randomUUID(), requestId, expectedPet.petId(), OWNER,
                "test-model", Optional.empty(), 0, 0, 0, BigDecimal.ZERO, 1,
                DialogueUsage.Status.SUCCEEDED, Optional.empty(), NOW);
        return new DialoguePersistRequest(eventId, expectedPet, ownerMessage, output, usage,
                new DialogueGameContext("survival", "minecraft:overworld", "dialogue"), NOW);
    }

    private static DialoguePersistRequest requestAt(Pet expectedPet, UUID eventId, UUID requestId,
            RelationshipSignal signal, String ownerMessage, Instant occurredAt) {
        DialoguePersistRequest base = request(expectedPet, eventId, requestId, signal, ownerMessage);
        DialogueUsage old = base.usage();
        DialogueUsage usage = new DialogueUsage(old.callId(), old.requestId(), old.petId(), old.ownerUuid(),
                old.model(), old.providerId(), old.inputTokens(), old.cachedInputTokens(), old.outputTokens(),
                old.estimatedCost(), old.latencyMillis(), old.status(), old.errorCategory(), occurredAt);
        return new DialoguePersistRequest(base.eventId(), base.expectedPet(), base.normalizedPlayerMessage(),
                base.output(), usage, base.gameContext(), occurredAt);
    }

    private static Pet pet() {
        PetAppearance appearance = PetAppearance.create(PetSpecies.CAT, "minecraft:tabby",
                0.7, 1L, AppearanceRules.defaults());
        return Pet.adopted(UUID.randomUUID(), OWNER, "Mica", appearance,
                PetTraits.initial(50, 50, 50, 50, 50, NOW), PetMood.initial(50, 10, 10, 10, NOW), NOW);
    }
}
