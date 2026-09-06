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

/** High-first, distinct-medium, repeated-low deterministic daily selection. */
public final class ConsolidationCandidateSelector {
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
        List<ConsolidationEvent> valid = pending.stream()
                .filter(event -> event.petId().equals(petId))
                .filter(event -> !isNoise(event))
                .toList();
        Map<String, Integer> lowFrequency = new HashMap<>();
        valid.stream().filter(event -> event.importance() == DialogueImportance.LOW)
                .forEach(event -> lowFrequency.merge(theme(event.summary()), 1, Integer::sum));

        Comparator<ConsolidationEvent> order = Comparator
                .comparingInt((ConsolidationEvent event) -> rank(event.importance())).reversed()
                .thenComparing(ConsolidationEvent::occurredAt, Comparator.reverseOrder())
                .thenComparing(event -> event.eventId().toString());
        List<ConsolidationEvent> candidates = valid.stream()
                .filter(event -> event.importance() != DialogueImportance.LOW
                        || lowFrequency.getOrDefault(theme(event.summary()), 0) >= 2)
                .sorted(order)
                .toList();
        List<ConsolidationEvent> selected = new ArrayList<>();
        Set<String> mediumThemes = new HashSet<>();
        Set<String> lowThemes = new HashSet<>();
        int usedTokens = 0;
        for (ConsolidationEvent candidate : candidates) {
            String theme = theme(candidate.summary());
            if (candidate.importance() == DialogueImportance.MEDIUM && !mediumThemes.add(theme)) {
                continue;
            }
            if (candidate.importance() == DialogueImportance.LOW && !lowThemes.add(theme)) {
                continue;
            }
            int eventTokens = tokens.count(candidate.summary());
            if (selected.size() >= config.maximumEvents()
                    || usedTokens + eventTokens > config.maximumInputTokens()) {
                continue;
            }
            selected.add(candidate);
            usedTokens += eventTokens;
        }
        Set<UUID> selectedIds = selected.stream().map(ConsolidationEvent::eventId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<UUID> discarded = pending.stream().map(ConsolidationEvent::eventId)
                .filter(id -> !selectedIds.contains(id)).toList();
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

    private static int rank(DialogueImportance importance) {
        return switch (importance) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}
