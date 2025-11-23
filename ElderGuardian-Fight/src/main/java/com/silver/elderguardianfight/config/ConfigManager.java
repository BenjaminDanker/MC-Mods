package com.silver.elderguardianfight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.silver.elderguardianfight.ElderGuardianFightMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists {@link ElderGuardianControlConfig} to Fabric's config directory.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "elderguardianfight.json";
    private final Path configPath;
    private ElderGuardianControlConfig config;

    public ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (Files.exists(configPath)) {
            this.config = readConfig(configPath);
            return;
        }

        ElderGuardianFightMod.LOGGER.info("No Elder Guardian config present, writing defaults to {}", configPath);
        this.config = ElderGuardianControlConfig.createDefault();
        save();
    }

    private ElderGuardianControlConfig coerceConfig(ElderGuardianControlConfig loaded) {
        if (loaded.portalRedirectCommand() == null || loaded.portalRedirectCommand().isBlank()) {
            String defaultCommand = ElderGuardianControlConfig.createDefault().portalRedirectCommand();
            ElderGuardianFightMod.LOGGER.info("Config missing portal redirect command; defaulting to '{}'", defaultCommand);
            ElderGuardianControlConfig updated = new ElderGuardianControlConfig(
                loaded.portalRedirectEnabled(),
                defaultCommand
            );
            this.config = updated;
            save();
            return updated;
        }
        return loaded;
    }

    private ElderGuardianControlConfig readConfig(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ElderGuardianControlConfig loaded = GSON.fromJson(reader, ElderGuardianControlConfig.class);
            return coerceConfig(loaded != null ? loaded : ElderGuardianControlConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            ElderGuardianFightMod.LOGGER.warn("Failed to read config from {}, falling back to defaults", path, ex);
            return ElderGuardianControlConfig.createDefault();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            ElderGuardianFightMod.LOGGER.error("Unable to write config file {}", configPath, ex);
        }
    }

    public ElderGuardianControlConfig getConfig() {
        return config != null ? config : ElderGuardianControlConfig.createDefault();
    }

    public Path getConfigPath() {
        return configPath;
    }
}
