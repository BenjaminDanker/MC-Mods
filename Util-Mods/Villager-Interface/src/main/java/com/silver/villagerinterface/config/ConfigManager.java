package com.silver.villagerinterface.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.villagerinterface.VillagerInterfaceMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "villagerinterface.json";

    private final Path configPath;
    private VillagerInterfaceConfig config;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            VillagerInterfaceMod.LOGGER.info("No Villager Interface config present, writing defaults to {}", configPath);
            this.config = VillagerInterfaceConfig.createDefault();
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            VillagerInterfaceConfig loaded = GSON.fromJson(reader, VillagerInterfaceConfig.class);
            this.config = coerceConfig(loaded != null ? loaded : VillagerInterfaceConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            VillagerInterfaceMod.LOGGER.warn("Failed to read Villager Interface config, falling back to defaults", ex);
            this.config = VillagerInterfaceConfig.createDefault();
        }
    }

    private VillagerInterfaceConfig coerceConfig(VillagerInterfaceConfig loaded) {
        boolean shouldSave = false;

        int intervalSeconds = loaded.checkIntervalSeconds();
        if (intervalSeconds <= 0) {
            intervalSeconds = VillagerInterfaceConfig.createDefault().checkIntervalSeconds();
            shouldSave = true;
        }

        String ollamaBaseUrl = loaded.ollamaBaseUrl();
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
            ollamaBaseUrl = VillagerInterfaceConfig.createDefault().ollamaBaseUrl();
            shouldSave = true;
        }

        String ollamaModel = loaded.ollamaModel();
        if (ollamaModel == null || ollamaModel.isBlank()) {
            ollamaModel = VillagerInterfaceConfig.createDefault().ollamaModel();
            shouldSave = true;
        }

        String ollamaKeepAlive = loaded.ollamaKeepAlive();
        if (ollamaKeepAlive == null || ollamaKeepAlive.isBlank()) {
            ollamaKeepAlive = VillagerInterfaceConfig.createDefault().ollamaKeepAlive();
            shouldSave = true;
        }

        int ollamaTimeoutSeconds = loaded.ollamaTimeoutSeconds();
        if (ollamaTimeoutSeconds <= 0) {
            ollamaTimeoutSeconds = VillagerInterfaceConfig.createDefault().ollamaTimeoutSeconds();
            shouldSave = true;
        }

        int maxHistoryTurns = loaded.maxHistoryTurns();
        if (maxHistoryTurns <= 0) {
            maxHistoryTurns = VillagerInterfaceConfig.createDefault().maxHistoryTurns();
            shouldSave = true;
        }

        List<VillagerConfigEntry> entries = new ArrayList<>();
        List<VillagerConfigEntry> loadedEntries = loaded.villagers();
        if (loadedEntries != null) {
            for (VillagerConfigEntry entry : loadedEntries) {
                if (entry == null || entry.id() == null || entry.id().isBlank()) {
                    continue;
                }

                String villagerType = entry.villagerType();
                if (villagerType == null || villagerType.isBlank()) {
                    villagerType = "villager";
                    shouldSave = true;
                }

                String displayName = entry.displayName();
                if (displayName == null || displayName.isBlank()) {
                    displayName = entry.id();
                    shouldSave = true;
                }

                String dimension = entry.dimension();
                if (dimension == null || dimension.isBlank()) {
                    dimension = "minecraft:overworld";
                    shouldSave = true;
                }

                VillagerPosition position = entry.position();
                if (position == null) {
                    position = new VillagerPosition(0.0, 64.0, 0.0);
                    shouldSave = true;
                }

                double maxDistance = entry.maxDistance();
                if (maxDistance <= 0.0) {
                    maxDistance = 5.0;
                    shouldSave = true;
                }

                String systemPrompt = entry.systemPrompt();
                if (systemPrompt == null || systemPrompt.isBlank()) {
                    systemPrompt = VillagerInterfaceConfig.DEFAULT_SYSTEM_PROMPT;
                    shouldSave = true;
                }

                entries.add(new VillagerConfigEntry(
                    entry.id(),
                    villagerType,
                    displayName,
                    dimension,
                    position,
                    entry.yaw(),
                    entry.pitch(),
                    maxDistance,
                    systemPrompt
                ));
            }
        }

        if (entries.isEmpty()) {
            entries.add(VillagerInterfaceConfig.createDefaultVillagerEntry());
            shouldSave = true;
        }

        VillagerInterfaceConfig coerced = new VillagerInterfaceConfig(
            intervalSeconds,
            ollamaBaseUrl,
            ollamaModel,
            ollamaKeepAlive,
            ollamaTimeoutSeconds,
            maxHistoryTurns,
            entries
        );
        int loadedSize = loadedEntries != null ? loadedEntries.size() : 0;
        if (shouldSave
            || coerced.checkIntervalSeconds() != loaded.checkIntervalSeconds()
            || entries.size() != loadedSize
            || !coerced.ollamaBaseUrl().equals(loaded.ollamaBaseUrl())
            || !coerced.ollamaModel().equals(loaded.ollamaModel())
            || !coerced.ollamaKeepAlive().equals(loaded.ollamaKeepAlive())
            || coerced.ollamaTimeoutSeconds() != loaded.ollamaTimeoutSeconds()
            || coerced.maxHistoryTurns() != loaded.maxHistoryTurns()) {
            this.config = coerced;
            save();
        }

        return coerced;
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            VillagerInterfaceMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public VillagerInterfaceConfig getConfig() {
        return config != null ? config : VillagerInterfaceConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }
}
