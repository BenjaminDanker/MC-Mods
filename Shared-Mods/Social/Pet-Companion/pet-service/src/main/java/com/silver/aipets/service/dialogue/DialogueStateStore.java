package com.silver.aipets.service.dialogue;

/** Successful commits must atomically persist event, usage, bounded state, and trait audits. */
public interface DialogueStateStore {
    DialoguePersistResult commit(DialoguePersistRequest request);

    void recordUsage(DialogueUsage usage);
}
