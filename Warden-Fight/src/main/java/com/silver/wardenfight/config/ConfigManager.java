package com.silver.wardenfight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.wardenfight.WardenFightMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists {@link WardenControlConfig} to Fabric's config directory.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "wardenfight.json";
    private final Path configPath;
    private WardenControlConfig config;

    public ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (Files.exists(configPath)) {
            this.config = readConfig(configPath);
            return;
        }

        WardenFightMod.LOGGER.info("No Warden config present, writing defaults to {}", configPath);
        this.config = WardenControlConfig.createDefault();
        save();
    }

    private WardenControlConfig coerceConfig(WardenControlConfig loaded) {
        WardenControlConfig defaults = WardenControlConfig.createDefault();

        boolean enabled = loaded.portalRedirectEnabled();

        int range = loaded.portalRedirectRange();
        if (range <= 0) {
            range = defaults.portalRedirectRange();
        }

        String command = loaded.portalRedirectCommand();
        if (command == null || command.isBlank()) {
            command = defaults.portalRedirectCommand();
            WardenFightMod.LOGGER.info("Config missing portal redirect command; defaulting to '{}'", command);
        }

        if (enabled != loaded.portalRedirectEnabled()
            || range != loaded.portalRedirectRange()
            || (loaded.portalRedirectCommand() == null || loaded.portalRedirectCommand().isBlank())) {
            WardenControlConfig updated = new WardenControlConfig(enabled, range, command);
            this.config = updated;
            save();
            return updated;
        }

        return loaded;
    }

    private WardenControlConfig readConfig(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WardenControlConfig loaded = GSON.fromJson(reader, WardenControlConfig.class);
            return coerceConfig(loaded != null ? loaded : WardenControlConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            WardenFightMod.LOGGER.warn("Failed to read config from {}, falling back to defaults", path, ex);
            return WardenControlConfig.createDefault();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            WardenFightMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public WardenControlConfig getConfig() {
        return config != null ? config : WardenControlConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }
}
