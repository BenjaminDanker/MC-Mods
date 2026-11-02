package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.DialogueContextUsageWire;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, token-budgeted assembly of the two prompts in a dialogue turn: the pet's
 * voice (a plain-language character card plus the live conversation as real chat turns) and the
 * parallel memory bookkeeping prompt. Provider truncation is never relied upon.
 */
public final class DialoguePromptBuilder {
    /** Turns newer than this are sent as live chat messages; older ones are summarized as "last time". */
    static final Duration LIVE_CONVERSATION_WINDOW = Duration.ofMinutes(20);
    /** Relationship evidence this recent is always part of the pet's feelings, cue or not. */
    static final Duration FRESH_RELATIONSHIP_WINDOW = Duration.ofHours(12);

    private final DialogueLimits limits;
    private final DialogueTokenCounter tokens;

    public DialoguePromptBuilder(DialogueLimits limits, DialogueTokenCounter tokens) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    /** The pet's voice prompt. */
    public DialoguePrompt build(DialogueContext context, String normalizedMessage) {
        Selection selection = select(context, normalizedMessage);
        RenderedPrompt rendered = renderVoice(context, normalizedMessage, selection);
        while (rendered.tokens() > limits.hardInputTokens()) {
            if (!selection.dropOne()) {
                throw new IllegalArgumentException("Core dialogue prompt exceeds the configured hard token cap: "
                        + rendered.tokens() + " > " + limits.hardInputTokens());
            }
            rendered = renderVoice(context, normalizedMessage, selection);
        }
        return new DialoguePrompt(rendered.text(), rendered.tokens(), rendered.contextUsage(),
                java.util.Optional.of(normalizedMessage), rendered.chatHistory());
    }

    /** The parallel record-keeping prompt over the same selected context, with record IDs. */
    public DialoguePrompt buildMemory(DialogueContext context, String normalizedMessage) {
        Selection selection = select(context, normalizedMessage);
        String text = MemoryBookkeepingPrompt.system(context, selection.shortTerm, selection.longTerm,
                selection.turns, selection.threads);
        while (tokens.count(text) + tokens.count(normalizedMessage) > limits.hardInputTokens()) {
            if (!selection.dropOne()) {
                throw new IllegalArgumentException("Memory bookkeeping prompt exceeds the hard token cap");
            }
            text = MemoryBookkeepingPrompt.system(context, selection.shortTerm, selection.longTerm,
                    selection.turns, selection.threads);
        }
        int inputTokens = tokens.count(text) + tokens.count(normalizedMessage);
        return new DialoguePrompt(text, inputTokens, DialogueContextUsageWire.empty(),
                java.util.Optional.of(normalizedMessage));
    }

    private Selection select(DialogueContext context, String message) {
        boolean recap = explicitRecap(message);
        List<ShortTermMemorySnippet> shortTerm = selectShortTerm(context, message, recap);
        List<LongTermMemorySnippet> longTerm = selectLongTerm(context, message, recap, shortTerm);
        List<DialogueTurn> turns = selectTurns(context);
        List<RelationshipEvidence> relationship = selectRelationshipEvidence(context, message);
        List<OrganicPetContext.ThreadCue> threads = selectThreads(
                context.organicContext().openThreads(), memoryCue(message, turns));
        return new Selection(new ArrayList<>(shortTerm), new ArrayList<>(longTerm), new ArrayList<>(turns),
                relationship, threads);
    }

    private RenderedPrompt renderVoice(DialogueContext context, String message, Selection selection) {
        java.time.Instant liveSince = context.now().minus(LIVE_CONVERSATION_WINDOW);
        List<DialogueTurn> live = selection.turns.stream()
                .filter(turn -> !turn.occurredAt().isBefore(liveSince)).toList();
        List<DialogueTurn> earlier = selection.turns.stream()
                .filter(turn -> turn.occurredAt().isBefore(liveSince)).toList();
        // A chat transcript must alternate from the owner; drop a leading pet line that lost its prompt.
        List<DialogueTurn> chat = new ArrayList<>(live);
        while (!chat.isEmpty() && chat.getFirst().role() == DialogueTurn.Role.PET) chat.removeFirst();
        boolean interpretation = message.toLowerCase(java.util.Locale.ROOT)
                .matches("(?s).*(\\b(what do you think|did i mean|how did that sound|am i|i meant|sorry)\\b).*");
        String summary = context.pet().traits().relationshipSummary();
        String relationship = summary.isBlank() ? "" : tokens.truncate(summary, limits.relationshipTokens()).strip();
        String scene = PersonaPromptRenderer.sceneFacts(message, context.gameContext());
        String text = PersonaPromptRenderer.system(context, selection.shortTerm, selection.longTerm, earlier,
                selection.relationship, selection.threads, interpretation, relationship, scene);
        int chatTokens = chat.stream().mapToInt(turn -> tokens.count(turn.text()) + 4).sum();
        int inputTokens = tokens.count(text) + chatTokens + tokens.count(message);
        int shortTokens = selection.shortTerm.stream().mapToInt(item -> tokens.count(item.summary()) + 6).sum();
        int relationalTokens = selection.longTerm.stream()
                .filter(item -> item.source() == LongTermMemorySnippet.Source.RELATIONAL)
                .mapToInt(item -> tokens.count(item.card().text()) + 6).sum();
        int vectorTokens = selection.longTerm.stream()
                .filter(item -> item.source() == LongTermMemorySnippet.Source.VECTOR)
                .mapToInt(item -> tokens.count(item.card().text()) + 6).sum();
        int earlierTokens = earlier.stream().mapToInt(turn -> tokens.count(turn.text()) + 3).sum();
        int instructionTokens = tokens.count(PersonaPromptRenderer.FIELD_GUIDE);
        int sceneTokens = tokens.count(scene);
        int ownerTokens = tokens.count(message);
        int systemTokens = Math.max(0, tokens.count(text) - shortTokens - relationalTokens - vectorTokens
                - earlierTokens - instructionTokens - sceneTokens);
        int relationalItems = (int) selection.longTerm.stream()
                .filter(memory -> memory.source() == LongTermMemorySnippet.Source.RELATIONAL).count();
        int vectorItems = selection.longTerm.size() - relationalItems;
        return new RenderedPrompt(text, inputTokens, chat, new DialogueContextUsageWire(
                systemTokens, 0, shortTokens, relationalTokens, vectorTokens, earlierTokens + chatTokens,
                sceneTokens, ownerTokens, instructionTokens, inputTokens, selection.shortTerm.size(),
                relationalItems, vectorItems, selection.turns.size()));
    }

    private List<ShortTermMemorySnippet> selectShortTerm(DialogueContext context, String message, boolean recap) {
        List<ShortTermMemorySnippet> candidates = context.shortTermMemories().stream()
                .filter(memory -> memory.eligibleAt(context.now()))
                .filter(memory -> recap || cueOverlap(message, memory.summary()) > 0)
                .sorted(Comparator
                        .comparingDouble((ShortTermMemorySnippet memory) ->
                                .20 * cueOverlap(message, memory.summary())
                                + .10 * importanceRank(memory.importance()) / 3.0
                                + .10 * memory.relevance()
                                + .10 / (1 + Math.max(0, Duration.between(memory.occurredAt(), context.now()).toSeconds()) / 86400.0)
                                + com.silver.aipets.service.memory.WitnessedLocations.boost(
                                        context.gameContext().scene().location(), memory.location().stream().toList())).reversed()
                        .thenComparing(Comparator.comparingInt((ShortTermMemorySnippet memory) ->
                                importanceRank(memory.importance())).reversed())
                        .thenComparing(Comparator.comparingDouble(
                                ShortTermMemorySnippet::relevance).reversed())
                        .thenComparing(ShortTermMemorySnippet::occurredAt,
                                Comparator.reverseOrder()))
                .toList();
        if (!recap) candidates = candidates.subList(0,
                Math.min(maximumEpisodeRecall(message, candidates.size()), candidates.size()));
        List<ShortTermMemorySnippet> selected = new ArrayList<>();
        for (ShortTermMemorySnippet candidate : candidates) {
            selected.add(candidate);
            if (selected.stream().mapToInt(item -> tokens.count(item.summary()) + 6).sum() > limits.shortTermTokens()) {
                selected.removeLast();
            }
        }
        selected.sort(Comparator.comparing(ShortTermMemorySnippet::occurredAt));
        return selected;
    }

    private List<LongTermMemorySnippet> selectLongTerm(DialogueContext context, String message,
            boolean recap, List<ShortTermMemorySnippet> shortTerm) {
        List<LongTermMemorySnippet> selected = new ArrayList<>();
        boolean alreadyHaveEpisode = shortTerm.stream().anyMatch(item -> item.provenance().equals("VERIFIED_OBSERVATION"));
        List<LongTermMemorySnippet> candidates = context.longTermMemories().stream()
                .filter(memory -> memory.card().kind() != com.silver.aipets.service.memory.MemoryKind.EPISODE
                        || recap || !alreadyHaveEpisode)
                .filter(memory -> recap || OwnerInterpretationMapper.isOwnerCorrection(message)
                        || memory.source() == LongTermMemorySnippet.Source.VECTOR
                        || cueOverlap(message, memory.card().text()) > 0)
                .sorted(Comparator.comparingDouble(LongTermMemorySnippet::rankingScore).reversed())
                .toList();
        long relevantEpisodes = candidates.stream().filter(memory ->
                memory.card().kind() == com.silver.aipets.service.memory.MemoryKind.EPISODE).count();
        int maximumEpisodes = recap ? Integer.MAX_VALUE
                : broadRecall(message) && relevantEpisodes > 1 ? 2 : alreadyHaveEpisode ? 0 : 1;
        int episodeCount = 0;
        for (LongTermMemorySnippet candidate : candidates) {
            if (selected.size() >= 3) break;
            if (candidate.card().kind() == com.silver.aipets.service.memory.MemoryKind.EPISODE
                    && episodeCount >= maximumEpisodes) continue;
            selected.add(candidate);
            if (candidate.card().kind() == com.silver.aipets.service.memory.MemoryKind.EPISODE) episodeCount++;
            if (selected.stream().mapToInt(item -> tokens.count(item.card().text()) + 6).sum() > limits.longTermTokens()) {
                selected.removeLast();
                if (candidate.card().kind() == com.silver.aipets.service.memory.MemoryKind.EPISODE) episodeCount--;
            }
        }
        return selected;
    }

    private static int maximumEpisodeRecall(String message, int count) {
        return count > 1 && broadRecall(message) ? 2 : 1;
    }

    private static boolean broadRecall(String message) {
        return message.toLowerCase(java.util.Locale.ROOT)
                .matches("(?s).*(\\b(what happened|what did we do|what did we see|remember|recap|timeline|tell me about)\\b).*");
    }

    private static boolean explicitRecap(String message) {
        return message.toLowerCase(java.util.Locale.ROOT)
                .matches("(?s).*(\\b(recap|timeline|everything that happened|complete recap|all the events)\\b).*");
    }

    private static int cueOverlap(String message, String memory) {
        java.util.Set<String> cues = cueTerms(message);
        if (cues.isEmpty()) return 0;
        java.util.Set<String> candidates = cueTerms(memory);
        return (int) cues.stream().filter(candidates::contains).count();
    }

    private static java.util.Set<String> cueTerms(String text) {
        java.util.Set<String> stop = java.util.Set.of("what", "when", "where", "which", "that", "this",
                "did", "does", "with", "from", "near", "about", "have", "were", "was", "are", "the",
                "and", "you", "your", "our", "we", "for", "tell", "remember", "happened", "event");
        return java.util.Arrays.stream(text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 3 && !stop.contains(word))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<DialogueTurn> selectTurns(DialogueContext context) {
        List<DialogueTurn> ordered = context.recentTurns().stream()
                .sorted(Comparator.comparing(DialogueTurn::occurredAt))
                .toList();
        int start = Math.max(0, ordered.size() - limits.maximumRecentTurns());
        List<DialogueTurn> selected = new ArrayList<>(ordered.subList(start, ordered.size()));
        while (!selected.isEmpty()
                && selected.stream().mapToInt(turn -> tokens.count(turn.text()) + 4).sum() > limits.recentTurnTokens()) {
            selected.removeFirst();
        }
        return selected;
    }

    private static List<RelationshipEvidence> selectRelationshipEvidence(DialogueContext context, String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        boolean cue = lower.matches("(?s).*(\\b(relationship|trust|boundary|boundaries|sorry|apolog|forgive|angry|annoyed|afraid|scared|fear|hurt|hit|stop following|leave me alone|stay away|keep away|need space|too close|thanks|appreciate|count on you|rely on you|with me|between us|how do you feel|do you like me)\\b).*");
        java.time.Instant fresh = context.now().minus(FRESH_RELATIONSHIP_WINDOW);
        List<RelationshipEvidence> ordered = context.relationshipEvidence().stream()
                .filter(item -> cue || !item.occurredAt().isBefore(fresh))
                .sorted(Comparator.comparing(RelationshipEvidence::occurredAt))
                .toList();
        return ordered.subList(Math.max(0, ordered.size() - 3), ordered.size());
    }

    static String memoryCue(String message, List<DialogueTurn> turns) {
        if (!message.toLowerCase(java.util.Locale.ROOT).matches("(?s).*\\b(there|it|that|material|instead|correction)\\b.*")) return message;
        String recent = turns.stream().skip(Math.max(0, turns.size()-2)).map(DialogueTurn::text)
                .collect(java.util.stream.Collectors.joining(" "));
        return message + " " + recent;
    }

    private static List<OrganicPetContext.ThreadCue> selectThreads(
            List<OrganicPetContext.ThreadCue> threads, String message) {
        List<OrganicPetContext.ThreadCue> active = threads.stream()
                .filter(thread -> java.util.Set.of("OPEN", "ACTIVE", "PAUSED").contains(thread.state()))
                .toList();
        return active.stream().filter(thread -> cueOverlap(message,
                thread.summary() + " " + (thread.locationCue() == null ? "" : thread.locationCue())) > 0)
                .limit(2).toList();
    }

    private static int importanceRank(DialogueImportance importance) {
        return switch (importance) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
    }

    /** Mutable selection with the documented drop order: low, oldest medium, weakest card, oldest turn. */
    private static final class Selection {
        private final List<ShortTermMemorySnippet> shortTerm;
        private final List<LongTermMemorySnippet> longTerm;
        private final List<DialogueTurn> turns;
        private final List<RelationshipEvidence> relationship;
        private final List<OrganicPetContext.ThreadCue> threads;

        private Selection(List<ShortTermMemorySnippet> shortTerm, List<LongTermMemorySnippet> longTerm,
                List<DialogueTurn> turns, List<RelationshipEvidence> relationship,
                List<OrganicPetContext.ThreadCue> threads) {
            this.shortTerm = shortTerm;
            this.longTerm = longTerm;
            this.turns = turns;
            this.relationship = relationship;
            this.threads = threads;
        }

        private boolean dropOne() {
            return removeLeastRelevantLow() || removeOldestMedium() || removeLowestLongTerm() || removeOldestTurn();
        }

        private boolean removeLeastRelevantLow() {
            return shortTerm.stream().filter(memory -> memory.importance() == DialogueImportance.LOW)
                    .min(Comparator.comparingDouble(ShortTermMemorySnippet::relevance)
                            .thenComparing(ShortTermMemorySnippet::occurredAt))
                    .map(shortTerm::remove).orElse(false);
        }

        private boolean removeOldestMedium() {
            return shortTerm.stream().filter(memory -> memory.importance() == DialogueImportance.MEDIUM)
                    .min(Comparator.comparing(ShortTermMemorySnippet::occurredAt))
                    .map(shortTerm::remove).orElse(false);
        }

        private boolean removeLowestLongTerm() {
            return longTerm.stream().min(Comparator.comparingDouble(LongTermMemorySnippet::similarity))
                    .map(longTerm::remove).orElse(false);
        }

        private boolean removeOldestTurn() {
            if (turns.isEmpty()) return false;
            turns.removeFirst();
            return true;
        }
    }

    private record RenderedPrompt(String text, int tokens, List<DialogueTurn> chatHistory,
            DialogueContextUsageWire contextUsage) {
    }
}
