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
        SafetyResult localResult = local.preprocess(playerInput, maximumCharacters);
        if (!localResult.allowed()) return localResult;
        OpenAiModerationClient.ModerationResult result = moderation.moderate(localResult.normalizedText());
        if (!result.available()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "dialogue moderation unavailable; continuing with local safety ({0})",
                    result.category());
            return localResult;
        }
        return result.allowed()
                ? localResult
                : new SafetyResult(false, "", result.category());
    }
}
