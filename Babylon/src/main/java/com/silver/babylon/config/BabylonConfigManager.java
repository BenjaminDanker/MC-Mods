package com.silver.babylon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BabylonConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;

    public BabylonConfigManager() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("babylon.json");
    }

    public BabylonConfig loadOrCreate() {
        if (Files.notExists(path)) {
            BabylonConfig cfg = new BabylonConfig();
            save(cfg);
            return cfg;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            BabylonConfig cfg = GSON.fromJson(reader, BabylonConfig.class);
            return cfg == null ? new BabylonConfig() : cfg;
        } catch (IOException e) {
            return new BabylonConfig();
        }
    }

    public void save(BabylonConfig config) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
        } catch (IOException ignored) {
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(config, writer);
        } catch (IOException ignored) {
        }
    }
}
