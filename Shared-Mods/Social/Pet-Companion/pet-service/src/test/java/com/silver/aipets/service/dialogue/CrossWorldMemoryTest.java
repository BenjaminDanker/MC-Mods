package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.InMemoryLongTermMemoryStore;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.memory.MemoryProvenance;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class CrossWorldMemoryTest {
    @Test void irrelevantHighRankedCardIsNotInjectedIntoGreeting() {
        UUID pet = UUID.randomUUID();
        InMemoryLongTermMemoryStore sql = new InMemoryLongTermMemoryStore();
        sql.put(new LongTermMemoryCard(UUID.randomUUID(), pet, 1,
                "Owner built a stone bridge", MemoryImportance.HIGH,
                Set.of(), Set.of(), Set.of("vanilla"), true, Instant.now(),
                MemoryProvenance.OWNER_STATEMENT));
        var recall = new RelationalDialogueMemoryRetriever(sql);
        assertTrue(recall.retrieve(pet, "Hi", "vanilla", "minecraft:overworld", 3)
                .isEmpty());
        assertEquals(1, recall.retrieve(pet, "How is the bridge?", "vanilla",
                "minecraft:overworld", 3).size());
    }

    @Test void oldNetherTripOutranksRecentIrrelevantCardWhileOwnerIsInOverworld() {
        UUID pet=UUID.randomUUID();
        InMemoryLongTermMemoryStore sql=new InMemoryLongTermMemoryStore();
        LongTermMemoryCard nether=new LongTermMemoryCard(UUID.randomUUID(),pet,1,
                "Owner and pet traveled through the nether fortress",
                MemoryImportance.HIGH,Set.of(),Set.of(),Set.of("minecraft:the_nether"),
                true,Instant.parse("2026-08-01T00:00:00Z"),
                MemoryProvenance.VERIFIED_OBSERVATION);
        LongTermMemoryCard recent=new LongTermMemoryCard(UUID.randomUUID(),pet,1,
                "Owner fed the pet in the overworld",MemoryImportance.MEDIUM,
                Set.of(),Set.of(),Set.of("minecraft:overworld"),true,
                Instant.parse("2026-09-25T00:00:00Z"),MemoryProvenance.VERIFIED_OBSERVATION);
        sql.put(nether); sql.put(recent);
        var results=new RelationalDialogueMemoryRetriever(sql).retrieve(
                pet,"Tell me about the nether fortress trip","survival",
                "minecraft:overworld",2);
        assertEquals(nether,results.getFirst().card());
        assertEquals(MemoryProvenance.VERIFIED_OBSERVATION,
                results.getFirst().card().provenance());
    }

    @Test void namedServerAndBiomeBeatCurrentLocationWithoutInventingTravel() {
        UUID pet = UUID.randomUUID();
        InMemoryLongTermMemoryStore sql = new InMemoryLongTermMemoryStore();
        LongTermMemoryCard sky = new LongTermMemoryCard(UUID.randomUUID(), pet, 1,
                "Owner and pet visited a floating island", MemoryImportance.HIGH,
                Set.of(), Set.of(), Set.of("sky-island", "minecraft:overworld",
                        "minecraft:meadow"), true, Instant.parse("2026-08-01T00:00:00Z"),
                MemoryProvenance.VERIFIED_OBSERVATION);
        LongTermMemoryCard ocean = new LongTermMemoryCard(UUID.randomUUID(), pet, 1,
                "Owner and pet swam near shore", MemoryImportance.HIGH,
                Set.of(), Set.of(), Set.of("ocean", "minecraft:overworld",
                        "minecraft:beach"), true, Instant.parse("2026-09-25T00:00:00Z"),
                MemoryProvenance.VERIFIED_OBSERVATION);
        sql.put(sky); sql.put(ocean);
        var recall = new RelationalDialogueMemoryRetriever(sql);
        assertEquals(sky, recall.retrieve(pet, "Sky Islands world trip", "ocean",
                "minecraft:overworld", "minecraft:beach", 2).getFirst().card());
        assertEquals(ocean, recall.retrieve(pet, "What happened at the beach biome?",
                "sky-island", "minecraft:overworld", "minecraft:meadow", 2)
                .getFirst().card());
    }
}
