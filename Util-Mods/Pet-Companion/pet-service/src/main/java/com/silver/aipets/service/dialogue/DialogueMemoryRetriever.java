package com.silver.aipets.service.dialogue;

import java.util.List;
import java.util.UUID;

/** Bounded, pet-scoped long-term memory lookup used while assembling dialogue context. */
@FunctionalInterface
public interface DialogueMemoryRetriever {
    List<LongTermMemorySnippet> retrieve(
            UUID petId, String playerMessage, String backend, String dimension, int limit);
}
