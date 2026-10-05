package com.silver.aipets.service.dialogue;

import java.util.Objects;

/** Composes deterministic local checks with best-effort provider moderation. */
public final class ModeratedDialogueSafety implements DialogueSafety {
    private static final System.Logger LOGGER =
            System.getLogger(ModeratedDialogueSafety.class.getName());
    private final RuleBasedDialogueSafety local;
    private final OpenAiModerationClient moderation;

    public ModeratedDialogueSafety(OpenAiModerationClient moderation) {
        this.local = new RuleBasedDialogueSafety();
        this.moderation = Objects.requireNonNull(moderation, "moderation");
    }

    @Override
    public SafetyResult preprocess(String playerInput, int maximumCharacters) {
        SafetyResult localResult = preprocessLocal(playerInput, maximumCharacters);
        if (!localResult.allowed()) return localResult;
        return moderate(localResult.normalizedText());
    }

    @Override
    public SafetyResult preprocessLocal(String playerInput, int maximumCharacters) {
        return local.preprocess(playerInput, maximumCharacters);
    }

    @Override
    public java.util.concurrent.CompletionStage<SafetyResult> moderateAsync(
            String normalizedInput, java.util.concurrent.Executor executor) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> moderate(normalizedInput), executor);
    }

    private SafetyResult moderate(String normalizedInput) {
        OpenAiModerationClient.ModerationResult result = moderation.moderate(normalizedInput);
        if (!result.available()) {
            LOGGER.log(System.Logger.Level.WARNING, "dialogue moderation unavailable; denying interaction ({0})",
                    result.category());
            return new SafetyResult(false, "", result.category());
        }
        return result.allowed()
                ? new SafetyResult(true, normalizedInput, "ALLOWED")
                : new SafetyResult(false, "", result.category());
    }
}
