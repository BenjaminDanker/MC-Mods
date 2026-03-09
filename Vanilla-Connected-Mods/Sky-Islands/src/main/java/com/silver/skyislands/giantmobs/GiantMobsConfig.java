package com.silver.skyislands.giantmobs;

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

public final class GiantMobsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(GiantMobsConfig.class);

    public final boolean enabled;
    public final int minimumGiants;
    public final int activationRadiusBlocks;
    public final int despawnRadiusBlocks;
    public final int minSpawnDistanceBlocks;
    public final int maxSpawnDistanceBlocks;
    public final int spawnHeightAboveGround;
    public final int spawnSearchAttempts;
    public final int groundSearchHorizontalRadiusBlocks;
    public final int groundSearchVerticalRangeBlocks;
    public final int groundSearchCooldownTicks;
    public final int groundCacheTtlTicks;
    public final int attackRangeBlocks;
    public final int attackCooldownTicks;
    public final int projectileCount;
    public final double projectileSpreadRadiusBlocks;
    public final float projectileImpactDamage;
    public final double projectileKnockbackStrength;
    public final boolean forceChunkLoadingEnabled;
    public final int preloadRadiusChunks;
    public final int preloadTicketLevel;
    public final int maxChunkLoadsPerTick;
    public final int releaseTicketsAfterTicks;
    public final int virtualStateFlushIntervalMinutes;

    private GiantMobsConfig(boolean enabled,
                            int minimumGiants,
                            int activationRadiusBlocks,
                            int despawnRadiusBlocks,
                            int minSpawnDistanceBlocks,
                            int maxSpawnDistanceBlocks,
                            int spawnHeightAboveGround,
                            int spawnSearchAttempts,
                            int groundSearchHorizontalRadiusBlocks,
                            int groundSearchVerticalRangeBlocks,
                            int groundSearchCooldownTicks,
                            int groundCacheTtlTicks,
                            int attackRangeBlocks,
                            int attackCooldownTicks,
                            int projectileCount,
                            double projectileSpreadRadiusBlocks,
                            float projectileImpactDamage,
                            double projectileKnockbackStrength,
                            boolean forceChunkLoadingEnabled,
                            int preloadRadiusChunks,
                            int preloadTicketLevel,
                            int maxChunkLoadsPerTick,
                            int releaseTicketsAfterTicks,
                            int virtualStateFlushIntervalMinutes) {
        this.enabled = enabled;
        this.minimumGiants = Math.max(1, minimumGiants);
        this.activationRadiusBlocks = Math.max(64, activationRadiusBlocks);
        this.despawnRadiusBlocks = Math.max(this.activationRadiusBlocks + 64, despawnRadiusBlocks);
        this.minSpawnDistanceBlocks = Math.max(0, minSpawnDistanceBlocks);
        this.maxSpawnDistanceBlocks = Math.max(this.minSpawnDistanceBlocks + 16, maxSpawnDistanceBlocks);
        this.spawnHeightAboveGround = Math.max(1, spawnHeightAboveGround);
        this.spawnSearchAttempts = Math.max(1, spawnSearchAttempts);
        this.groundSearchHorizontalRadiusBlocks = Math.max(0, groundSearchHorizontalRadiusBlocks);
        this.groundSearchVerticalRangeBlocks = Math.max(16, groundSearchVerticalRangeBlocks);
        this.groundSearchCooldownTicks = Math.max(1, groundSearchCooldownTicks);
        this.groundCacheTtlTicks = Math.max(20, groundCacheTtlTicks);
        this.attackRangeBlocks = Math.max(8, attackRangeBlocks);
        this.attackCooldownTicks = Math.max(10, attackCooldownTicks);
        this.projectileCount = Math.max(1, projectileCount);
        this.projectileSpreadRadiusBlocks = Math.max(0.0, projectileSpreadRadiusBlocks);
        this.projectileImpactDamage = Math.max(0.0F, projectileImpactDamage);
        this.projectileKnockbackStrength = Math.max(0.0, projectileKnockbackStrength);
        this.forceChunkLoadingEnabled = forceChunkLoadingEnabled;
        this.preloadRadiusChunks = Math.max(0, preloadRadiusChunks);
        this.preloadTicketLevel = clamp(preloadTicketLevel, 1, 33);
        this.maxChunkLoadsPerTick = Math.max(0, maxChunkLoadsPerTick);
        this.releaseTicketsAfterTicks = Math.max(20, releaseTicketsAfterTicks);
        this.virtualStateFlushIntervalMinutes = Math.max(0, virtualStateFlushIntervalMinutes);
    }

    public static GiantMobsConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-giantmobs.json");

        if (!Files.exists(configPath)) {
            GiantMobsConfig defaults = defaults();
            write(configPath, defaults);
            return defaults;
        }

        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            GiantMobsConfig parsed = new GiantMobsConfig(
                    getBool(root, "enabled", true),
                    getInt(root, "minimumGiants", 4),
                    getInt(root, "activationRadiusBlocks", 192),
                    getInt(root, "despawnRadiusBlocks", 320),
                    getInt(root, "minSpawnDistanceBlocks", 0),
                    getInt(root, "maxSpawnDistanceBlocks", 15_000),
                    getInt(root, "spawnHeightAboveGround", 1),
                    getInt(root, "spawnSearchAttempts", 10),
                    getInt(root, "groundSearchHorizontalRadiusBlocks", 32),
                    getInt(root, "groundSearchVerticalRangeBlocks", 96),
                    getInt(root, "groundSearchCooldownTicks", 40),
                    getInt(root, "groundCacheTtlTicks", 20 * 60),
                    getInt(root, "attackRangeBlocks", 192),
                    getInt(root, "attackCooldownTicks", 180),
                    getInt(root, "projectileCount", 11),
                    getDouble(root, "projectileSpreadRadiusBlocks", 1.6),
                    (float) getDouble(root, "projectileImpactDamage", 8.0),
                    getDouble(root, "projectileKnockbackStrength", 1.8),
                    getBool(root, "forceChunkLoadingEnabled", true),
                    getInt(root, "preloadRadiusChunks", 3),
                    getInt(root, "preloadTicketLevel", 2),
                    getInt(root, "maxChunkLoadsPerTick", 4),
                    getInt(root, "releaseTicketsAfterTicks", 20 * 30),
                    getInt(root, "virtualStateFlushIntervalMinutes", 5)
            );

            write(configPath, parsed);
            return parsed;
        } catch (IOException | IllegalStateException | JsonParseException e) {
            GiantMobsConfig defaults = defaults();
            write(configPath, defaults);
            LOGGER.warn("[Sky-Islands][giants] failed to read config; using defaults. path={} err={}", configPath, e.toString());
            return defaults;
        }
    }

    private static GiantMobsConfig defaults() {
        return new GiantMobsConfig(true, 4, 192, 320, 0, 20_000, 1, 10, 32, 96, 40, 20 * 60, 192, 60, 11, 1.6, 8.0F, 1.8, true, 3, 2, 4, 20 * 30, 5);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    private static void write(Path path, GiantMobsConfig config) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", config.enabled);
            root.addProperty("minimumGiants", config.minimumGiants);
            root.addProperty("activationRadiusBlocks", config.activationRadiusBlocks);
            root.addProperty("despawnRadiusBlocks", config.despawnRadiusBlocks);
            root.addProperty("minSpawnDistanceBlocks", config.minSpawnDistanceBlocks);
            root.addProperty("maxSpawnDistanceBlocks", config.maxSpawnDistanceBlocks);
            root.addProperty("spawnHeightAboveGround", config.spawnHeightAboveGround);
            root.addProperty("spawnSearchAttempts", config.spawnSearchAttempts);
            root.addProperty("groundSearchHorizontalRadiusBlocks", config.groundSearchHorizontalRadiusBlocks);
            root.addProperty("groundSearchVerticalRangeBlocks", config.groundSearchVerticalRangeBlocks);
            root.addProperty("groundSearchCooldownTicks", config.groundSearchCooldownTicks);
            root.addProperty("groundCacheTtlTicks", config.groundCacheTtlTicks);
            root.addProperty("attackRangeBlocks", config.attackRangeBlocks);
            root.addProperty("attackCooldownTicks", config.attackCooldownTicks);
            root.addProperty("projectileCount", config.projectileCount);
            root.addProperty("projectileSpreadRadiusBlocks", config.projectileSpreadRadiusBlocks);
            root.addProperty("projectileImpactDamage", config.projectileImpactDamage);
            root.addProperty("projectileKnockbackStrength", config.projectileKnockbackStrength);
            root.addProperty("forceChunkLoadingEnabled", config.forceChunkLoadingEnabled);
            root.addProperty("preloadRadiusChunks", config.preloadRadiusChunks);
            root.addProperty("preloadTicketLevel", config.preloadTicketLevel);
            root.addProperty("maxChunkLoadsPerTick", config.maxChunkLoadsPerTick);
            root.addProperty("releaseTicketsAfterTicks", config.releaseTicketsAfterTicks);
            root.addProperty("virtualStateFlushIntervalMinutes", config.virtualStateFlushIntervalMinutes);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            LOGGER.warn("[Sky-Islands][giants] failed to write config path={}", path);
        }
    }
}