package com.silver.villagerinterface.config;

/** Settings shared by every supported AI provider. */
public final class ConversationSettings {
    private final String activeProvider;
    private final int checkIntervalSeconds;
    private final int maxHistoryTurns;

    public ConversationSettings(String activeProvider, int checkIntervalSeconds, int maxHistoryTurns) {
        this.activeProvider = activeProvider;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public String activeProvider() { return activeProvider; }
    public int checkIntervalSeconds() { return checkIntervalSeconds; }
    public int maxHistoryTurns() { return maxHistoryTurns; }
}
