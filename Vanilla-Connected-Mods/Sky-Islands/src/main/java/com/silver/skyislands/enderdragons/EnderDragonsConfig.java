package com.silver.skyislands.enderdragons;

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

public final class EnderDragonsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderDragonsConfig.class);

    public final int minimumDragons;
    public final int roamIntervalTicks;
    public final int roamMinDistanceBlocks;
    public final int roamMaxDistanceBlocks;
    public final int spawnY;
    public final int spawnYRandomRange;

    public final boolean virtualTravelEnabled;
    public final int activationRadiusBlocks;
    public final int despawnRadiusBlocks;
    public final double virtualSpeedBlocksPerTick;
    public final boolean directionJitterEnabled;
    public final int directionChangeIntervalTicks;

    public final boolean autoDistancesFromServer;

    public final boolean forceChunkLoadingEnabled;
    public final int minSpawnDistanceBlocks;
    public final int preloadRadiusChunks;
    public final int preloadAheadChunks;
    public final int preloadTicketLevel;
    public final int maxChunkLoadsPerTick;
    public final int releaseTicketsAfterTicks;

    public final int virtualStateFlushIntervalMinutes;

    public final boolean headFearEnabled;
    public final int headSearchRadiusBlocks;
    public final int headScanAheadBlocks;
    public final int headScanIntervalTicks;
    public final int headOrbitRadiusBlocks;
    public final double headOrbitAngularSpeedRadiansPerTick;
    public final int headOrbitYAboveHeadBlocks;
    public final int headAvoidSpawnBufferBlocks;
    public final int headOrbitSwitchCooldownTicks;

    private EnderDragonsConfig(int minimumDragons,
                              int roamIntervalTicks,
                              int roamMinDistanceBlocks,
                              int roamMaxDistanceBlocks,
                              int spawnY,
                              int spawnYRandomRange,
                              boolean virtualTravelEnabled,
                              int activationRadiusBlocks,
                              int despawnRadiusBlocks,
                              double virtualSpeedBlocksPerTick,
                              boolean directionJitterEnabled,
                              int directionChangeIntervalTicks,
                              boolean autoDistancesFromServer,
                              boolean forceChunkLoadingEnabled,
                              int minSpawnDistanceBlocks,
                              int preloadRadiusChunks,
                              int preloadAheadChunks,
                              int preloadTicketLevel,
                              int maxChunkLoadsPerTick,
                              int releaseTicketsAfterTicks,
                              int virtualStateFlushIntervalMinutes,
                              boolean headFearEnabled,
                              int headSearchRadiusBlocks,
                              int headScanAheadBlocks,
                              int headScanIntervalTicks,
                              int headOrbitRadiusBlocks,
                              double headOrbitAngularSpeedRadiansPerTick,
                              int headOrbitYAboveHeadBlocks,
                              int headAvoidSpawnBufferBlocks,
                              int headOrbitSwitchCooldownTicks) {
        this.minimumDragons = Math.max(5, minimumDragons);
        this.roamIntervalTicks = Math.max(20, roamIntervalTicks);
        this.roamMinDistanceBlocks = Math.max(64, roamMinDistanceBlocks);
        this.roamMaxDistanceBlocks = Math.max(this.roamMinDistanceBlocks, roamMaxDistanceBlocks);
        this.spawnY = clamp(spawnY, -64, 512);
        this.spawnYRandomRange = Math.max(0, spawnYRandomRange);

        this.virtualTravelEnabled = virtualTravelEnabled;
        this.activationRadiusBlocks = Math.max(64, activationRadiusBlocks);
        this.despawnRadiusBlocks = Math.max(this.activationRadiusBlocks + 64, despawnRadiusBlocks);
        this.virtualSpeedBlocksPerTick = Math.max(0.01, virtualSpeedBlocksPerTick);
        this.directionJitterEnabled = directionJitterEnabled;
        this.directionChangeIntervalTicks = Math.max(20, directionChangeIntervalTicks);

        this.autoDistancesFromServer = autoDistancesFromServer;

        this.forceChunkLoadingEnabled = forceChunkLoadingEnabled;
        this.minSpawnDistanceBlocks = Math.max(0, minSpawnDistanceBlocks);
        this.preloadRadiusChunks = Math.max(0, preloadRadiusChunks);
        this.preloadAheadChunks = Math.max(0, preloadAheadChunks);
        this.preloadTicketLevel = clamp(preloadTicketLevel, 1, 33);
        this.maxChunkLoadsPerTick = Math.max(0, maxChunkLoadsPerTick);
        this.releaseTicketsAfterTicks = Math.max(20, releaseTicketsAfterTicks);

        this.virtualStateFlushIntervalMinutes = Math.max(0, virtualStateFlushIntervalMinutes);

        this.headFearEnabled = headFearEnabled;
        this.headSearchRadiusBlocks = Math.max(8, headSearchRadiusBlocks);
        this.headScanAheadBlocks = Math.max(0, headScanAheadBlocks);
        this.headScanIntervalTicks = Math.max(5, headScanIntervalTicks);
        this.headOrbitRadiusBlocks = Math.max(8, headOrbitRadiusBlocks);
        this.headOrbitAngularSpeedRadiansPerTick = Math.max(0.001, headOrbitAngularSpeedRadiansPerTick);
        this.headOrbitYAboveHeadBlocks = Math.max(0, headOrbitYAboveHeadBlocks);
        this.headAvoidSpawnBufferBlocks = Math.max(0, headAvoidSpawnBufferBlocks);
        this.headOrbitSwitchCooldownTicks = Math.max(300, headOrbitSwitchCooldownTicks);
    }

    public static EnderDragonsConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-enderdragons.json");

        if (!Files.exists(configPath)) {
            EnderDragonsConfig defaults = defaults();
            write(configPath, defaults);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons] wrote default config: {}", configPath);
            }
            return defaults;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int minimumDragons = getInt(root, "minimumDragons", 5);
            int roamIntervalTicks = getInt(root, "roamIntervalTicks", 20 * 30);
            int roamMinDistanceBlocks = getInt(root, "roamMinDistanceBlocks", 5000);
            int roamMaxDistanceBlocks = getInt(root, "roamMaxDistanceBlocks", 30000);
            int spawnY = getInt(root, "spawnY", 160);
            int spawnYRandomRange = getInt(root, "spawnYRandomRange", 32);

            boolean virtualTravelEnabled = getBool(root, "virtualTravelEnabled", true);
            int activationRadiusBlocks = getInt(root, "activationRadiusBlocks", 256);
            int despawnRadiusBlocks = getInt(root, "despawnRadiusBlocks", 384);
            double virtualSpeedBlocksPerTick = getDouble(root, "virtualSpeedBlocksPerTick", 0.6);
            boolean directionJitterEnabled = getBool(root, "directionJitterEnabled", false);
            int directionChangeIntervalTicks = getInt(root, "directionChangeIntervalTicks", 20 * 20);

            boolean autoDistancesFromServer = getBool(root, "autoDistancesFromServer", false);

            boolean forceChunkLoadingEnabled = getBool(root, "forceChunkLoadingEnabled", true);
            int minSpawnDistanceBlocks = getInt(root, "minSpawnDistanceBlocks", 256);
            int preloadRadiusChunks = getInt(root, "preloadRadiusChunks", 2);
            int preloadAheadChunks = getInt(root, "preloadAheadChunks", 8);
            int preloadTicketLevel = getInt(root, "preloadTicketLevel", 2);
            int maxChunkLoadsPerTick = getInt(root, "maxChunkLoadsPerTick", 4);
            int releaseTicketsAfterTicks = getInt(root, "releaseTicketsAfterTicks", 20 * 30);

            int virtualStateFlushIntervalMinutes = getInt(root, "virtualStateFlushIntervalMinutes", 5);

            boolean headFearEnabled = getBool(root, "headFearEnabled", true);
            int headSearchRadiusBlocks = getInt(root, "headSearchRadiusBlocks", 75);
            // If not specified, default vertical search to match horizontal radius.
            int headScanAheadBlocks = getInt(root, "headScanAheadBlocks", 160);
            int headScanIntervalTicks = getInt(root, "headScanIntervalTicks", 60);
            int headOrbitRadiusBlocks = getInt(root, "headOrbitRadiusBlocks", 72);
            double headOrbitAngularSpeedRadiansPerTick = getDouble(root, "headOrbitAngularSpeedRadiansPerTick", 0.06);
            int headOrbitYAboveHeadBlocks = getInt(root, "headOrbitYAboveHeadBlocks", 16);
            int headAvoidSpawnBufferBlocks = getInt(root, "headAvoidSpawnBufferBlocks", 8);
            int headOrbitSwitchCooldownTicks = getInt(root, "headOrbitSwitchCooldownTicks", 20 * 10);

            EnderDragonsConfig parsed = new EnderDragonsConfig(
                    minimumDragons,
                    roamIntervalTicks,
                    roamMinDistanceBlocks,
                    roamMaxDistanceBlocks,
                    spawnY,
                    spawnYRandomRange,
                    virtualTravelEnabled,
                    activationRadiusBlocks,
                    despawnRadiusBlocks,
                    virtualSpeedBlocksPerTick,
                    directionJitterEnabled,
                    directionChangeIntervalTicks,
                    autoDistancesFromServer,
                    forceChunkLoadingEnabled,
                    minSpawnDistanceBlocks,
                    preloadRadiusChunks,
                    preloadAheadChunks,
                    preloadTicketLevel,
                    maxChunkLoadsPerTick,
                    releaseTicketsAfterTicks,
                        virtualStateFlushIntervalMinutes,
                        headFearEnabled,
                        headSearchRadiusBlocks,
                            headScanAheadBlocks,
                        headScanIntervalTicks,
                        headOrbitRadiusBlocks,
                        headOrbitAngularSpeedRadiansPerTick,
                        headOrbitYAboveHeadBlocks,
                        headAvoidSpawnBufferBlocks,
                        headOrbitSwitchCooldownTicks
            );

            // Rewrite with normalized values if file had invalid entries.
            write(configPath, parsed);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons] loaded config: {} min={} virtual={} chunkPreload={} headFear={}",
                        configPath, parsed.minimumDragons, parsed.virtualTravelEnabled, parsed.forceChunkLoadingEnabled, parsed.headFearEnabled);
            }
            return parsed;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            EnderDragonsConfig defaults = defaults();
            write(configPath, defaults);
            LOGGER.warn("[Sky-Islands][dragons] failed to read config; using defaults. path={} err={}", configPath, e.toString());
            return defaults;
        }
    }

    private static EnderDragonsConfig defaults() {
        return new EnderDragonsConfig(
                5,
                20 * 30,
                5000,
                30000,
                160,
                32,
                true,
                256,
                384,
                0.6,
                false,
                20 * 20,
                false,
                true,
                256,
                2,
                8,
                2,
                4,
                20 * 30,
                5
                ,
                true,
                48,
                160,
                60,
                72,
                0.06,
                32,
                16,
                20 * 10
        );
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

    private static double getDouble(JsonObject obj, String key, double def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
            return def;
        }
    }

    private static void write(Path path, EnderDragonsConfig config) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("minimumDragons", config.minimumDragons);
            root.addProperty("roamIntervalTicks", config.roamIntervalTicks);
            root.addProperty("roamMinDistanceBlocks", config.roamMinDistanceBlocks);
            root.addProperty("roamMaxDistanceBlocks", config.roamMaxDistanceBlocks);
            root.addProperty("spawnY", config.spawnY);
            root.addProperty("spawnYRandomRange", config.spawnYRandomRange);

            root.addProperty("virtualTravelEnabled", config.virtualTravelEnabled);
            root.addProperty("activationRadiusBlocks", config.activationRadiusBlocks);
            root.addProperty("despawnRadiusBlocks", config.despawnRadiusBlocks);
            root.addProperty("virtualSpeedBlocksPerTick", config.virtualSpeedBlocksPerTick);
            root.addProperty("directionJitterEnabled", config.directionJitterEnabled);
            root.addProperty("directionChangeIntervalTicks", config.directionChangeIntervalTicks);

            root.addProperty("autoDistancesFromServer", config.autoDistancesFromServer);

            root.addProperty("forceChunkLoadingEnabled", config.forceChunkLoadingEnabled);
            root.addProperty("minSpawnDistanceBlocks", config.minSpawnDistanceBlocks);
            root.addProperty("preloadRadiusChunks", config.preloadRadiusChunks);
            root.addProperty("preloadAheadChunks", config.preloadAheadChunks);
            root.addProperty("preloadTicketLevel", config.preloadTicketLevel);
            root.addProperty("maxChunkLoadsPerTick", config.maxChunkLoadsPerTick);
            root.addProperty("releaseTicketsAfterTicks", config.releaseTicketsAfterTicks);

            root.addProperty("virtualStateFlushIntervalMinutes", config.virtualStateFlushIntervalMinutes);

            root.addProperty("headFearEnabled", config.headFearEnabled);
            root.addProperty("headSearchRadiusBlocks", config.headSearchRadiusBlocks);
            root.addProperty("headScanAheadBlocks", config.headScanAheadBlocks);
            root.addProperty("headScanIntervalTicks", config.headScanIntervalTicks);
            root.addProperty("headOrbitRadiusBlocks", config.headOrbitRadiusBlocks);
            root.addProperty("headOrbitAngularSpeedRadiansPerTick", config.headOrbitAngularSpeedRadiansPerTick);
            root.addProperty("headOrbitYAboveHeadBlocks", config.headOrbitYAboveHeadBlocks);
            root.addProperty("headAvoidSpawnBufferBlocks", config.headAvoidSpawnBufferBlocks);
            root.addProperty("headOrbitSwitchCooldownTicks", config.headOrbitSwitchCooldownTicks);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons] wrote config: {}", path);
            }
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][dragons] failed to write config: {}", path);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
