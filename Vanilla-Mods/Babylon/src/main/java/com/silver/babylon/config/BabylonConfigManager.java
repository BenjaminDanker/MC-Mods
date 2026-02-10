package com.silver.babylon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BabylonConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("babylon");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;

    public BabylonConfigManager() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("babylon.json");
    }

    public BabylonConfig loadOrCreate() {
        if (Files.notExists(path)) {
            BabylonConfig cfg = new BabylonConfig();
            save(cfg);
            LOGGER.info("Created default Babylon config at {}", path.toAbsolutePath());
            return cfg;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            BabylonConfig cfg = GSON.fromJson(reader, BabylonConfig.class);
            if (cfg == null) {
                LOGGER.warn("Babylon config at {} parsed to null; using defaults", path.toAbsolutePath());
                return new BabylonConfig();
            }

            boolean changed = false;
            if (cfg.portalRedirectTargetPortal == null) {
                cfg.portalRedirectTargetPortal = "";
                changed = true;
            }

            if (changed) {
                save(cfg);
            }

            LOGGER.info("Loaded Babylon config from {}", path.toAbsolutePath());
            return cfg;
        } catch (Exception e) {
            LOGGER.error("Failed to load Babylon config from {} (using defaults)", path.toAbsolutePath(), e);
            return new BabylonConfig();
        }
    }

    public void save(BabylonConfig config) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory for {}", path.toAbsolutePath(), e);
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write Babylon config to {}", path.toAbsolutePath(), e);
        }
    }
}
