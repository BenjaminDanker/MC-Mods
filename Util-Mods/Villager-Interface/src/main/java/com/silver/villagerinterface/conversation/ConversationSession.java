package com.silver.villagerinterface.conversation;

import com.silver.villagerinterface.config.VillagerConfigEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationSession {
    private final VillagerConfigEntry entry;
    private final int maxHistoryTurns;
    private final List<OllamaChatMessage> history = new ArrayList<>();
    private boolean awaitingResponse;
    private BlacksmithInteraction.PendingModification pendingModification;

    public ConversationSession(VillagerConfigEntry entry, int maxHistoryTurns) {
        this.entry = entry;
        this.maxHistoryTurns = Math.max(1, maxHistoryTurns);
    }

    public VillagerConfigEntry entry() {
        return entry;
    }

    public boolean isAwaitingResponse() {
        return awaitingResponse;
    }

    public void setAwaitingResponse(boolean awaitingResponse) {
        this.awaitingResponse = awaitingResponse;
    }

    public void addSystemPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        history.add(OllamaChatMessage.system(prompt));
    }

    public void addSystemMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        history.add(OllamaChatMessage.system(message));
        trimHistory();
    }

    public void addUserMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        history.add(OllamaChatMessage.user(message));
        trimHistory();
    }

    public void addAssistantMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        history.add(OllamaChatMessage.assistant(message));
        trimHistory();
    }

    public List<OllamaChatMessage> history() {
        return Collections.unmodifiableList(history);
    }

    public BlacksmithInteraction.PendingModification getPendingModification() {
        return pendingModification;
    }

    public void setPendingModification(BlacksmithInteraction.PendingModification pendingModification) {
        this.pendingModification = pendingModification;
    }

    private void trimHistory() {
        int maxMessages = maxHistoryTurns * 2 + 1;
        if (history.size() <= maxMessages) {
            return;
        }

        int startIndex = history.size() - maxMessages;
        List<OllamaChatMessage> trimmed = new ArrayList<>(history.subList(startIndex, history.size()));
        history.clear();
        history.addAll(trimmed);
    }
}
