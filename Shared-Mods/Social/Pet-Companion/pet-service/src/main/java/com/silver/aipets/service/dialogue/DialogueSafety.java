package com.silver.aipets.service.dialogue;

public interface DialogueSafety {
    SafetyResult preprocess(String playerInput, int maximumCharacters);

    /** Local, immediate gate. Implementations must not call a remote provider here. */
    default SafetyResult preprocessLocal(String playerInput, int maximumCharacters) {
        return preprocess(playerInput, maximumCharacters);
    }

    /** Remote moderation stage, separated so callers can overlap it with context/model work. */
    default java.util.concurrent.CompletionStage<SafetyResult> moderateAsync(
            String normalizedInput, java.util.concurrent.Executor executor) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                new SafetyResult(true, normalizedInput, "ALLOWED"));
    }

    record SafetyResult(boolean allowed, String normalizedText, String category) {
        public SafetyResult {
            java.util.Objects.requireNonNull(normalizedText, "normalizedText");
            java.util.Objects.requireNonNull(category, "category");
        }
    }
}
