package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.provider.AiFallbackReporter;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tries the primary provider and, if it fails, retries once on a secondary provider serving the
 * same model. The primary gets most of the time budget; the fallback gets what is left. A response
 * is charged by whichever provider produced it (token pricing for OpenAI, reported cost for
 * OpenRouter). Only transport, availability, and malformed-response failures fall back; the
 * caller's own validation of a returned response is unchanged.
 */
public final class FallbackDialogueModelClient implements DialogueModelClient {
    private static final Duration MINIMUM_FALLBACK_BUDGET = Duration.ofMillis(1_500);

    private final DialogueModelClient primary;
    private final DialogueModelClient fallback;
    private final AiFallbackReporter reporter;

    public FallbackDialogueModelClient(DialogueModelClient primary, DialogueModelClient fallback) {
        this(primary, fallback, AiFallbackReporter.LOG_ONLY);
    }

    public FallbackDialogueModelClient(
            DialogueModelClient primary, DialogueModelClient fallback, AiFallbackReporter reporter) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
    }

    @Override
    public String model() {
        return primary.model();
    }

    @Override
    public DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long started = System.nanoTime();
        Duration primaryBudget = timeout.multipliedBy(6).dividedBy(10);
        if (primaryBudget.compareTo(Duration.ofSeconds(1)) < 0) primaryBudget = timeout;
        try {
            return primary.complete(requestId, prompt, requiredJsonSchema, primaryBudget);
        } catch (IllegalStateException primaryFailure) {
            Duration remaining = timeout.minus(Duration.ofNanos(System.nanoTime() - started));
            String reason = primaryFailure.getClass().getSimpleName() + ": "
                    + (primaryFailure.getMessage() == null ? "" : primaryFailure.getMessage());
            if (remaining.compareTo(MINIMUM_FALLBACK_BUDGET) < 0) {
                reporter.fallbackFailed(AiFallbackReporter.Operation.CHAT, primary.model(),
                        "no time left to fall back after " + reason, Optional.of(requestId));
                throw primaryFailure;
            }
            try {
                DialogueModelResponse response = fallback.complete(requestId, prompt, requiredJsonSchema, remaining);
                reporter.fallbackUsed(AiFallbackReporter.Operation.CHAT, primary.model(), reason, Optional.of(requestId));
                return response;
            } catch (IllegalStateException fallbackFailure) {
                // Surface the primary failure; the fallback is best effort.
                reporter.fallbackFailed(AiFallbackReporter.Operation.CHAT, primary.model(),
                        reason + " | fallback: " + fallbackFailure.getClass().getSimpleName(), Optional.of(requestId));
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
    }
}
