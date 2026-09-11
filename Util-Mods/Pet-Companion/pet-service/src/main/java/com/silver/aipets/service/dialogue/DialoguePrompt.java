package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.DialogueContextUsageWire;

import java.util.Objects;

public record DialoguePrompt(
        String text,
        int inputTokens,
        DialogueContextUsageWire contextUsage) {
    public DialoguePrompt(String text, int inputTokens) {
        this(text, inputTokens, DialogueContextUsageWire.empty());
    }

    public DialoguePrompt {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(contextUsage, "contextUsage");
        if (text.isBlank() || inputTokens < 1) {
            throw new IllegalArgumentException("Dialogue prompt must be nonempty");
        }
    }
}
