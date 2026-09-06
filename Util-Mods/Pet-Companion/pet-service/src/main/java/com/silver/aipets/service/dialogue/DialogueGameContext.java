package com.silver.aipets.service.dialogue;

import java.util.Objects;

public record DialogueGameContext(String backend, String dimension, String eventType) {
    public DialogueGameContext {
        backend = ShortTermMemorySnippet.bounded(backend, 128, "backend");
        dimension = ShortTermMemorySnippet.bounded(dimension, 191, "dimension");
        eventType = ShortTermMemorySnippet.bounded(eventType, 64, "eventType");
    }
}
