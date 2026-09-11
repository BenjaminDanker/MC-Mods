package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded prompt-eligible event history supplied by the authoritative store. */
@FunctionalInterface
public interface DialogueHistoryReader {
    DialogueHistory read(UUID petId, Instant now, int maximumEvents);

    record DialogueHistory(
            List<ShortTermMemorySnippet> shortTermMemories,
            List<DialogueTurn> recentTurns) {
        public DialogueHistory {
            shortTermMemories = List.copyOf(shortTermMemories);
            recentTurns = List.copyOf(recentTurns);
        }

        public static DialogueHistory empty() {
            return new DialogueHistory(List.of(), List.of());
        }
    }
}
