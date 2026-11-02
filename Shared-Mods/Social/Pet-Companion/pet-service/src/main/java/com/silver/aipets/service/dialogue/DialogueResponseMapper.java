package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Maps Luna output to the existing state adapter. Never interprets or rewrites conversation. */
public final class DialogueResponseMapper {
    private final DialogueLimits limits;

    public DialogueResponseMapper(DialogueLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ValidatedDialogueOutput map(
            DialogueModelOutput output, String normalizedPlayerMessage) {
        return map(output, normalizedPlayerMessage, null);
    }

    public ValidatedDialogueOutput map(
            DialogueModelOutput output, String normalizedPlayerMessage, DialogueContext context) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(normalizedPlayerMessage, "normalizedPlayerMessage");
        String reply = sanitize(output.reply(), limits.maximumReplyCharacters());
        if (reply.isBlank()) throw new DialogueOutputException("Reply is empty", null);
        DialogueSelfModelCandidate selfCandidate = validateSelfModelCandidate(
                output.selfModelCandidate(), normalizedPlayerMessage, context);
        OwnerInterpretationCandidate interpretation = validateOwnerInterpretation(
                output.ownerInterpretation(), normalizedPlayerMessage);
        return new ValidatedDialogueOutput(
                reply, output.importance(), output.memoryCandidate(), output.traitDeltas(), output.moodDeltas(),
                output.action(), output.attention(), output.affectDelta(), output.relationshipSignal(),
                output.affectStrength(), selfCandidate, interpretation,
                output.ownerMemory().flatMap(item -> item.bind(normalizedPlayerMessage, context)));
    }

    private static OwnerInterpretationCandidate validateOwnerInterpretation(
            OwnerInterpretationCandidate candidate, String ownerMessage) {
        if (OwnerInterpretationMapper.isOwnerCorrection(ownerMessage)) {
            return OwnerInterpretationCandidate.none();
        }
        Optional<String> intent = candidate.intent().map(value -> sanitize(value, 80))
                .filter(value -> !value.isBlank());
        Optional<String> emotion = candidate.emotion().map(value -> sanitize(value, 40))
                .filter(value -> !value.isBlank());
        if (intent.isEmpty() && emotion.isEmpty()) return OwnerInterpretationCandidate.none();
        return new OwnerInterpretationCandidate(intent, emotion, candidate.confidence());
    }

    private static DialogueSelfModelCandidate validateSelfModelCandidate(
            DialogueSelfModelCandidate candidate, String ownerMessage, DialogueContext context) {
        if (!candidate.meaningful() || OwnerInterpretationMapper.isOwnerCorrection(ownerMessage))
            return DialogueSelfModelCandidate.none();
        String normalized = normalizeSelfModelValue(candidate.kind(), sanitize(candidate.value(), 100));
        if (normalized.isBlank()) return DialogueSelfModelCandidate.none();
        // Owner statements are stored as owner-reported memory, never as evidence of the pet's own preference.
        if (ownerMessage.toLowerCase(Locale.ROOT).matches(
                "(?s).*(\\b(i|we)\\s+(like|love|prefer|enjoy|hate|dislike|want)\\b|\\bmy preference\\b).*")) {
            return DialogueSelfModelCandidate.none();
        }
        if (context == null) return DialogueSelfModelCandidate.none();
        java.util.Set<java.util.UUID> supporting = context.shortTermMemories().stream()
                .filter(memory -> actionCueMatches(normalized, memory.summary()))
                .filter(memory -> supportsObservedPetAction(candidate.kind(), normalized, memory.summary()))
                .map(ShortTermMemorySnippet::eventId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (supporting.isEmpty()) return DialogueSelfModelCandidate.none();
        return new DialogueSelfModelCandidate(candidate.kind(), normalized,
                Math.min(.75, candidate.confidence()), supporting);
    }

    private static boolean supportsObservedPetAction(DialogueSelfModelCandidate.Kind kind,
            String candidate, String witnessedEvent) {
        // The existing Fabric event reports only the completed action enum, not a destination,
        // motive, or owner preference. Preserve that narrow evidence boundary.
        return kind == DialogueSelfModelCandidate.Kind.INTEREST
                && candidate.equalsIgnoreCase("nearby exploration")
                && witnessedEvent.matches("(?is).*\\bACTION_COMPLETED\\b.*\\bPet completed EXPLORE_NEARBY reason=CURIOSITY\\b.*");
    }

    private static String normalizeSelfModelValue(
            DialogueSelfModelCandidate.Kind kind, String candidate) {
        // Luna commonly phrases this witnessed action as "exploring nearby". Keep
        // that bounded synonym mapped to the canonical cue supported by the
        // existing EXPLORE_NEARBY/CURIOSITY server event.
        if (kind == DialogueSelfModelCandidate.Kind.INTEREST
                && candidate.matches("(?i)(?:i )?(?:explore|explored|exploring) nearby(?: out of curiosity)?"
                        + "|nearby exploration(?: out of curiosity)?")) {
            return "nearby exploration";
        }
        return candidate;
    }

    private static boolean actionCueMatches(String message, String witnessedEvent) {
        return witnessedEvent.matches("(?is).*\\bACTION_COMPLETED\\b.*\\bEXPLORE_NEARBY reason=CURIOSITY\\b.*")
                && message.toLowerCase(Locale.ROOT).matches("(?s).*(\\bexplor\\w*|\\bnearby\\b).*");
    }

    public static String sanitize(String value, int maximumCharacters) {

        Objects.requireNonNull(value, "value");
        String sanitized = value.replace('§', ' ')
                .replaceAll("(?i)https?://\\S+|www\\.\\S+", "[link removed]")
                .codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip().replaceAll("\\s+", " ");
        if (sanitized.length() > maximumCharacters) {
            sanitized = sanitized.substring(0, maximumCharacters).stripTrailing();
        }
        return sanitized;
    }
}
