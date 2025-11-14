package com.silver.enderfight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.enderfight.EnderFightMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and persists {@link EndControlConfig} instances from the Fabric config directory. The mod reads
 * the file on startup and writes defaults if the configuration is absent or malformed.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "endcontrol.json";

    private final Path configPath;
    private EndControlConfig config;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            EnderFightMod.LOGGER.info("No config present, writing defaults to {}", configPath);
            this.config = EndControlConfig.createDefault();
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            EndControlConfig loaded = GSON.fromJson(reader, EndControlConfig.class);
            this.config = coerceConfig(loaded != null ? loaded : EndControlConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            EnderFightMod.LOGGER.warn("Failed to read config, falling back to defaults", ex);
            this.config = EndControlConfig.createDefault();
        }
    }

    private EndControlConfig coerceConfig(EndControlConfig loaded) {
        if (loaded.portalRedirectCommand() == null || loaded.portalRedirectCommand().isBlank()) {
            String defaultCommand = EndControlConfig.createDefault().portalRedirectCommand();
            EnderFightMod.LOGGER.info("Config missing portal redirect command; defaulting to '{}'",
                defaultCommand);
            EndControlConfig updated = new EndControlConfig(
                loaded.resetIntervalHours(),
                loaded.resetWarningSeconds(),
                loaded.warningMessage(),
                loaded.teleportMessage(),
                loaded.customBreathEnabled(),
                loaded.portalRedirectEnabled(),
                defaultCommand
            );
            this.config = updated;
            save();
            return updated;
        }
        return loaded;
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            EnderFightMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public EndControlConfig getConfig() {
        return config != null ? config : EndControlConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }
}
