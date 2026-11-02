package com.silver.aipets.service.provider;

import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.config.PetServiceConfig;
import com.silver.aipets.service.dialogue.DialogueModelClient;
import com.silver.aipets.service.dialogue.FallbackDialogueModelClient;
import com.silver.aipets.service.dialogue.OpenAiDialogueModelClient;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds structured-output chat clients: OpenAI is always primary; when an OpenRouter key is
 * configured and the fallback is enabled, a failed OpenAI call is retried once on the same model
 * through OpenRouter. Both use the same client; only the {@link ProviderTarget} differs.
 */
public final class ChatModels {
    private final ProviderTarget primary;
    private final Optional<ProviderTarget> fallback;
    private final Duration timeout;
    private final AiPricing pricing;
    private final AiFallbackReporter reporter;

    public ChatModels(ProviderTarget primary, Optional<ProviderTarget> fallback, Duration timeout,
            AiPricing pricing, AiFallbackReporter reporter) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    public static ChatModels from(PetServiceConfig config, AiFallbackReporter reporter) {
        Objects.requireNonNull(config, "config");
        Optional<ProviderTarget> fallback = config.openRouterFallback()
                ? Optional.of(ProviderTarget.openRouter(config.openRouterBaseUri(), config.openRouterApiKey(),
                        config.aiPricing().dialogueHoldUsd()))
                : Optional.empty();
        return new ChatModels(ProviderTarget.openAi(config.openAiBaseUri(), config.openAiApiKey()), fallback,
                Duration.ofMillis(config.dialogueTimeoutMs()), config.aiPricing(), reporter);
    }

    /** The fallback target, present only when a fallback is enabled (also used for embeddings). */
    public Optional<ProviderTarget> fallbackTarget() {
        return fallback;
    }

    /**
     * @param model OpenAI model id
     * @param reasoningEffort none/minimal/low/medium/high, or blank to omit
     */
    public DialogueModelClient create(String model, String schemaName, int maxCompletionTokens,
            String reasoningEffort) {
        DialogueModelClient direct = new OpenAiDialogueModelClient(primary, model, timeout, schemaName,
                pricing, reasoningEffort, maxCompletionTokens);
        if (fallback.isEmpty()) return direct;
        DialogueModelClient viaFallback = new OpenAiDialogueModelClient(fallback.orElseThrow(),
                PetServiceConfig.openRouterModel(model), timeout, schemaName, pricing, reasoningEffort,
                maxCompletionTokens);
        return new FallbackDialogueModelClient(direct, viaFallback, reporter);
    }
}
