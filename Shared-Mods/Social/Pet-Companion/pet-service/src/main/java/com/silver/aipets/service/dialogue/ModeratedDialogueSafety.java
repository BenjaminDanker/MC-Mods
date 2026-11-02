package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.provider.AiFallbackReporter;

import java.util.Objects;

/**
 * Composes deterministic local checks with best-effort provider moderation. Moderation blocks only
 * its configured categories; when moderation is unavailable the message is let through and the
 * bypass is reported through {@link AiFallbackReporter} so it is never silent.
 */
public final class ModeratedDialogueSafety implements DialogueSafety {
    private final RuleBasedDialogueSafety local;
    private final OpenAiModerationClient moderation;
    private final AiFallbackReporter reporter;

    public ModeratedDialogueSafety(OpenAiModerationClient moderation) {
        this(moderation, AiFallbackReporter.LOG_ONLY);
    }

    public ModeratedDialogueSafety(OpenAiModerationClient moderation, AiFallbackReporter reporter) {
        this.local = new RuleBasedDialogueSafety();
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
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
            reporter.moderationBypassed(result.category());
            return new SafetyResult(true, normalizedInput, "MODERATION_BYPASSED");
        }
        if (!result.allowed()) {
            reporter.moderationBlocked();
            return new SafetyResult(false, "", result.category());
        }
        return new SafetyResult(true, normalizedInput, "ALLOWED");
    }
}
