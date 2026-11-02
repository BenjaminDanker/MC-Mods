package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.memory.MemoryProvenance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RepeatedWitnessSelectionTest {
    private static final UUID PET = new UUID(0, 501);
    private final ConsolidationCandidateSelector selector = new ConsolidationCandidateSelector(
            ConsolidationConfig.defaults(), new com.silver.aipets.service.dialogue.JTokkitDialogueTokenCounter());

    @Test void repeatedWitnessesRetainThreeDistinctSourcesWithinExistingLimits() {
        var events = new ArrayList<ConsolidationEvent>();
        for (int i = 1; i <= 5; i++) events.add(event(i, MemoryProvenance.VERIFIED_OBSERVATION,
                DialogueImportance.MEDIUM));
        var selected = selector.select(PET, events);
        assertEquals(List.of(new UUID(0, 5), new UUID(0, 4), new UUID(0, 3)),
                selected.selected().stream().map(ConsolidationEvent::eventId).toList());
        assertEquals(2, selected.discardedEventIds().size());
        assertTrue(selected.selectedTokens() <= ConsolidationConfig.defaults().maximumInputTokens());
    }

    @Test void duplicateEventIdCannotSupplyRepeatedLowEvidence() {
        var once = event(1, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.LOW);
        assertTrue(selector.select(PET, List.of(once, once, once)).selected().isEmpty());
        var twice = event(2, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.LOW);
        assertEquals(2, selector.select(PET, List.of(once, twice)).selected().size());
    }

    @Test void ownerRetellingsDoNotDisplaceWitnessedEvidenceOrBecomeWitnesses() {
        var first = event(1, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.MEDIUM);
        var second = event(2, MemoryProvenance.OWNER_STATEMENT, DialogueImportance.MEDIUM);
        var third = event(3, MemoryProvenance.OWNER_STATEMENT, DialogueImportance.MEDIUM);
        var selected = selector.select(PET, List.of(first, second, third)).selected();
        assertEquals(2, selected.size());
        assertTrue(selected.contains(first));
        assertTrue(selected.contains(third));
        assertFalse(selected.contains(second));
    }

    @Test void repetitionGateDoesNotCombineWitnessesWithOwnerReports() {
        var witnessed = event(1, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.LOW);
        var reported = event(2, MemoryProvenance.OWNER_STATEMENT, DialogueImportance.LOW);
        assertTrue(selector.select(PET, List.of(witnessed, reported)).selected().isEmpty());
    }

    @Test void sameSummaryInDistinctSavesRetainsSeparateEvidenceGroups() {
        var events = new ArrayList<ConsolidationEvent>();
        for (int i = 1; i <= 4; i++) {
            var source = event(i, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.MEDIUM);
            events.add(new ConsolidationEvent(source.eventId(), PET, source.importance(), source.summary(),
                    source.eventType(), source.occurredAt(), source.provenance(), source.sourceServer(),
                    source.sourceDimension(), java.util.Optional.of(new com.silver.aipets.common.transport.SpatialLocation(
                            "survival", "save-" + i, "minecraft:overworld", 0, 64, 0))));
        }
        assertEquals(4, selector.select(PET, events).selected().size());
    }

    @Test void repeatedWitnessPromptKeepsSeparateIdsDatesAndInferenceBoundaries() {
        var events = List.of(event(1, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.MEDIUM),
                event(2, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.MEDIUM),
                event(3, MemoryProvenance.VERIFIED_OBSERVATION, DialogueImportance.MEDIUM));
        var at = events.getFirst().occurredAt();
        var pet = new com.silver.aipets.common.domain.Pet(PET, new UUID(0, 502), "Mica",
                com.silver.aipets.common.domain.PetAppearance.create(
                        com.silver.aipets.common.domain.PetSpecies.CAT, "minecraft:tabby", 0.7, null,
                        com.silver.aipets.common.domain.AppearanceRules.defaults()),
                com.silver.aipets.common.domain.PetTraits.initial(72, 38, 33, 42, 76, at),
                com.silver.aipets.common.domain.PetMood.initial(52, 12, 12, 22, at),
                com.silver.aipets.common.domain.HeldPlacement.INSTANCE, 0, at, at);
        var prompt = new ConsolidationPromptBuilder(ConsolidationConfig.defaults(),
                new com.silver.aipets.service.dialogue.JTokkitDialogueTokenCounter())
                .build(pet, selector.select(PET, events)).prompt().text();
        for (var event : events) {
            assertTrue(prompt.contains("event_id=" + event.eventId()));
            assertTrue(prompt.contains("occurred_at=" + event.occurredAt()));
        }
        assertTrue(prompt.contains("separate occurrences"));
        assertTrue(prompt.contains("not evidence of liking, trust, temperament or the same physical place"));
        assertTrue(prompt.contains("Never combine witnessed events with owner reports"));
    }

    private static ConsolidationEvent event(int id, MemoryProvenance provenance,
            DialogueImportance importance) {
        return new ConsolidationEvent(new UUID(0, id), PET, importance,
                "We crossed a spruce bridge in rain", "ARRIVED_TOGETHER",
                Instant.parse("2026-10-01T00:00:00Z").plusSeconds(id * 86400L),
                provenance, "survival", "minecraft:overworld");
    }
}
