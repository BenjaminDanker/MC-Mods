package com.silver.aipets.common.transport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded admin-only conversation and provider-usage projection. */
public record DialogueHistoryWireResult(
        UUID ownerUuid,
        UUID petId,
        String petName,
        List<ConversationEntry> conversations,
        List<UsageEntry> usage) {
    public DialogueHistoryWireResult {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        petName = bounded(petName, 64, "petName");
        conversations = List.copyOf(Objects.requireNonNull(conversations, "conversations"));
        usage = List.copyOf(Objects.requireNonNull(usage, "usage"));
        if (conversations.size() > 32 || usage.size() > 64) {
            throw new IllegalArgumentException("dialogue history is too large");
        }
    }

    public record ConversationEntry(
            Instant occurredAt,
            String importance,
            String summary,
            String ownerText,
            String petReply) {
        public ConversationEntry {
            Objects.requireNonNull(occurredAt, "occurredAt");
            importance = bounded(importance, 16, "importance");
            summary = bounded(summary, 2_000, "summary");
            ownerText = nullableBounded(ownerText, 2_000, "ownerText");
            petReply = nullableBounded(petReply, 2_000, "petReply");
        }
    }

    public record UsageEntry(
            Instant createdAt,
            String operation,
            String model,
            int inputTokens,
            int cachedInputTokens,
            int outputTokens,
            BigDecimal estimatedCost,
            String status,
            Optional<DialogueContextUsageWire> context) {
        public UsageEntry {
            Objects.requireNonNull(createdAt, "createdAt");
            operation = bounded(operation, 32, "operation");
            model = bounded(model, 191, "model");
            if (inputTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens
                    || outputTokens < 0) {
                throw new IllegalArgumentException("invalid usage token counts");
            }
            Objects.requireNonNull(estimatedCost, "estimatedCost");
            if (estimatedCost.signum() < 0) throw new IllegalArgumentException("negative cost");
            status = bounded(status, 32, "status");
            context = Objects.requireNonNull(context, "context");
        }
    }

    private static String bounded(String value, int max, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }

    private static String nullableBounded(String value, int max, String name) {
        return value == null ? null : bounded(value, max, name);
    }
}
