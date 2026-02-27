package com.silver.entitypruner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class EntityPrunerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "entitypruner.json");

    public int maxMobsPerChunk = 20;
    public int maxItemsPerChunk = 64;
    public boolean enablePruning = true;

    public int itemPruneDelaySeconds = 5;
    public int itemPruneMaxRemovalsPerTick = 1;

    public boolean enablePruneLogging = true;
    public int pruneLogIntervalSeconds = 10;

    public int resyncIntervalSeconds = 15;
    public int resyncMaxEntitiesPerTick = 200;

    private static EntityPrunerConfig instance;

    public static EntityPrunerConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, EntityPrunerConfig.class);
            } catch (IOException e) {
                EntityPruner.LOGGER.error("Failed to load config", e);
                instance = new EntityPrunerConfig();
            }
        } else {
            instance = new EntityPrunerConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            EntityPruner.LOGGER.error("Failed to save config", e);
        }
    }
}