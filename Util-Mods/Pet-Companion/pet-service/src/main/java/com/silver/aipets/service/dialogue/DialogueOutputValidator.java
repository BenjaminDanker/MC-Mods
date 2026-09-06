package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Sanitizes rendering text and deterministically downgrades trivial interactions. */
public final class DialogueOutputValidator {
    private final DialogueLimits limits;

    public DialogueOutputValidator(DialogueLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ValidatedDialogueOutput validate(
            DialogueModelOutput output, String normalizedPlayerMessage) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(normalizedPlayerMessage, "normalizedPlayerMessage");
        String reply = sanitize(output.reply(), limits.maximumReplyCharacters());
        if (reply.isBlank()) {
            throw new DialogueOutputException("Sanitized reply is empty", null);
        }
        DialogueImportance importance = output.importance();
        String lower = normalizedPlayerMessage.toLowerCase(Locale.ROOT);
        if (lower.matches("(?:hi|hello|hey|hiya|yo)[!. ]*")) {
            importance = DialogueImportance.LOW;
        }
        Optional<String> candidate = output.memoryCandidate()
                .map(value -> sanitize(value, 500))
                .filter(value -> !value.isBlank());
        if (importance == DialogueImportance.LOW) {
            candidate = Optional.empty();
        }
        return new ValidatedDialogueOutput(
                reply, importance, candidate, output.traitDeltas(), output.moodDeltas());
    }

    static String sanitize(String value, int maximumCharacters) {
        Objects.requireNonNull(value, "value");
        String sanitized = value.replace('§', ' ')
                .replaceAll("(?i)https?://\\S+|www\\.\\S+", "[link removed]")
                .codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip().replaceAll("\\s+", " ");
        int sentenceEnds = 0;
        int end = sanitized.length();
        for (int index = 0; index < sanitized.length(); index++) {
            char character = sanitized.charAt(index);
            if (character == '.' || character == '!' || character == '?') {
                sentenceEnds++;
                if (sentenceEnds == 2) {
                    end = index + 1;
                    break;
                }
            }
        }
        sanitized = sanitized.substring(0, end).strip();
        if (sanitized.length() > maximumCharacters) {
            sanitized = sanitized.substring(0, maximumCharacters).stripTrailing();
        }
        return sanitized;
    }
}
