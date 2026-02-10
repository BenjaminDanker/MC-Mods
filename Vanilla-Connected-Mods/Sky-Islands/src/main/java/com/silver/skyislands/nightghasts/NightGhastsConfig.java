package com.silver.skyislands.nightghasts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NightGhastsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(NightGhastsConfig.class);

    public final boolean enabled;

    /** Target number of tagged ghasts near each player (night only). */
    public final int targetGhastsPerPlayer;

    /** Spawn pacing per player. */
    public final int spawnIntervalTicks;
    public final int maxSpawnPerPlayerPerInterval;

    /** Spawn ring around player. */
    public final int minSpawnDistanceBlocks;
    public final int maxSpawnDistanceBlocks;

    /** Vertical offsets relative to player Y. */
    public final int spawnYOffsetMin;
    public final int spawnYOffsetMax;

    /** How far we scan for existing tagged ghasts. */
    public final int playerScanRadiusBlocks;

    public final int spawnAttemptsPerInterval;

    private NightGhastsConfig(boolean enabled,
                             int targetGhastsPerPlayer,
                             int spawnIntervalTicks,
                             int maxSpawnPerPlayerPerInterval,
                             int minSpawnDistanceBlocks,
                             int maxSpawnDistanceBlocks,
                             int spawnYOffsetMin,
                             int spawnYOffsetMax,
                             int playerScanRadiusBlocks,
                             int spawnAttemptsPerInterval) {
        this.enabled = enabled;
        this.targetGhastsPerPlayer = clamp(targetGhastsPerPlayer, 0, 16);

        this.spawnIntervalTicks = Math.max(20, spawnIntervalTicks);
        this.maxSpawnPerPlayerPerInterval = clamp(maxSpawnPerPlayerPerInterval, 0, 8);

        this.minSpawnDistanceBlocks = Math.max(0, minSpawnDistanceBlocks);
        this.maxSpawnDistanceBlocks = Math.max(this.minSpawnDistanceBlocks + 8, maxSpawnDistanceBlocks);

        this.spawnYOffsetMin = spawnYOffsetMin;
        this.spawnYOffsetMax = Math.max(spawnYOffsetMin, spawnYOffsetMax);

        this.playerScanRadiusBlocks = Math.max(this.maxSpawnDistanceBlocks + 16, playerScanRadiusBlocks);

        this.spawnAttemptsPerInterval = clamp(spawnAttemptsPerInterval, 1, 64);
    }

    public static NightGhastsConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-nightghasts.json");

        if (!Files.exists(configPath)) {
            NightGhastsConfig defaults = defaults();
            write(configPath, defaults);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] wrote default config: {}", configPath);
            }
            return defaults;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            boolean enabled = getBool(root, "enabled", true);
            int targetGhastsPerPlayer = getInt(root, "targetGhastsPerPlayer", 2);

            int spawnIntervalTicks = getInt(root, "spawnIntervalTicks", 20 * 20);
            int maxSpawnPerPlayerPerInterval = getInt(root, "maxSpawnPerPlayerPerInterval", 1);

            int minSpawnDistanceBlocks = getInt(root, "minSpawnDistanceBlocks", 48);
            int maxSpawnDistanceBlocks = getInt(root, "maxSpawnDistanceBlocks", 128);

            int spawnYOffsetMin = getInt(root, "spawnYOffsetMin", 12);
            int spawnYOffsetMax = getInt(root, "spawnYOffsetMax", 32);

            int playerScanRadiusBlocks = getInt(root, "playerScanRadiusBlocks", 176);

            int spawnAttemptsPerInterval = getInt(root, "spawnAttemptsPerInterval", 16);

            NightGhastsConfig parsed = new NightGhastsConfig(
                    enabled,
                    targetGhastsPerPlayer,
                    spawnIntervalTicks,
                    maxSpawnPerPlayerPerInterval,
                    minSpawnDistanceBlocks,
                    maxSpawnDistanceBlocks,
                    spawnYOffsetMin,
                    spawnYOffsetMax,
                    playerScanRadiusBlocks,
                    spawnAttemptsPerInterval
            );

            write(configPath, parsed);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] loaded config: {} enabled={} target={} interval={}",
                        configPath, parsed.enabled, parsed.targetGhastsPerPlayer, parsed.spawnIntervalTicks);
            }
            return parsed;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            NightGhastsConfig defaults = defaults();
            write(configPath, defaults);
            LOGGER.warn("[Sky-Islands][nightghasts] failed to read config; using defaults. path={} err={}", configPath, e.toString());
            return defaults;
        }
    }

    private static NightGhastsConfig defaults() {
        return new NightGhastsConfig(
                true,
                2,
                20 * 20,
                1,
                48,
                128,
                12,
                32,
                176,
                16
        );
    }

    private static void write(Path path, NightGhastsConfig config) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();

            root.addProperty("enabled", config.enabled);
            root.addProperty("targetGhastsPerPlayer", config.targetGhastsPerPlayer);

            root.addProperty("spawnIntervalTicks", config.spawnIntervalTicks);
            root.addProperty("maxSpawnPerPlayerPerInterval", config.maxSpawnPerPlayerPerInterval);

            root.addProperty("minSpawnDistanceBlocks", config.minSpawnDistanceBlocks);
            root.addProperty("maxSpawnDistanceBlocks", config.maxSpawnDistanceBlocks);

            root.addProperty("spawnYOffsetMin", config.spawnYOffsetMin);
            root.addProperty("spawnYOffsetMax", config.spawnYOffsetMax);

            root.addProperty("playerScanRadiusBlocks", config.playerScanRadiusBlocks);

            root.addProperty("spawnAttemptsPerInterval", config.spawnAttemptsPerInterval);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] wrote config: {}", path);
            }
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][nightghasts] failed to write config: {}", path);
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
