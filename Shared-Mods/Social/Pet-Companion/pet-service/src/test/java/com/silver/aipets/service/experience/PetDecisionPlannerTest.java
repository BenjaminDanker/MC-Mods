package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.*;
import com.silver.aipets.service.billing.AiBudgetService;
import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.dialogue.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class PetDecisionPlannerTest {
    private static final Instant NOW=Instant.parse("2026-09-25T12:00:00Z");

    @Test void expiryRoundTripsAtSqlPrecisionForExactRetry() {
        var decision = new PetDecisionPlanner.Decision("LOOK_AT_OWNER", null,
                Instant.parse("2026-09-25T12:00:00.123456789Z"));
        assertEquals(Instant.parse("2026-09-25T12:00:00.123456Z"),
                decision.expiresAt());
    }

    @Test void heldPetAndExhaustedBudgetNeverCallProvider() {
        Store store=new Store(); Model model=new Model(false);
        PetDecisionPlanner planner=new PetDecisionPlanner(store,deniedBudget(),
                AiPricing.defaults(),model,true);
        assertTrue(planner.consider(pet(true),UUID.randomUUID(),"NEW_AREA",
                "Visited a mesa together",NOW,true).isEmpty());
        assertTrue(planner.consider(pet(false),UUID.randomUUID(),"NEW_AREA",
                "Visited a mesa together",NOW,true).isEmpty());
        assertEquals(0,model.calls);
        assertTrue(store.windows.isEmpty());
    }

    @Test void tokenLimitedPlannerResponseCannotCommitTheSuggestedIntention() {
        Store store = new Store();
        Model model = new Model(false);
        model.finishReason = "length";
        PetDecisionPlanner planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true);

        assertTrue(planner.consider(pet(false), UUID.randomUUID(), "NEW_AREA",
                "A witnessed new path", NOW, true).isEmpty());
        assertEquals("FAILED", store.lastUsage);
        assertEquals("PAUSE", store.windows.values().iterator().next().intent());
    }

    @Test void outageFallsBackAndDurableWindowCoalescesEventsAcrossPlannerRestart() {
        Store store=new Store(); Model model=new Model(true);
        Pet pet=pet(false);
        PetDecisionPlanner first=new PetDecisionPlanner(store,AiBudgetService.UNLIMITED,
                AiPricing.defaults(),model,true);
        assertTrue(first.consider(pet,UUID.randomUUID(),"NEW_AREA",
                "Visited a mesa together",NOW,true).isEmpty());
        assertEquals(1,model.calls);
        assertEquals("FAILED",store.lastUsage);
        PetDecisionPlanner restarted=new PetDecisionPlanner(store,AiBudgetService.UNLIMITED,
                AiPricing.defaults(),model,true);
        restarted.consider(pet,UUID.randomUUID(),"SURVIVED_DANGER",
                "Survived nearby danger",NOW.plusSeconds(20),true);
        assertEquals(1,model.calls);
        model.fail=false;
        var result=restarted.consider(pet,UUID.randomUUID(),"SURVIVED_DANGER",
                "Survived nearby danger",NOW.plusSeconds(61),true);
        assertEquals("LOOK_AT_OWNER",result.orElseThrow().intent());
        assertEquals(2,model.calls);
        assertEquals("SUCCEEDED",store.lastUsage);
    }

    @Test void retryReplaysOnlyItsOwnDecisionWithOriginalWords() {
        Store store = new Store();
        Model model = new Model(false);
        model.words = "There you are.";
        PetDecisionPlanner planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true);
        Pet pet = pet(false);
        UUID first = UUID.randomUUID();
        assertEquals("There you are.", planner.consider(pet, first, "NEW_AREA",
                "Visited a mesa together", NOW, true).orElseThrow().utterance());
        assertEquals("There you are.", planner.replay(pet.petId(), first, NOW.plusSeconds(1))
                .orElseThrow().utterance());
        assertTrue(planner.consider(pet, UUID.randomUUID(), "SURVIVED_DANGER",
                "A new danger", NOW.plusSeconds(1), true).isEmpty());
        assertEquals(1, model.calls);
    }

    @Test void unsolicitedSpeechIsCoalescedInsideTheExistingDecisionCooldown() {
        Store store=new Store(); Model model=new Model(false); model.words="The path is quiet.";
        var planner=new PetDecisionPlanner(store,AiBudgetService.UNLIMITED,AiPricing.defaults(),model,true);
        var first=planner.consider(pet(false),UUID.randomUUID(),"NEW_AREA",
                "Owner and pet visited a new area",NOW,true).orElseThrow();
        assertEquals("The path is quiet.",first.utterance());
        assertTrue(planner.consider(pet(false),UUID.randomUUID(),"NEW_AREA",
                "Owner and pet visited another area",NOW.plusSeconds(15),true).isEmpty());
        assertEquals(1,model.calls,"no second model-generated unsolicited line starts during cooldown");
    }

    @Test void anUnfinishedThreadReturnsAtTheRightPlaceWithoutCallingTheModel() {
        Store store = new Store();
        Model model = new Model(false);
        Pet pet = pet(false);
        UUID bridgeMemory = UUID.randomUUID();
        OpenThreadRecall threads = new OpenThreadRecall() {
            @Override public Optional<ThreadCue> find(Pet candidate, String type,
                    String summary, Instant now) {
                return type.equals("OWNER_RETURNED_AFTER_ABSENCE")
                        && summary.contains("biome=minecraft:river")
                        ? Optional.of(new ThreadCue(bridgeMemory, "bridge")) : Optional.empty();
            }
        };
        PetDecisionPlanner planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true, threads);
        assertTrue(planner.consider(pet, UUID.randomUUID(), "NEW_AREA",
                "biome=minecraft:desert", NOW, true).isPresent());
        // A new planner instance models a return after the prior decision window.
        planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true, threads);
        var result = planner.consider(pet, UUID.randomUUID(),
                "OWNER_RETURNED_AFTER_ABSENCE", "biome=minecraft:river",
                NOW.plusSeconds(121), true).orElseThrow();
        assertTrue(result.utterance().contains("bridge"));
        assertEquals("LOOK_AT_OWNER", result.intent());
        assertEquals(1, model.calls);
    }

    @Test void relevantMemoryChangesSilentActionButUnrelatedMemoryDoesNothing() {
        Store store = new Store(); Model model = new Model(false);
        UUID memoryId = UUID.randomUUID();
        DecisionMemoryRecall recall = (petId, type, summary) ->
                MemoryInfluencePolicy.relevant(type, summary, "We gathered oak logs")
                        ? Optional.of(new DecisionMemoryRecall.Cue(memoryId,
                                "VERIFIED_OBSERVATION", "We gathered oak logs"))
                        : Optional.empty();
        var planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true, OpenThreadRecall.NONE, recall);
        var relevant = planner.consider(pet(false), UUID.randomUUID(), "OWNER_ACTION",
                "Owner broke minecraft:oak_log nearby", NOW, true).orElseThrow();
        assertEquals("LOOK_AT_INTEREST", relevant.intent());
        assertNull(relevant.utterance());
        assertEquals(memoryId, relevant.memoryId());
        assertTrue(planner.consider(pet(false), UUID.randomUUID(), "OWNER_ACTION",
                "Owner broke minecraft:stone nearby", NOW.plusSeconds(46), true).isEmpty());
        assertEquals(0, model.calls);
    }

    @Test void lateModelResultCannotReplaceNewerIntention() {
        Store store = new Store(); Model model = new Model(false);
        Pet pet = pet(false);
        UUID newer = UUID.randomUUID();
        model.beforeReturn = () -> store.save(pet.petId(), newer,
                new PetDecisionPlanner.Decision("SIT", null, NOW.plusSeconds(90)),
                NOW.plusSeconds(1), NOW.plusSeconds(90));
        var planner = new PetDecisionPlanner(store, AiBudgetService.UNLIMITED,
                AiPricing.defaults(), model, true);
        assertTrue(planner.consider(pet, UUID.randomUUID(), "NEW_AREA",
                "Visited a mesa together", NOW, true).isEmpty());
        assertEquals("SIT", planner.replay(pet.petId(), newer,
                NOW.plusSeconds(2)).orElseThrow().intent());
    }

    private static Pet pet(boolean held) {
        UUID id=UUID.fromString("10000000-0000-0000-0000-000000000001");
        return new Pet(id,UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "Mochi",PetAppearance.create(PetSpecies.CAT,"minecraft:tabby",.8,1L,
                        AppearanceRules.defaults()),
                PetTraits.initial(50,50,50,50,50,NOW),
                PetMood.initial(50,20,10,20,NOW),
                held ? HeldPlacement.INSTANCE : PlacedPlacement.materialized(
                        new BackendId("survival"),DimensionId.parse("minecraft:overworld"),
                        new WorldPosition(0,64,0),UUID.randomUUID()),
                1,NOW,NOW);
    }

    private static AiBudgetService deniedBudget() {
        return new AiBudgetService() {
            @Override public Optional<Reservation> tryReserve(UUID owner,BigDecimal maximum,
                    Instant now) { return Optional.empty(); }
            @Override public void settle(Reservation reservation,BigDecimal actual) { }
            @Override public com.silver.aipets.service.billing.AiBudgetSnapshot snapshot(
                    UUID owner,Instant now) { throw new UnsupportedOperationException(); }
        };
    }

    private static final class Store implements DecisionStore {
        private final Map<UUID,PetDecisionPlanner.Decision> windows=new HashMap<>();
        private final Map<UUID,UUID> sources=new HashMap<>();
        private final Map<UUID,Instant> created=new HashMap<>();
        private String lastUsage;
        @Override public Optional<PetDecisionPlanner.Decision> current(UUID petId,Instant now) {
            return Optional.ofNullable(windows.get(petId)).filter(value -> value.expiresAt().isAfter(now));
        }
        @Override public void save(UUID petId,UUID eventId,PetDecisionPlanner.Decision decision,
                Instant now,Instant next) {
            sources.put(petId,eventId);
            created.put(petId,now);
            windows.put(petId,new PetDecisionPlanner.Decision(
                    decision.intent(),decision.utterance(),next,decision.memoryId()));
        }
        @Override public boolean saveIfNewer(UUID petId, UUID eventId,
                PetDecisionPlanner.Decision decision, Instant now, Instant next) {
            Instant old = created.get(petId);
            if (old != null && !old.isBefore(now)
                    && !eventId.equals(sources.get(petId))) return false;
            save(petId,eventId,decision,now,next);
            return true;
        }
        @Override public Optional<PetDecisionPlanner.Decision> forEvent(UUID petId, UUID eventId,
                Instant now) {
            return eventId.equals(sources.get(petId)) ? current(petId,now) : Optional.empty();
        }
        @Override public void usage(Pet pet,UUID requestId,String model,
                DialogueModelResponse result,String status,Instant now) { lastUsage=status; }
    }

    private static final class Model implements DialogueModelClient {
        private boolean fail;
        private int calls;
        private String finishReason = "stop";
        private String words;
        private Runnable beforeReturn;
        private Model(boolean fail) { this.fail=fail; }
        @Override public String model() { return "gpt-6-luna"; }
        @Override public DialogueModelResponse complete(UUID requestId,DialoguePrompt prompt,
                String schema,Duration timeout) {
            calls++;
            if (fail) throw new IllegalStateException("provider outage");
            if (beforeReturn != null) beforeReturn.run();
            String json = "{\"intent\":\"LOOK_AT_OWNER\",\"utterance\":"
                    + (words == null ? "null" : "\"" + words + "\"")
                    + ",\"memory_candidate\":null,\"reconsider_after_seconds\":30}";
            return new DialogueModelResponse(json,Optional.empty(),120,0,20,
                    new BigDecimal("0.00002200"),10, finishReason, 2000, Optional.empty());
        }
    }
}
