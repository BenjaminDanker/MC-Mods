package com.silver.aipets.service.dialogue;

import java.util.Objects;

public record DialogueGameContext(String backend, String dimension, String eventType,
        com.silver.aipets.common.transport.PetSceneSnapshot scene) {
    public DialogueGameContext(String backend, String dimension, String eventType) {
        this(backend, dimension, eventType,
                com.silver.aipets.common.transport.PetSceneSnapshot.unknown());
    }
    public DialogueGameContext {
        backend = ShortTermMemorySnippet.bounded(backend, 128, "backend");
        dimension = ShortTermMemorySnippet.bounded(dimension, 191, "dimension");
        eventType = ShortTermMemorySnippet.bounded(eventType, 64, "eventType");
        Objects.requireNonNull(scene, "scene");
    }
}
