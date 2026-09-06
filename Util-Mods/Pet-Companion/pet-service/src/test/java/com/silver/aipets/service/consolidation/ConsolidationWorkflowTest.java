package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialoguePrompt;
import com.silver.aipets.service.dialogue.DialogueTokenCounter;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.sleep.InMemoryPetSleepStateStore;
import com.silver.aipets.service.sleep.PetSleepEvent;
import com.silver.aipets.service.sleep.PetSleepPolicy;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.sleep.PetSleepState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsolidationWorkflowTest {
    private static final Instant START = Instant.parse("2026-08-31T00:00:00Z");
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final WordTokens TOKENS = new WordTokens();

    @Test
    void selectionIsDeterministicHighDistinctMediumRepeatedLowAndNoiseAware() {
        ConsolidationConfig config = ConsolidationConfig.defaults();
        ConsolidationCandidateSelector selector = new ConsolidationCandidateSelector(config, TOKENS);
        UUID high = id(10), mediumNew = id(20), mediumOld = id(21), lowNew = id(30), lowOld = id(31);
        List<ConsolidationEvent> input = List.of(
                event(lowOld, DialogueImportance.LOW, "Owner found an emerald", "DIALOGUE", 1),
                event(mediumOld, DialogueImportance.MEDIUM, "Owner explored a cave", "DIALOGUE", 2),
                event(id(40), DialogueImportance.LOW, "A one-off footstep", "DIALOGUE", 3),
                event(high, DialogueImportance.HIGH, "Owner rescued Mochi from lava", "DIALOGUE", 4),
                event(mediumNew, DialogueImportance.MEDIUM, "Owner explored a cave", "DIALOGUE", 5),
                event(lowNew, DialogueImportance.LOW, "Owner found an emerald", "DIALOGUE", 6),
                event(id(50), DialogueImportance.HIGH, "duplicate retry response", "RETRY", 7));

        CandidateSelection selected = selector.select(PET_ID, input);
        assertEquals(List.of(high, mediumNew, lowNew), selected.selected().stream()
                .map(ConsolidationEvent::eventId).toList());
        assertEquals(4, selected.discardedEventIds().size());
        assertTrue(selected.selectedTokens() <= config.maximumInputTokens());

        ConsolidationOutputCodec codec = new ConsolidationOutputCodec();
        assertThrows(ConsolidationOutputException.class,
                () -> codec.decode("{\"memory_cards\":[],\"relationship_summary\":\"ok\","
                        + "\"trait_deltas\":{},\"unexpected\":true}"));
        assertThrows(ConsolidationOutputException.class,
                () -> new ConsolidationOutputValidator(config, TOKENS)
                        .validate(codec.decode(validJson(id(999))), selected));
    }

    @Test
    void emptyAndInactiveCyclesAreIdempotentAndNeverCallTheModel() {
        MutableClock clock = new MutableClock(START);
        QueueModel model = new QueueModel();
        AtomicInteger ids = new AtomicInteger(100);
        InMemoryConsolidationRepository repository = repository(ids);
        repository.putPet(pet(START));
        ConsolidationWorker active = worker(repository, model, clock, owner -> true, card -> { }, ids);
        UUID emptyCycle = id(60);
        assertTrue(active.enqueueAtSleepStart(PET_ID, emptyCycle).created());
        assertFalse(active.enqueueAtSleepStart(PET_ID, emptyCycle).created());
        assertEquals(1, active.processDue("worker-a", 10));
        assertEquals(ConsolidationJobStatus.SUCCEEDED,
                repository.findByKey(key(emptyCycle)).orElseThrow().status());
        assertEquals(0, model.calls);
        assertEquals(0, repository.usageCount());

        UUID pending = id(61);
        repository.addEvent(event(pending, DialogueImportance.HIGH,
                "Owner shared a diamond with Mochi", "DIALOGUE", 1));
        ConsolidationWorker inactive = worker(repository, model, clock, owner -> false, card -> { }, ids);
        UUID inactiveCycle = id(62);
        inactive.enqueueAtSleepStart(PET_ID, inactiveCycle);
        assertEquals(1, inactive.processDue("worker-b", 10));
        assertEquals(0, model.calls);
        assertEquals(ConsolidationEventStatus.PENDING, repository.eventStatus(pending));
        assertTrue(repository.promptEligible(pending));

        // A fresh worker over the durable store replays neither completed cycle.
        ConsolidationWorker restarted = worker(repository, model, clock, owner -> true, card -> { }, ids);
        assertFalse(restarted.enqueueAtSleepStart(PET_ID, inactiveCycle).created());
        assertEquals(0, restarted.processDue("worker-c", 10));
    }

    @Test
    void meaningfulCycleCommitsBoundedGroundedStateAndFailureCannotExtendSleep() {
        MutableClock clock = new MutableClock(START);
        AtomicInteger ids = new AtomicInteger(200);
        InMemoryConsolidationRepository repository = repository(ids);
        repository.putPet(pet(START));
        UUID rescued = id(70), cave = id(71), noise = id(72);
        repository.addEvent(event(rescued, DialogueImportance.HIGH,
                "Owner rescued Mochi from lava near the village", "DIALOGUE", 1));
        repository.addEvent(event(cave, DialogueImportance.MEDIUM,
                "Mochi explored a cave with the owner", "GAMEPLAY", 2));
        repository.addEvent(event(noise, DialogueImportance.LOW,
                "request failed because provider timed out", "SYSTEM", 3));
        QueueModel model = new QueueModel();
        model.failures.add(new IllegalStateException("secret response must not persist"));
        model.responses.add(validJson(rescued));
        List<LongTermMemoryCard> embeddings = new ArrayList<>();
        ConsolidationWorker worker = worker(
                repository, model, clock, owner -> true, embeddings::add, ids);

        InMemoryPetSleepStateStore sleepStore = new InMemoryPetSleepStateStore();
        sleepStore.put(OWNER, PetSleepState.initial(PET_ID, START, PetSleepPolicy.defaults()));
        PetSleepService sleep = new PetSleepService(
                sleepStore, PetSleepPolicy.defaults(), clock, new SleepConsolidationTrigger(worker));
        sleep.ownerOnline(OWNER);
        clock.advance(Duration.ofHours(23));
        assertEquals(PetSleepEvent.SLEEP_STARTED_FORCED, sleep.processDue(10).getFirst().event());
        Instant exactEnd = START.plus(Duration.ofHours(24));
        assertEquals(exactEnd, sleepStore.find(PET_ID).orElseThrow().sleepEndsAt().orElseThrow());

        assertEquals(1, worker.processDue("worker-a", 10));
        ConsolidationJob retry = repository.findByKey(repositoryKey()).orElseThrow();
        assertEquals(ConsolidationJobStatus.RETRY, retry.status());
        assertEquals("IllegalStateException", retry.lastErrorCategory().orElseThrow());
        assertEquals(exactEnd, sleepStore.find(PET_ID).orElseThrow().sleepEndsAt().orElseThrow());
        assertTrue(sleepStore.find(PET_ID).orElseThrow().sleeping());

        clock.advance(Duration.ofMinutes(1));
        // Simulate a service restart: a fresh worker resumes the same durable retry job.
        ConsolidationWorker restarted = worker(
                repository, model, clock, owner -> true, embeddings::add, ids);
        assertEquals(1, restarted.processDue("worker-b", 10));
        assertEquals(ConsolidationJobStatus.SUCCEEDED,
                repository.findByKey(repositoryKey()).orElseThrow().status());
        assertEquals(2, model.calls);
        assertEquals(2, repository.usageCount());
        assertEquals(1, repository.memories(PET_ID).size());
        LongTermMemoryCard card = repository.memories(PET_ID).getFirst();
        assertTrue(TOKENS.count(card.text()) <= ConsolidationConfig.defaults().maximumCardTokens());
        assertEquals(java.util.Set.of(rescued), repository.memorySources(card.memoryId()));
        assertEquals(List.of(card), embeddings);
        assertEquals(ConsolidationEventStatus.CONSOLIDATED, repository.eventStatus(rescued));
        assertEquals(ConsolidationEventStatus.CONSOLIDATED, repository.eventStatus(cave));
        assertEquals(ConsolidationEventStatus.DISCARDED, repository.eventStatus(noise));
        assertFalse(repository.promptEligible(rescued));
        assertEquals("Mochi trusts the owner after the lava rescue.",
                repository.pet(PET_ID).traits().relationshipSummary());
        assertEquals(1, repository.pet(PET_ID).traits().summaryVersion());
        assertEquals(51, repository.pet(PET_ID).traits().curiosity());
        assertEquals(42, repository.pet(PET_ID).traits().attachment());
        assertEquals(8, repository.audits().size());

        clock.advance(Duration.ofMinutes(59));
        assertEquals(PetSleepEvent.SLEEP_COMPLETED, sleep.processDue(10).getFirst().event());
        assertFalse(sleepStore.find(PET_ID).orElseThrow().sleeping());
        assertEquals(exactEnd, sleepStore.find(PET_ID).orElseThrow().lastSleepCompletedAt().orElseThrow());
    }

    private static ConsolidationWorker worker(
            InMemoryConsolidationRepository repository,
            QueueModel model,
            Clock clock,
            com.silver.aipets.service.subscription.SubscriptionAccess subscription,
            java.util.function.Consumer<LongTermMemoryCard> embeddings,
            AtomicInteger ids) {
        ConsolidationConfig config = ConsolidationConfig.defaults();
        return new ConsolidationWorker(
                config, repository, subscription,
                new ConsolidationCandidateSelector(config, TOKENS),
                new ConsolidationPromptBuilder(config, TOKENS), model,
                new ConsolidationOutputCodec(), new ConsolidationOutputValidator(config, TOKENS),
                embeddings, clock, () -> id(ids.incrementAndGet()),
                () -> id(ids.incrementAndGet()));
    }

    private static InMemoryConsolidationRepository repository(AtomicInteger ids) {
        return new InMemoryConsolidationRepository(
                () -> id(ids.incrementAndGet()), () -> id(ids.incrementAndGet()));
    }

    private static ConsolidationEvent event(
            UUID id, DialogueImportance importance, String summary, String type, long seconds) {
        return new ConsolidationEvent(id, PET_ID, importance, summary, type, START.plusSeconds(seconds));
    }

    private static Pet pet(Instant at) {
        return new Pet(
                PET_ID, OWNER, "Mochi",
                PetAppearance.create(PetSpecies.CAT, "minecraft:tabby", 0.7, 42L,
                        AppearanceRules.defaults()),
                new PetTraits(50, 50, 50, 50, 50, 40, 45, 40,
                        "A cautious new friendship.", 0, at),
                PetMood.initial(80, 40, 20, 10, at), HeldPlacement.INSTANCE,
                0, at, at);
    }

    private static String validJson(UUID source) {
        StringBuilder traits = new StringBuilder();
        for (TraitName trait : TraitName.values()) {
            if (!traits.isEmpty()) traits.append(',');
            traits.append('"').append(trait.name().toLowerCase()).append("\":100");
        }
        return "{\"memory_cards\":[{\"text\":\"Owner rescued Mochi from lava near the village.\","
                + "\"importance\":\"HIGH\",\"source_event_ids\":[\"" + source + "\"],"
                + "\"emotion_tags\":[\"grateful\"],\"entity_tags\":[\"owner\"],"
                + "\"location_tags\":[\"village\"]}],"
                + "\"relationship_summary\":\"Mochi trusts the owner after the lava rescue.\","
                + "\"trait_deltas\":{" + traits + "}}";
    }

    private static String key(UUID cycle) {
        return "sleep-consolidation:" + PET_ID + ':' + cycle;
    }

    private static String repositoryKey() {
        String identity = PET_ID + ":" + START.plus(Duration.ofHours(23));
        UUID cycle = UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return key(cycle);
    }

    private static UUID id(long value) { return new UUID(0, value); }

    private static final class QueueModel implements ConsolidationModelClient {
        private final Deque<String> responses = new ArrayDeque<>();
        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        private int calls;

        @Override public String model() { return "mock-consolidation"; }

        @Override
        public DialogueModelResponse complete(
                UUID requestId, DialoguePrompt prompt, String schema, Duration timeout) {
            calls++;
            assertTrue(prompt.inputTokens() <= ConsolidationConfig.defaults().maximumInputTokens());
            assertTrue(schema.contains("\"maxItems\":5"));
            assertTrue(schema.contains("\"additionalProperties\":false"));
            if (!failures.isEmpty()) throw failures.removeFirst();
            return new DialogueModelResponse(
                    responses.removeFirst(), Optional.of("provider-" + calls),
                    prompt.inputTokens(), 0, 80, new BigDecimal("0.02"), 20);
        }
    }

    private static final class WordTokens implements DialogueTokenCounter {
        @Override public int count(String text) {
            String stripped = text.strip();
            return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
        }
        @Override public String truncate(String text, int maximumTokens) {
            if (text.isBlank()) return "";
            String[] words = text.strip().split("\\s+");
            return String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, maximumTokens)));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
