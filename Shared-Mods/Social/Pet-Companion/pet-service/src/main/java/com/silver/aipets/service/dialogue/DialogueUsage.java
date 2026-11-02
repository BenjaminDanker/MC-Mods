package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.DialogueContextUsageWire;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DialogueUsage(
        UUID callId,
        UUID requestId,
        UUID petId,
        UUID ownerUuid,
        String model,
        Optional<String> providerId,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        long latencyMillis,
        Status status,
        Optional<String> errorCategory,
        Instant createdAt,
        Optional<DialogueContextUsageWire> contextUsage) {
    public DialogueUsage(
            UUID callId, UUID requestId, UUID petId, UUID ownerUuid, String model,
            Optional<String> providerId, int inputTokens, int cachedInputTokens, int outputTokens,
            BigDecimal estimatedCost, long latencyMillis, Status status,
            Optional<String> errorCategory, Instant createdAt) {
        this(callId, requestId, petId, ownerUuid, model, providerId, inputTokens,
                cachedInputTokens, outputTokens, estimatedCost, latencyMillis, status,
                errorCategory, createdAt, Optional.empty());
    }

    public DialogueUsage {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(errorCategory, "errorCategory");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(contextUsage, "contextUsage");
        if (inputTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens
                || outputTokens < 0 || estimatedCost.signum() < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("Invalid usage values");
        }
    }

    public enum Status {
        SUCCEEDED,
        FAILED,
        TIMED_OUT,
        REJECTED
    }
}
