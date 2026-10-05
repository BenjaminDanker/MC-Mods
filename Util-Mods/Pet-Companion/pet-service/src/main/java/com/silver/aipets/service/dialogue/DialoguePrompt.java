package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.DialogueContextUsageWire;

import java.util.Objects;
import java.util.Optional;

public record DialoguePrompt(
        String text,
        int inputTokens,
        DialogueContextUsageWire contextUsage,
        Optional<String> currentOwnerMessage) {
    public DialoguePrompt(String text, int inputTokens) {
        this(text, inputTokens, DialogueContextUsageWire.empty());
    }

    public DialoguePrompt(String text, int inputTokens, DialogueContextUsageWire contextUsage) {
        this(text, inputTokens, contextUsage, Optional.empty());
    }

    public DialoguePrompt {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(contextUsage, "contextUsage");
        Objects.requireNonNull(currentOwnerMessage, "currentOwnerMessage");
        if (text.isBlank() || inputTokens < 1) {
            throw new IllegalArgumentException("Dialogue prompt must be nonempty");
        }
        if (currentOwnerMessage.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Current owner message must be nonempty");
        }
    }
}
