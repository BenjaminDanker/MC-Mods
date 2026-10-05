package com.silver.aipets.service.dialogue;

import java.util.Optional;
import java.util.UUID;

/** A source-bound proposal for the existing owner memory and thread tables, never pet testimony. */
public record OwnerMemoryProposal(Kind kind, String summary, String ownerQuote,
        Optional<UUID> replacesMemoryId, Optional<UUID> threadId, Operation threadOperation,
        long expectedMemoryVersion, long expectedThreadVersion) {
    public OwnerMemoryProposal(Kind kind, String summary, String ownerQuote,
            Optional<UUID> replacesMemoryId, Optional<UUID> threadId, Operation operation) {
        this(kind, summary, ownerQuote, replacesMemoryId, threadId, operation, 0, 0);
    }
    public enum Kind { FACT, PREFERENCE, OPEN_THREAD, CORRECTION }
    public enum Operation { NONE, CREATE, CONTINUE, PAUSE, COMPLETE, FORGET }

    public boolean quotedBy(String message) {
        // This checks attribution only. Luna interprets the statement; code never judges the reply.
        return !ownerQuote.isBlank() && message.contains(ownerQuote)
                && !summary.isBlank() && summary.length() <= 500;
    }

    public Optional<OwnerMemoryProposal> bind(String message, DialogueContext context) {
        if (!quotedBy(message) || context == null) return Optional.empty();
        long memoryVersion = 0, threadVersion = 0;
        if (replacesMemoryId.isPresent()) {
            var target = context.longTermMemories().stream()
                    .filter(item -> item.card().memoryId().equals(replacesMemoryId.orElseThrow()))
                    .findFirst();
            if (target.isEmpty()) return Optional.empty();
            memoryVersion = target.orElseThrow().card().version();
        }
        if (kind == Kind.CORRECTION && replacesMemoryId.isEmpty()) return Optional.empty();
        if (threadId.isPresent()) {
            var target = context.organicContext().openThreads().stream()
                    .filter(item -> item.id().equals(threadId.orElseThrow().toString())).findFirst();
            if (target.isEmpty()) return Optional.empty();
            threadVersion = target.orElseThrow().version();
        }
        if (threadOperation != Operation.NONE && threadOperation != Operation.CREATE && threadId.isEmpty())
            return Optional.empty();
        if (threadOperation == Operation.CREATE && threadId.isPresent()) return Optional.empty();
        return Optional.of(new OwnerMemoryProposal(kind, summary, ownerQuote, replacesMemoryId,
                threadId, threadOperation, memoryVersion, threadVersion));
    }
}
