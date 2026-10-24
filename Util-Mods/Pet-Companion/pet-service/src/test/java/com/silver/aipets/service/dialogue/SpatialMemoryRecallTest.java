package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.*;
import com.silver.aipets.common.transport.*;
import com.silver.aipets.service.memory.*;
import com.silver.aipets.service.vector.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SpatialMemoryRecallTest {
    private static final UUID PET = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final SpatialLocation HERE = place("world", 0);

    @Test void nearBoostReordersComparableCardsButKeepsFarRelevantRecall() {
        var sql = new InMemoryLongTermMemoryStore();
        var far = card("We crossed a spruce bridge in rain", place("world", 4000));
        var near = card("We crossed a stone bridge at dawn", place("world", 8));
        sql.put(far); sql.put(near);
        var results = new RelationalDialogueMemoryRetriever(sql).retrieveSpatial(PET,
                "Remember our bridge crossing", "survival", "minecraft:overworld", "", 3,
                Optional.of(HERE), NOW, false);
        assertEquals(List.of(near, far), results.stream().map(LongTermMemorySnippet::card).toList());
        assertTrue(results.getFirst().rankingScore() - results.getLast().rankingScore() <= .05);
        assertEquals(List.of(far), new RelationalDialogueMemoryRetriever(sql).retrieveSpatial(PET,
                "spruce rain", "survival", "minecraft:overworld", "", 1,
                Optional.of(HERE), NOW, false).stream().map(LongTermMemorySnippet::card).toList());
    }

    @Test void nearbyIrrelevantMemoryIsExcludedInVectorAndRelationalPaths() {
        var sql = new InMemoryLongTermMemoryStore();
        var far = card("We crossed a spruce bridge in rain", place("world", 4000));
        var irrelevant = card("Owner fed the pet a salmon", HERE);
        sql.put(far); sql.put(irrelevant);
        var vectors = new InMemoryVectorMemoryRepository();
        // A plausible but below strong-semantic threshold irrelevant vector candidate.
        vectors.upsert(new VectorMemoryDocument(far, new EmbeddingVector("test", new float[]{1, 0})));
        vectors.upsert(new VectorMemoryDocument(irrelevant, new EmbeddingVector("test", new float[]{.6f, .8f})));
        EmbeddingModelClient model = new EmbeddingModelClient() {
            public String model() { return "test"; }
            public EmbeddingVector embed(String text) { return new EmbeddingVector("test", new float[]{1, 0}); }
        };
        var recall = new VectorDialogueMemoryRetriever(new MemoryRetrievalService(sql, vectors,
                model, Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC)), new RelationalDialogueMemoryRetriever(sql));
        for (boolean available : List.of(true, false)) {
            vectors.setAvailable(available);
            var results = recall.retrieveSpatial(PET, "Remember our spruce bridge crossing", "survival",
                    "minecraft:overworld", "", 3, Optional.of(HERE), NOW, false);
            assertEquals(List.of(far), results.stream().map(LongTermMemorySnippet::card).toList());
            assertEquals(available ? LongTermMemorySnippet.Source.VECTOR : LongTermMemorySnippet.Source.RELATIONAL,
                    results.getFirst().source());
        }
    }

    @Test void boostRequiresSameServerWorldAndDimensionAndIsBounded() {
        assertEquals(.05, WitnessedLocations.boost(Optional.of(HERE), List.of(HERE)), 1e-12);
        assertEquals(.025, WitnessedLocations.boost(Optional.of(HERE), List.of(place("world", 64))), 1e-12);
        assertEquals(.025, WitnessedLocations.boost(Optional.of(HERE), List.of(
                new SpatialLocation("survival", "world", "minecraft:overworld", 0, 128, 0))), 1e-12);
        assertEquals(0, WitnessedLocations.boost(Optional.of(HERE), List.of(place("world", 128))));
        assertEquals(0, WitnessedLocations.boost(Optional.of(HERE), List.of(place("other-save", 0))));
        assertEquals(0, WitnessedLocations.boost(Optional.of(HERE), List.of(
                new SpatialLocation("other-server", "world", "minecraft:overworld", 0, 64, 0))));
        assertEquals(0, WitnessedLocations.boost(Optional.of(HERE), List.of(
                new SpatialLocation("survival", "world", "minecraft:the_nether", 0, 64, 0))));
        assertEquals(0, WitnessedLocations.boost(Optional.empty(), List.of(HERE)));
        assertEquals(0, WitnessedLocations.boost(Optional.of(HERE), List.of()));
        assertEquals(.05, WitnessedLocations.boost(Optional.of(HERE), List.of(HERE, HERE)));
    }

    @Test void savedWorldIdentitySurvivesMetadataWithoutWhitespaceNormalization() {
        var location = place("my  save", 10);
        assertEquals(List.of(location), WitnessedLocations.from(card("A spruce bridge", location).locationTags()));
    }

    @Test void coordinateMetadataCannotCreateLexicalRelevance() {
        var unrelated = card("Owner fed the pet a salmon", HERE);
        assertFalse(MemoryRanking.relevant("world survival 64", unrelated, .5));
        assertFalse(MemoryRanking.relevant("spruce bridge", unrelated, .5));
    }

    @Test void promptPreservesSpatialOrderAndShortTermRelevanceGate() {
        var near = card("We crossed a stone bridge at dawn", place("world", 8));
        var far = card("We crossed a spruce bridge in rain", place("world", 4000));
        var sql = new InMemoryLongTermMemoryStore(); sql.put(near); sql.put(far);
        var ranked = new RelationalDialogueMemoryRetriever(sql).retrieveSpatial(PET,
                "Our bridge crossing?", "survival", "minecraft:overworld", "", 3,
                Optional.of(HERE), NOW, false);
        var scene = new PetSceneSnapshot("unknown", "unknown", "UNKNOWN", false,
                "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false, Optional.of(HERE));
        var context = context(scene, List.of(), ranked);
        var builder = new DialoguePromptBuilder(DialogueLimits.defaultsWithoutReplyCap(), new JTokkitDialogueTokenCounter());
        String prompt = builder.build(context, "Our bridge crossing?").text();
        assertTrue(prompt.contains(near.text()));
        assertFalse(prompt.contains(far.text()), "Ordinary recall remains limited to one episode");
        var irrelevant = new ShortTermMemorySnippet(UUID.randomUUID(), DialogueImportance.HIGH,
                "Owner fed the pet a salmon", NOW, Optional.empty(), .9, "VERIFIED_OBSERVATION", Optional.of(HERE));
        var farEvent = new ShortTermMemorySnippet(UUID.randomUUID(), DialogueImportance.HIGH,
                far.text(), NOW, Optional.empty(), .9, "VERIFIED_OBSERVATION", Optional.of(place("world", 4000)));
        prompt = builder.build(context(scene, List.of(irrelevant, farEvent), List.of()), "spruce bridge rain").text();
        assertTrue(prompt.contains(far.text()));
        assertFalse(prompt.contains(irrelevant.summary()));
        // Coordinates steer ranking only; they never reach the pet's voice.
        assertFalse(prompt.contains("4000.0"));
    }

    @Test void ownerReportedShortTermLocationRemainsUnknownEvenIfCallerSuppliesChatCoordinates() {
        var report = new ShortTermMemorySnippet(UUID.randomUUID(), DialogueImportance.HIGH,
                "Owner said they built a bridge", NOW, Optional.empty(), .9, "OWNER_STATEMENT", Optional.of(HERE));
        assertTrue(report.location().isEmpty());
    }

    @Test void benchmarkBoundedRerankingAgainstSameCardsWithoutProximity() {
        List<LongTermMemoryCard> cards = new ArrayList<>();
        for (int i = 0; i < 12; i++) cards.add(card("We crossed a spruce bridge in rain", place("world", i * 32)));
        long[] baseline = new long[500], spatial = new long[500];
        double checksum = 0;
        for (int i = -200; i < baseline.length; i++) {
            // Alternate order to reduce JIT/order bias; no provider, SQL or vector I/O here.
            for (int pass = 0; pass < 2; pass++) {
                boolean withProximity = (i + pass) % 2 == 0;
                long start = System.nanoTime();
                for (var card : cards) checksum += withProximity
                        ? MemoryRanking.score("Remember our spruce bridge crossing", card, .65,
                                "survival", "minecraft:overworld", "", NOW, Optional.of(HERE))
                        : MemoryRanking.score("Remember our spruce bridge crossing", card, .65,
                                "survival", "minecraft:overworld", "", NOW);
                long elapsed = System.nanoTime() - start;
                if (i >= 0) (withProximity ? spatial : baseline)[i] = elapsed;
            }
        }
        Arrays.sort(baseline); Arrays.sort(spatial);
        assertTrue(checksum > 0);
        System.out.printf(Locale.ROOT,
                "Spatial rerank 12 cards, 500 paired warm batches: baseline median=%.3f ms p95=%.3f ms; spatial median=%.3f ms p95=%.3f ms; median delta=%.3f ms%n",
                baseline[249] / 1e6, baseline[474] / 1e6, spatial[249] / 1e6, spatial[474] / 1e6,
                (spatial[249] - baseline[249]) / 1e6);
    }

    private static SpatialLocation place(String world, double x) {
        return new SpatialLocation("survival", world, "minecraft:overworld", x, 64, 0);
    }

    private static LongTermMemoryCard card(String text, SpatialLocation location) {
        return new LongTermMemoryCard(UUID.randomUUID(), PET, 1, text, MemoryImportance.HIGH,
                Set.of(), Set.of(), Set.of(WitnessedLocations.tag(location)), true, NOW,
                MemoryProvenance.VERIFIED_OBSERVATION, MemoryKind.EPISODE, .9, 4, 1, Optional.empty());
    }

    private static DialogueContext context(PetSceneSnapshot scene, List<ShortTermMemorySnippet> events,
            List<LongTermMemorySnippet> memories) {
        var pet = new Pet(PET, UUID.randomUUID(), "Mica", PetAppearance.create(PetSpecies.CAT,
                "minecraft:tabby", .7, 42L, AppearanceRules.defaults()),
                new PetTraits(50, 50, 50, 50, 50, 40, 45, 40, "", 0, NOW),
                PetMood.initial(80, 40, 20, 10, NOW), PlacedPlacement.materialized(new BackendId("survival"),
                        DimensionId.parse("minecraft:overworld"), new WorldPosition(0, 64, 0), UUID.randomUUID()),
                0, NOW, NOW);
        return new DialogueContext(pet, false, true, events, memories, List.of(),
                new DialogueGameContext("survival", "minecraft:overworld", "PLAYER_MESSAGE", scene), NOW);
    }
}
