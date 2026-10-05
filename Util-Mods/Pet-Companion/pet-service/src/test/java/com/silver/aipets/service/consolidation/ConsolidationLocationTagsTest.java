package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.memory.MemoryProvenance;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ConsolidationLocationTagsTest {
    @Test void allCitedWitnessedCoordinatesSurviveButOwnerChatPositionAndModelTagsDoNot() {
        UUID pet = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID(), claim = UUID.randomUUID();
        var near = new com.silver.aipets.common.transport.SpatialLocation("survival", "world", "minecraft:overworld", 10, 64, 0);
        var far = new com.silver.aipets.common.transport.SpatialLocation("survival", "other-save", "minecraft:overworld", 4000, 70, 0);
        var e1 = new ConsolidationEvent(first, pet, DialogueImportance.HIGH, "We crossed a spruce bridge",
                "ARRIVED_TOGETHER", Instant.now(), MemoryProvenance.VERIFIED_OBSERVATION,
                "survival", "minecraft:overworld", java.util.Optional.of(near));
        var e2 = new ConsolidationEvent(second, pet, DialogueImportance.HIGH, "We crossed a stone bridge",
                "ARRIVED_TOGETHER", Instant.now(), MemoryProvenance.VERIFIED_OBSERVATION,
                "survival", "minecraft:overworld", java.util.Optional.of(far));
        var owner = new ConsolidationEvent(claim, pet, DialogueImportance.HIGH, "Owner said they built a bridge",
                "DIALOGUE", Instant.now(), MemoryProvenance.OWNER_STATEMENT,
                "survival", "minecraft:overworld", java.util.Optional.of(near));
        var proposal = new ConsolidationCardProposal("We crossed bridges", MemoryImportance.HIGH,
                List.of(first, second), Set.of(), Set.of(), Set.of("invented place"));
        var tags = ConsolidationLocationTags.from(proposal, Map.of(first, e1, second, e2));
        assertEquals(Set.of(near, far), Set.copyOf(com.silver.aipets.service.memory.WitnessedLocations.from(tags)));
        assertFalse(tags.contains("invented place"));
        var claimProposal = new ConsolidationCardProposal(owner.summary(), MemoryImportance.HIGH,
                List.of(claim), Set.of(), Set.of(), Set.of("invented place"));
        assertTrue(ConsolidationLocationTags.from(claimProposal, Map.of(claim, owner)).isEmpty());
    }

    @Test void onlyVerifiedSourceLocationsBecomeMemoryTags() {
        UUID observedId = UUID.randomUUID(), statementId = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        var observed = new ConsolidationEvent(observedId, pet, DialogueImportance.HIGH,
                "Owner and pet explored biome=minecraft:warped_forest",
                "NEW_AREA", Instant.now(), MemoryProvenance.VERIFIED_OBSERVATION,
                "sky-island", "minecraft:the_nether");
        var claim = new ConsolidationEvent(statementId, pet, DialogueImportance.HIGH,
                "Owner said we visited a desert", "DIALOGUE", Instant.now(),
                MemoryProvenance.OWNER_STATEMENT, "vanilla", "minecraft:overworld");
        var proposal = new ConsolidationCardProposal("Owner and pet explored",
                MemoryImportance.HIGH, List.of(observedId, statementId), Set.of(), Set.of(),
                Set.of("minecraft:the_end"));
        assertEquals(Set.of("sky-island", "minecraft:the_nether",
                "minecraft:warped_forest"), ConsolidationLocationTags.from(proposal,
                        Map.of(observedId, observed, statementId, claim)));
    }
}
