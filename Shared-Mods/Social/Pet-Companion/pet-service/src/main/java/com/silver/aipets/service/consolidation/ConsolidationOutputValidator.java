package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueTokenCounter;

import java.util.Arrays;
import java.util.List;
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
            "were", "have", "has", "had", "for", "but", "you", "your", "pet",
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
        // A grounded episode need not imply a durable relationship change.
        String relationship = output.relationshipSummary().isBlank()
                ? "" : normalize(output.relationshipSummary());
        if (relationship.length() > 2_000
                || tokens.count(relationship) > config.maximumRelationshipTokens()) {
            throw invalid("Relationship summary exceeds its token cap");
        }
        Map<UUID, ConsolidationEvent> events = selection.selected().stream()
                .collect(Collectors.toUnmodifiableMap(ConsolidationEvent::eventId, Function.identity()));
        List<ConsolidationCardProposal> groundedCards = new java.util.ArrayList<>();
        for (ConsolidationCardProposal card : output.memoryCards()) {
            try {
            String text = normalize(card.text());
            if (text.length() > 2_000 || tokens.count(text) > config.maximumCardTokens()) {
                throw invalid("Memory card exceeds its token cap");
            }
            List<List<String>> sourceSpans = new java.util.ArrayList<>();
            for (UUID sourceId : card.sourceEventIds()) {
                ConsolidationEvent source = events.get(sourceId);
                if (source == null) {
                    throw invalid("Memory card cites an event outside the selected pet/day set");
                }
                sourceSpans.add(meaningfulTokens(source.summary()));
            }
            if (card.sourceEventIds().size() > 1) {
                long provenanceKinds = card.sourceEventIds().stream()
                        .map(id -> events.get(id).provenance()).distinct().count();
                if (provenanceKinds != 1) {
                    throw invalid("A pattern cannot merge witnessed and owner-reported sources");
                }
                if (events.get(card.sourceEventIds().getFirst()).provenance()
                        == com.silver.aipets.service.memory.MemoryProvenance.OWNER_STATEMENT
                        && !text.toLowerCase(Locale.ROOT).matches(".*\\b(said|told)\\b.*")) {
                    throw invalid("An owner-reported pattern must remain attributed");
                }
            }
            List<String> cardTerms = meaningfulTokens(text);
            boolean frequencyClaim = text.toLowerCase(Locale.ROOT)
                    .matches(".*\\b(often|repeatedly|regularly)\\b.*");
            if (frequencyClaim && sourceSpans.size() < 2) {
                throw invalid("A recurring pattern needs multiple cited events");
            }
            // Preserve the order and adjacency of factual words, including negation.
            // Bag-of-words overlap can invert a preference or attach a denied object.
            if (cardTerms.size() < 2 || sourceSpans.stream()
                    .noneMatch(source -> containsSpan(source, cardTerms))
                    && !multiEventGrounded(text, sourceSpans)) {
                throw invalid("Memory card is not factually grounded by its cited events");
            }
            validateTags(card.emotionTags());
            validateTags(card.entityTags());
            validateTags(card.locationTags());
            groundedCards.add(card);
            ConsolidationDiagnostics.note("Grounded consolidation card from "
                    + card.sourceEventIds());
            } catch (ConsolidationOutputException rejectedCard) {
                // Keep a grounded episode when another proposed card is unsupported.
                // If nothing survived, retry the job so an outage/malformed batch is visible.
                ConsolidationDiagnostics.note("Rejected consolidation card from "
                        + card.sourceEventIds() + ": " + rejectedCard.getMessage());
            }
        }
        if (!output.memoryCards().isEmpty() && groundedCards.isEmpty())
            throw invalid("No proposed memory card was factually grounded");
        return new ValidatedConsolidationOutput(
                groundedCards, relationship, output.traitDeltas());
    }

    private static void validateTags(Set<String> tags) {
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized.length() > 128) {
                throw invalid("Memory tag exceeds its length cap");
            }
        }
    }

    private static List<String> meaningfulTokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> (term.length() >= 3 || term.equals("no"))
                        && !STOP_WORDS.contains(term)
                        && !Set.of("often", "repeatedly", "regularly").contains(term))
                .map(term -> term.length() > 4 && term.endsWith("s")
                        ? term.substring(0,term.length()-1) : term)
                .toList();
    }

    private static boolean multiEventGrounded(String text, List<List<String>> sources) {
        if (sources.size() < 2 || !text.toLowerCase(Locale.ROOT)
                .matches(".*\\b(often|repeatedly|regularly)\\b.*")) return false;
        String[] clauses = text.split("(?i)\\s+and\\s+|\\s*;\\s*");
        if (clauses.length < 2) return false;
        List<List<String>> parts = Arrays.stream(clauses).map(ConsolidationOutputValidator::meaningfulTokens)
                .toList();
        if (parts.stream().anyMatch(part -> part.size() < 2
                || sources.stream().noneMatch(source -> containsSpan(source, part)))) return false;
        if (sources.stream().anyMatch(source -> parts.stream()
                .noneMatch(part -> containsSpan(source, part)))) return false;
        Set<String> shared = new java.util.HashSet<>(sources.getFirst());
        for (List<String> source : sources.subList(1, sources.size())) shared.retainAll(source);
        shared.removeAll(Set.of("not", "no", "never"));
        if (shared.isEmpty()) return false;
        List<String> claim = meaningfulTokens(text);
        Set<String> evidence = sources.stream().flatMap(List::stream)
                .collect(Collectors.toSet());
        if (!evidence.containsAll(claim)) return false;
        // A negated source cannot silently turn into a positive preference or action.
        boolean sourceNegated = sources.stream().flatMap(List::stream)
                .anyMatch(word -> Set.of("not", "no", "never").contains(word));
        boolean claimNegated = claim.stream()
                .anyMatch(word -> Set.of("not", "no", "never").contains(word));
        return sourceNegated == claimNegated;
    }

    private static boolean containsSpan(List<String> source, List<String> card) {
        for (int start = 0; start + card.size() <= source.size(); start++) {
            if (source.subList(start, start + card.size()).equals(card)) return true;
        }
        return false;
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
