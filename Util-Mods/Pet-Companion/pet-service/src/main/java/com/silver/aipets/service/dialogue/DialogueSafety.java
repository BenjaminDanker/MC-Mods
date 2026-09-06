package com.silver.aipets.service.dialogue;

public interface DialogueSafety {
    SafetyResult preprocess(String playerInput, int maximumCharacters);

    record SafetyResult(boolean allowed, String normalizedText, String category) {
        public SafetyResult {
            java.util.Objects.requireNonNull(normalizedText, "normalizedText");
            java.util.Objects.requireNonNull(category, "category");
        }
    }
}
