package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueTokenCounter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Rejects oversized, cross-pet, uncited, or factually ungrounded memory proposals. */
public final class ConsolidationOutputValidator {
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "that", "this", "with", "from", "their", "they", "was",
            "were", "have", "has", "had", "for", "but", "not", "you", "your", "pet",
            "owner", "about", "into", "after", "before", "when", "while", "then");

    private final ConsolidationConfig config;
    private final DialogueTokenCounter tokens;

    public ConsolidationOutputValidator(
            ConsolidationConfig config, DialogueTokenCounter tokens) {
        this.config = Objects.requireNonNull(config, "config");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    public ValidatedConsolidationOutput validate(
            ConsolidationModelOutput output, CandidateSelection selection) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(selection, "selection");
        if (output.memoryCards().size() > config.maximumCards()) {
            throw invalid("Too many long-term memory cards");
        }
        String relationship = normalize(output.relationshipSummary());
        if (relationship.length() > 2_000
                || tokens.count(relationship) > config.maximumRelationshipTokens()) {
            throw invalid("Relationship summary exceeds its token cap");
        }
        Map<UUID, ConsolidationEvent> events = selection.selected().stream()
                .collect(Collectors.toUnmodifiableMap(ConsolidationEvent::eventId, Function.identity()));
        for (ConsolidationCardProposal card : output.memoryCards()) {
            String text = normalize(card.text());
            if (text.length() > 2_000 || tokens.count(text) > config.maximumCardTokens()) {
                throw invalid("Memory card exceeds its token cap");
            }
            Set<String> sourceTerms = new HashSet<>();
            for (UUID sourceId : card.sourceEventIds()) {
                ConsolidationEvent source = events.get(sourceId);
                if (source == null) {
                    throw invalid("Memory card cites an event outside the selected pet/day set");
                }
                sourceTerms.addAll(meaningfulTerms(source.summary()));
            }
            Set<String> cardTerms = meaningfulTerms(text);
            if (sourceTerms.isEmpty() || java.util.Collections.disjoint(sourceTerms, cardTerms)) {
                throw invalid("Memory card is not factually grounded by its cited events");
            }
            validateTags(card.emotionTags());
            validateTags(card.entityTags());
            validateTags(card.locationTags());
        }
        return new ValidatedConsolidationOutput(
                output.memoryCards(), relationship, output.traitDeltas());
    }

    private static void validateTags(Set<String> tags) {
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized.length() > 128) {
                throw invalid("Memory tag exceeds its length cap");
            }
        }
    }

    private static Set<String> meaningfulTerms(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 3 && !STOP_WORDS.contains(term))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw invalid("Consolidation text cannot be blank");
        }
        return normalized;
    }

    private static ConsolidationOutputException invalid(String message) {
        return new ConsolidationOutputException(message);
    }
}
