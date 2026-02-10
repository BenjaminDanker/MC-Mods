package com.silver.villagerinterface.config;

import java.util.Collections;
import java.util.List;

public final class VillagerInterfaceConfig {
    public static VillagerInterfaceConfig createDefault() {
        return new VillagerInterfaceConfig(10, "http://localhost:11434", "phi3", "-1", 120, 10, Collections.emptyList());
    }

    private final int checkIntervalSeconds;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final String ollamaKeepAlive;
    private final int ollamaTimeoutSeconds;
    private final int maxHistoryTurns;
    private final List<VillagerConfigEntry> villagers;

    public VillagerInterfaceConfig(
        int checkIntervalSeconds,
        String ollamaBaseUrl,
        String ollamaModel,
        String ollamaKeepAlive,
        int ollamaTimeoutSeconds,
        int maxHistoryTurns,
        List<VillagerConfigEntry> villagers
    ) {
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.ollamaKeepAlive = ollamaKeepAlive;
        this.ollamaTimeoutSeconds = ollamaTimeoutSeconds;
        this.maxHistoryTurns = maxHistoryTurns;
        this.villagers = villagers != null ? List.copyOf(villagers) : Collections.emptyList();
    }

    public int checkIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public String ollamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public String ollamaModel() {
        return ollamaModel;
    }

    public String ollamaKeepAlive() {
        return ollamaKeepAlive;
    }

    public int ollamaTimeoutSeconds() {
        return ollamaTimeoutSeconds;
    }

    public int maxHistoryTurns() {
        return maxHistoryTurns;
    }

    public List<VillagerConfigEntry> villagers() {
        return villagers;
    }
}
