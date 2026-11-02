package com.silver.skyislands.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SkyIslandsPortalConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "sky_islands_portal.json";

    private final Logger logger;
    private final Path configPath;
    private SkyIslandsPortalConfig config;

    public SkyIslandsPortalConfigManager(Logger logger) {
        this.logger = logger;
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    public void load() {
        if (!Files.exists(configPath)) {
            this.config = SkyIslandsPortalConfig.createDefault();
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            SkyIslandsPortalConfig loaded = GSON.fromJson(reader, SkyIslandsPortalConfig.class);
            this.config = coerceConfig(loaded != null ? loaded : SkyIslandsPortalConfig.createDefault());
        } catch (IOException | JsonParseException ex) {
            logger.warn("Failed to read Sky-Islands portal config, falling back to defaults", ex);
            this.config = SkyIslandsPortalConfig.createDefault();
        }
    }

    public SkyIslandsPortalConfig getConfig() {
        return config != null ? config : SkyIslandsPortalConfig.createDefault();
    }

    private SkyIslandsPortalConfig coerceConfig(SkyIslandsPortalConfig loaded) {
        boolean changed = false;

        String targetServer = loaded.portalRedirectTargetServer();
        if (targetServer == null || targetServer.isBlank()) {
            targetServer = SkyIslandsPortalConfig.DEFAULT_TARGET_SERVER;
            changed = true;
        }

        String secret = loaded.portalRequestSecret();
        if (secret == null || secret.isBlank()) {
            secret = SkyIslandsPortalConfig.DEFAULT_PORTAL_REQUEST_SECRET;
            changed = true;
        }

        if (!changed) {
            return loaded;
        }

        SkyIslandsPortalConfig updated = new SkyIslandsPortalConfig(
            loaded.portalRedirectEnabled(),
            targetServer,
            secret
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
            logger.error("Unable to write Sky-Islands portal config {}", configPath, ex);
        }
    }
}