package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.dialogue.DialogueTokenCounter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** High-first bounded selection retaining distinct witnesses for grounded patterns. */
public final class ConsolidationCandidateSelector {
    private static final int MAXIMUM_WITNESSES_PER_THEME = 3;
    private final ConsolidationConfig config;
    private final DialogueTokenCounter tokens;

    public ConsolidationCandidateSelector(
            ConsolidationConfig config, DialogueTokenCounter tokens) {
        this.config = Objects.requireNonNull(config, "config");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    public CandidateSelection select(UUID petId, List<ConsolidationEvent> pending) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(pending, "pending");
        Comparator<ConsolidationEvent> order = Comparator
                .comparingInt((ConsolidationEvent event) -> rank(event.importance())).reversed()
                .thenComparing(ConsolidationEvent::occurredAt, Comparator.reverseOrder())
                .thenComparing(event -> event.eventId().toString());
        Set<UUID> seen = new HashSet<>();
        List<ConsolidationEvent> valid = pending.stream()
                .filter(event -> event.petId().equals(petId))
                .filter(event -> !isNoise(event))
                .sorted(order)
                .filter(event -> seen.add(event.eventId()))
                .toList();
        Map<EvidenceTheme, Integer> lowFrequency = new HashMap<>();
        valid.stream().filter(event -> event.importance() == DialogueImportance.LOW)
                .forEach(event -> lowFrequency.merge(theme(event), 1, Integer::sum));

        List<ConsolidationEvent> candidates = valid.stream()
                .filter(event -> event.importance() != DialogueImportance.LOW
                        || lowFrequency.getOrDefault(theme(event), 0) >= 2)
                .toList();
        List<ConsolidationEvent> selected = new ArrayList<>();
        Map<EvidenceTheme, Integer> mediumThemes = new HashMap<>();
        Map<EvidenceTheme, Integer> lowThemes = new HashMap<>();
        int usedTokens = 0;
        for (ConsolidationEvent candidate : candidates) {
            EvidenceTheme theme = theme(candidate);
            Map<EvidenceTheme, Integer> counts = candidate.importance() == DialogueImportance.MEDIUM
                    ? mediumThemes : lowThemes;
            int maximum = candidate.provenance()
                    == com.silver.aipets.service.memory.MemoryProvenance.VERIFIED_OBSERVATION
                    ? MAXIMUM_WITNESSES_PER_THEME : 1;
            if (candidate.importance() != DialogueImportance.HIGH
                    && counts.getOrDefault(theme, 0) >= maximum) continue;
            int eventTokens = tokens.count(candidate.summary());
            if (selected.size() >= config.maximumEvents()
                    || usedTokens + eventTokens > config.maximumInputTokens()) {
                continue;
            }
            selected.add(candidate);
            usedTokens += eventTokens;
            if (candidate.importance() != DialogueImportance.HIGH) counts.merge(theme, 1, Integer::sum);
        }
        Set<UUID> selectedIds = selected.stream().map(ConsolidationEvent::eventId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<UUID> discarded = pending.stream().map(ConsolidationEvent::eventId)
                .filter(id -> !selectedIds.contains(id)).distinct().toList();
        return new CandidateSelection(selected, discarded, usedTokens);
    }

    private static boolean isNoise(ConsolidationEvent event) {
        String type = event.eventType().toUpperCase(Locale.ROOT);
        String text = event.summary().toLowerCase(Locale.ROOT);
        return type.equals("SYSTEM") || type.equals("RETRY")
                || text.startsWith("request failed") || text.startsWith("duplicate retry");
    }

    private static String theme(String summary) {
        return summary.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\b\\d+\\b", "#")
                .replaceAll("\\s+", " ").strip();
    }

    private static EvidenceTheme theme(ConsolidationEvent event) {
        return new EvidenceTheme(theme(event.summary()), event.provenance(), event.eventType(),
                event.sourceServer(), event.sourceDimension(),
                event.location().map(com.silver.aipets.common.transport.SpatialLocation::world).orElse(""));
    }

    private record EvidenceTheme(String summary,
            com.silver.aipets.service.memory.MemoryProvenance provenance,
            String eventType, String server, String dimension, String world) { }

    private static int rank(DialogueImportance importance) {
        return switch (importance) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}
