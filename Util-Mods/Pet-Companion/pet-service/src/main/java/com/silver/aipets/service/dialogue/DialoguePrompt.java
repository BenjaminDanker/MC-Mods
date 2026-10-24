package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.DialogueContextUsageWire;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One provider request: the system text, optional prior conversation sent as real chat
 * turns (oldest first), and the current owner message as the final user turn.
 */
public record DialoguePrompt(
        String text,
        int inputTokens,
        DialogueContextUsageWire contextUsage,
        Optional<String> currentOwnerMessage,
        List<DialogueTurn> chatHistory) {
    public DialoguePrompt(String text, int inputTokens) {
        this(text, inputTokens, DialogueContextUsageWire.empty());
    }

    public DialoguePrompt(String text, int inputTokens, DialogueContextUsageWire contextUsage) {
        this(text, inputTokens, contextUsage, Optional.empty());
    }

    public DialoguePrompt(String text, int inputTokens, DialogueContextUsageWire contextUsage,
            Optional<String> currentOwnerMessage) {
        this(text, inputTokens, contextUsage, currentOwnerMessage, List.of());
    }

    public DialoguePrompt {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(contextUsage, "contextUsage");
        Objects.requireNonNull(currentOwnerMessage, "currentOwnerMessage");
        chatHistory = List.copyOf(Objects.requireNonNull(chatHistory, "chatHistory"));
        if (text.isBlank() || inputTokens < 1) {
            throw new IllegalArgumentException("Dialogue prompt must be nonempty");
        }
        if (currentOwnerMessage.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Current owner message must be nonempty");
        }
        if (!chatHistory.isEmpty() && currentOwnerMessage.isEmpty()) {
            throw new IllegalArgumentException("Chat history requires a current owner message");
        }
    }
}
