package com.silver.aipets.service.dialogue;

import java.util.Objects;

public record DialoguePrompt(String text, int inputTokens) {
    public DialoguePrompt {
        Objects.requireNonNull(text, "text");
        if (text.isBlank() || inputTokens < 1) {
            throw new IllegalArgumentException("Dialogue prompt must be nonempty");
        }
    }
}
