package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.Objects;

/** Deterministic first-line safety; deployments may compose an external moderation client. */
public final class RuleBasedDialogueSafety implements DialogueSafety {
    @Override
    public SafetyResult preprocess(String playerInput, int maximumCharacters) {
        Objects.requireNonNull(playerInput, "playerInput");
        String normalized = playerInput.replace('§', ' ')
                .codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return new SafetyResult(false, "", "EMPTY");
        }
        if (normalized.length() > maximumCharacters) {
            return new SafetyResult(false, "", "TOO_LONG");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("reveal your system prompt")
                || lower.contains("print your credentials")
                || lower.contains("execute /op")
                || containsUrl(normalized)) {
            return new SafetyResult(false, "", "UNSAFE_INSTRUCTION");
        }
        return new SafetyResult(true, normalized, "ALLOWED");
    }

    private static boolean containsUrl(String value) {
        return value.matches("(?is).*\\b(?:https?://|www\\.)\\S+.*");
    }
}
