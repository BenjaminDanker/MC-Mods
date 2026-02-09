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
    private static final String CONFIG_FILE_NAME = "enderfight.json";

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
        boolean changed = false;

        String portalRedirectCommand = loaded.portalRedirectCommand();
        if (portalRedirectCommand == null || portalRedirectCommand.isBlank()) {
            portalRedirectCommand = EndControlConfig.createDefault().portalRedirectCommand();
            EnderFightMod.LOGGER.info("Config missing portal redirect command; defaulting to '{}'", portalRedirectCommand);
            changed = true;
        }

        int usesDefault = loaded.customBreathTrackingUsesDefault();
        if (usesDefault <= 0) {
            usesDefault = EndControlConfig.DEFAULT_CUSTOM_BREATH_TRACKING_USES;
            EnderFightMod.LOGGER.info("Config missing custom breath tracking uses; defaulting to {}", usesDefault);
            changed = true;
        }

        String customBreathId = loaded.customBreathId();
        if (customBreathId == null || customBreathId.isBlank()) {
            customBreathId = EndControlConfig.DEFAULT_CUSTOM_BREATH_ID;
            EnderFightMod.LOGGER.info("Config missing custom breath id; defaulting to '{}'", customBreathId);
            changed = true;
        }

        if (!changed) {
            return loaded;
        }

        EndControlConfig updated = new EndControlConfig(
            loaded.resetIntervalHours(),
            loaded.resetWarningSeconds(),
            loaded.warningMessage(),
            loaded.teleportMessage(),
            loaded.customBreathEnabled(),
            usesDefault,
            customBreathId,
            loaded.portalRedirectEnabled(),
            portalRedirectCommand
        );
        this.config = updated;
        save();
        return updated;
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
