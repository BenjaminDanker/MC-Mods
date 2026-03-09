package com.silver.villagerinterface.conversation;

import com.silver.villagerinterface.config.VillagerConfigEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class ConversationSession {
    private final VillagerConfigEntry entry;
    private final int maxHistoryTurns;
    private final List<OllamaChatMessage> history = new ArrayList<>();
    private boolean awaitingResponse;
    private boolean cancellationRequested;
    private CompletableFuture<?> activeRequest;
    private Stream<String> activeResponseStream;
    private BlacksmithInteraction.PendingModification pendingModification;

    public ConversationSession(VillagerConfigEntry entry, int maxHistoryTurns) {
        this.entry = entry;
        this.maxHistoryTurns = Math.max(1, maxHistoryTurns);
    }

    public VillagerConfigEntry entry() {
        return entry;
    }

    public synchronized boolean isAwaitingResponse() {
        return awaitingResponse;
    }

    public synchronized void setAwaitingResponse(boolean awaitingResponse) {
        this.awaitingResponse = awaitingResponse;
    }

    public synchronized void beginRequest(CompletableFuture<?> activeRequest) {
        this.awaitingResponse = true;
        this.cancellationRequested = false;
        this.activeRequest = activeRequest;
        this.activeResponseStream = null;
    }

    public synchronized boolean attachResponseStream(Stream<String> responseStream) {
        if (cancellationRequested) {
            closeStream(responseStream);
            return false;
        }

        this.activeResponseStream = responseStream;
        return true;
    }

    public synchronized boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public synchronized boolean cancelActiveRequest() {
        boolean hadActiveRequest = awaitingResponse || activeRequest != null || activeResponseStream != null;
        cancellationRequested = hadActiveRequest;
        awaitingResponse = false;

        CompletableFuture<?> request = activeRequest;
        activeRequest = null;
        if (request != null) {
            request.cancel(true);
        }

        Stream<String> responseStream = activeResponseStream;
        activeResponseStream = null;
        closeStream(responseStream);
        return hadActiveRequest;
    }

    public synchronized void clearActiveRequest() {
        awaitingResponse = false;
        cancellationRequested = false;
        activeRequest = null;
        activeResponseStream = null;
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

    private void closeStream(Stream<String> responseStream) {
        if (responseStream == null) {
            return;
        }

        try {
            responseStream.close();
        } catch (Exception ignored) {
        }
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
