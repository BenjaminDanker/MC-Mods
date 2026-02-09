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

    private final Path configPath;
    private WitherControlConfig config;

    public ConfigManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (Files.exists(configPath)) {
            this.config = readConfig(configPath);
            return;
        }

        WitherFightMod.LOGGER.info("No Wither Fight config present, writing defaults to {}", configPath);
        this.config = WitherControlConfig.createDefault();
        save();
    }

    private WitherControlConfig coerceConfig(WitherControlConfig loaded) {
        WitherControlConfig defaults = WitherControlConfig.createDefault();

        boolean enabled = loaded.portalRedirectEnabled();

        String targetServer = loaded.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            targetServer = defaults.portalRedirectTargetServer();
            WitherFightMod.LOGGER.info("Config missing portal redirect target server; defaulting to '{}'", targetServer);
        }

        String secret = loaded.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            secret = defaults.portalRequestSecret();
            WitherFightMod.LOGGER.info("Config missing portal request secret; defaulting to '{}'", secret);
        }

        String targetPortal = loaded.portalRedirectTargetPortal();
        if (targetPortal == null) {
            targetPortal = "";
        }

        boolean changed = enabled != loaded.portalRedirectEnabled()
            || (loaded.portalRedirectTargetServer() == null || loaded.portalRedirectTargetServer().isBlank())
            || (loaded.portalRequestSecret() == null || loaded.portalRequestSecret().isBlank())
            || loaded.portalRedirectTargetPortal() == null;

        if (changed) {
            WitherControlConfig updated = new WitherControlConfig(enabled, targetServer, targetPortal, secret);
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
