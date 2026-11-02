package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded deterministic ranking. Semantic 55%, lexical 20%, recency 10%,
 * importance 10%, current location 5%, plus at most .05 witnessed proximity. Relevance is gated first;
 * neither location signal can exclude distant/cross-world memories. */
final class MemoryRanking {
    private static final Set<String> STOP = Set.of("the", "and", "you", "your", "pet",
            "what", "when", "where", "did", "was", "were", "have", "about", "remember",
            "our", "that", "this", "with", "from", "for", "are", "we", "i",
            "world", "server", "dimension", "biome");
    private static final Set<String> LOCATION_WORDS = Set.of("vanilla", "ocean",
            "sky", "island", "desert", "cave", "magic", "nether", "end",
            "overworld");

    static double lexical(String question, LongTermMemoryCard card) {
        Set<String> terms = terms(question);
        if (terms.isEmpty()) return 0;
        Set<String> text = terms(card.text() + " " + String.join(" ", com.silver.aipets.service.memory.WitnessedLocations.ordinaryTags(card.locationTags()))
                + " " + String.join(" ", card.entityTags()));
        long common = terms.stream().filter(text::contains).count();
        return (double) common / terms.size();
    }

    /** A high ranking score alone is not evidence that a card answers this turn. */
    static boolean relevant(String question, LongTermMemoryCard card, double semantic) {
        return lexical(question, card) > 0 || semantic >= .72;
    }

    static double score(String question, LongTermMemoryCard card, double semantic,
            String dimension, Instant now) {
        return score(question, card, semantic, "", dimension, "", now);
    }

    static double score(String question, LongTermMemoryCard card, double semantic,
            String backend, String dimension, String biome, Instant now) {
        double age = card.createdAt().equals(Instant.EPOCH) ? 0.0
                : 1.0 / (1.0 + Math.max(0, ChronoUnit.DAYS.between(card.createdAt(), now)) / 30.0);
        Set<String> queryTerms = terms(question);
        Set<String> locationTerms = terms(String.join(" ", com.silver.aipets.service.memory.WitnessedLocations.ordinaryTags(card.locationTags())));
        boolean explicitPlace = queryTerms.stream().anyMatch(LOCATION_WORDS::contains)
                || queryTerms.stream().anyMatch(locationTerms::contains);
        double context = explicitPlace
                ? (queryTerms.stream().anyMatch(locationTerms::contains) ? 1 : 0)
                : .5 * (card.locationTags().contains(backend) ? 1 : 0)
                        + .3 * (card.locationTags().contains(dimension) ? 1 : 0)
                        + .2 * (card.locationTags().contains(biome) ? 1 : 0);
        return .55 * Math.max(0, semantic) + .20 * lexical(question, card)
                + .10 * age + .10 * (card.importance() == MemoryImportance.HIGH ? 1 : .5)
                + .05 * context;
    }

    static double score(String question, LongTermMemoryCard card, double semantic,
            String backend, String dimension, String biome, Instant now,
            java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> current) {
        double base = score(question, card, semantic, backend, dimension, biome, now);
        if (current.isEmpty()) return base;
        return base + com.silver.aipets.service.memory.WitnessedLocations.boost(current,
                        com.silver.aipets.service.memory.WitnessedLocations.from(card.locationTags()));
    }

    private static Set<String> terms(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .map(word -> word.length() > 4 && word.endsWith("s")
                        ? word.substring(0, word.length() - 1) : word)
                .filter(word -> word.length() >= 3 && !STOP.contains(word))
                .collect(Collectors.toSet());
    }
}
