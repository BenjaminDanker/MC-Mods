package com.silver.witherfight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.witherfight.WitherFightMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and persists {@link WitherControlConfig} instances from the Fabric config directory. Mirrors the
 * Ender Fight tooling so both mods share the same config file and defaults.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "witherfight.json";
    private static final String LEGACY_CONFIG_FILE_NAME = "endcontrol.json";

    private final Path configPath;
    private final Path legacyConfigPath;
    private WitherControlConfig config;

    public ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
        this.legacyConfigPath = configDir.resolve(LEGACY_CONFIG_FILE_NAME);
    }

    public void load() {
        if (Files.exists(configPath)) {
            this.config = readConfig(configPath);
            return;
        }

        if (Files.exists(legacyConfigPath)) {
            WitherFightMod.LOGGER.info("Found legacy config at {}, importing into {}", legacyConfigPath, configPath);
            this.config = readConfig(legacyConfigPath);
            save();
            return;
        }

        WitherFightMod.LOGGER.info("No Wither Fight config present, writing defaults to {}", configPath);
        this.config = WitherControlConfig.createDefault();
        save();
    }

    private WitherControlConfig coerceConfig(WitherControlConfig loaded) {
        if (loaded.portalRedirectCommand() == null || loaded.portalRedirectCommand().isBlank()) {
            String defaultCommand = WitherControlConfig.createDefault().portalRedirectCommand();
            WitherFightMod.LOGGER.info("Config missing portal redirect command; defaulting to '{}'", defaultCommand);
            WitherControlConfig updated = new WitherControlConfig(
                loaded.portalRedirectEnabled(),
                defaultCommand
            );
            this.config = updated;
            save();
            return updated;
        }
        return loaded;
    }

    private WitherControlConfig readConfig(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WitherControlConfig loaded = GSON.fromJson(reader, WitherControlConfig.class);
            return coerceConfig(loaded != null ? loaded : WitherControlConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            WitherFightMod.LOGGER.warn("Failed to read config from {}, falling back to defaults", path, ex);
            return WitherControlConfig.createDefault();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            WitherFightMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public WitherControlConfig getConfig() {
        return config != null ? config : WitherControlConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }
}
