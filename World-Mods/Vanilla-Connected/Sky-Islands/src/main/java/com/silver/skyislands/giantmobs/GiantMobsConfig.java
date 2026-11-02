package com.silver.skyislands.giantmobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings for the stationary sky-island giants. Unknown keys are dropped and missing keys take their
 * default, so the file is rewritten complete on every load.
 */
public final class GiantMobsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(GiantMobsConfig.class);

    // Population and placement
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

    // Combat shared by every attack
    public final double maxHealth;
    public final int attackRangeBlocks;
    /** Minimum quiet time between the end of one attack and the start of the next. */
    public final int attackGapTicks;
    public final float projectileImpactDamage;
    public final float rainDamage;
    public final double projectileKnockbackStrength;
    /** Below this fraction of max health a giant is enraged: shorter gaps and bigger attacks. */
    public final double enrageHealthFraction;
    public final double enrageGapScale;

    // Boulder volley (long range)
    public final int volleyBoulders;
    public final int volleyMinRangeBlocks;
    public final int volleyWindupTicks;
    public final int volleyCooldownTicks;
    public final double volleySpreadBlocks;
    /** 0 aims at the target's current position, 1 fully leads their movement. Below 1 stays dodgeable. */
    public final double volleyLeadFactor;

    // Ground slam (close range, jump to avoid)
    public final int slamRadiusBlocks;
    public final int slamWindupTicks;
    public final int slamCooldownTicks;
    public final float slamDamage;

    // Rock rain (punishes standing still)
    public final int rainBoulders;
    public final int rainRadiusBlocks;
    public final int rainWindupTicks;
    public final int rainCooldownTicks;
    public final int rainStillTicks;

    // Chunk handling
    public final boolean forceChunkLoadingEnabled;
    public final int preloadRadiusChunks;
    public final int preloadTicketLevel;
    public final int maxChunkLoadsPerTick;
    public final int releaseTicketsAfterTicks;
    public final int virtualStateFlushIntervalMinutes;

    private GiantMobsConfig(JsonObject root) {
        enabled = getBool(root, "enabled", true);
        minimumGiants = Math.max(1, getInt(root, "minimumGiants", 4));
        activationRadiusBlocks = Math.max(64, getInt(root, "activationRadiusBlocks", 192));
        despawnRadiusBlocks = Math.max(activationRadiusBlocks + 64, getInt(root, "despawnRadiusBlocks", 320));
        int minSpawn = Math.max(0, getInt(root, "minSpawnDistanceBlocks", 96));
        minSpawnDistanceBlocks = minSpawn;
        maxSpawnDistanceBlocks = Math.max(minSpawn + 16, getInt(root, "maxSpawnDistanceBlocks", 640));
        spawnHeightAboveGround = Math.max(1, getInt(root, "spawnHeightAboveGround", 1));
        spawnSearchAttempts = Math.max(1, getInt(root, "spawnSearchAttempts", 10));
        groundSearchHorizontalRadiusBlocks = Math.max(0, getInt(root, "groundSearchHorizontalRadiusBlocks", 32));
        groundSearchVerticalRangeBlocks = Math.max(16, getInt(root, "groundSearchVerticalRangeBlocks", 96));
        groundSearchCooldownTicks = Math.max(1, getInt(root, "groundSearchCooldownTicks", 40));
        groundCacheTtlTicks = Math.max(20, getInt(root, "groundCacheTtlTicks", 20 * 60));

        maxHealth = Math.max(1.0, getDouble(root, "maxHealth", 100.0));
        attackRangeBlocks = Math.max(16, getInt(root, "attackRangeBlocks", 56));
        attackGapTicks = Math.max(10, getInt(root, "attackGapTicks", 40));
        projectileImpactDamage = (float) Math.max(0.0, getDouble(root, "projectileImpactDamage", 14.0));
        rainDamage = (float) Math.max(0.0, getDouble(root, "rainDamage", 12.0));
        projectileKnockbackStrength = Math.max(0.0, getDouble(root, "projectileKnockbackStrength", 1.4));
        enrageHealthFraction = clamp(getDouble(root, "enrageHealthFraction", 0.5), 0.0, 1.0);
        enrageGapScale = clamp(getDouble(root, "enrageGapScale", 0.65), 0.2, 1.0);

        volleyBoulders = clamp(getInt(root, "volleyBoulders", 6), 1, 24);
        volleyMinRangeBlocks = Math.max(0, getInt(root, "volleyMinRangeBlocks", 14));
        volleyWindupTicks = Math.max(10, getInt(root, "volleyWindupTicks", 26));
        volleyCooldownTicks = Math.max(20, getInt(root, "volleyCooldownTicks", 90));
        volleySpreadBlocks = Math.max(0.0, getDouble(root, "volleySpreadBlocks", 2.4));
        volleyLeadFactor = clamp(getDouble(root, "volleyLeadFactor", 0.6), 0.0, 1.0);

        slamRadiusBlocks = Math.max(4, getInt(root, "slamRadiusBlocks", 13));
        slamWindupTicks = Math.max(10, getInt(root, "slamWindupTicks", 30));
        slamCooldownTicks = Math.max(20, getInt(root, "slamCooldownTicks", 110));
        slamDamage = (float) Math.max(0.0, getDouble(root, "slamDamage", 18.0));

        rainBoulders = clamp(getInt(root, "rainBoulders", 8), 1, 32);
        rainRadiusBlocks = Math.max(2, getInt(root, "rainRadiusBlocks", 6));
        rainWindupTicks = Math.max(10, getInt(root, "rainWindupTicks", 30));
        rainCooldownTicks = Math.max(20, getInt(root, "rainCooldownTicks", 160));
        rainStillTicks = Math.max(20, getInt(root, "rainStillTicks", 60));

        forceChunkLoadingEnabled = getBool(root, "forceChunkLoadingEnabled", true);
        preloadRadiusChunks = Math.max(0, getInt(root, "preloadRadiusChunks", 3));
        preloadTicketLevel = clamp(getInt(root, "preloadTicketLevel", 2), 1, 33);
        maxChunkLoadsPerTick = Math.max(0, getInt(root, "maxChunkLoadsPerTick", 4));
        releaseTicketsAfterTicks = Math.max(20, getInt(root, "releaseTicketsAfterTicks", 20 * 30));
        virtualStateFlushIntervalMinutes = Math.max(0, getInt(root, "virtualStateFlushIntervalMinutes", 5));
    }

    public static GiantMobsConfig loadOrCreate() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("sky-islands-giantmobs.json");
        JsonObject root = new JsonObject();
        if (Files.exists(configPath)) {
            try {
                root = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | IllegalStateException | JsonParseException e) {
                LOGGER.warn("[Sky-Islands][giants] failed to read config; using defaults. path={} err={}", configPath, e.toString());
            }
        }
        GiantMobsConfig config = new GiantMobsConfig(root);
        config.write(configPath);
        return config;
    }

    private void write(Path path) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("minimumGiants", minimumGiants);
        root.addProperty("activationRadiusBlocks", activationRadiusBlocks);
        root.addProperty("despawnRadiusBlocks", despawnRadiusBlocks);
        root.addProperty("minSpawnDistanceBlocks", minSpawnDistanceBlocks);
        root.addProperty("maxSpawnDistanceBlocks", maxSpawnDistanceBlocks);
        root.addProperty("spawnHeightAboveGround", spawnHeightAboveGround);
        root.addProperty("spawnSearchAttempts", spawnSearchAttempts);
        root.addProperty("groundSearchHorizontalRadiusBlocks", groundSearchHorizontalRadiusBlocks);
        root.addProperty("groundSearchVerticalRangeBlocks", groundSearchVerticalRangeBlocks);
        root.addProperty("groundSearchCooldownTicks", groundSearchCooldownTicks);
        root.addProperty("groundCacheTtlTicks", groundCacheTtlTicks);
        root.addProperty("maxHealth", maxHealth);
        root.addProperty("attackRangeBlocks", attackRangeBlocks);
        root.addProperty("attackGapTicks", attackGapTicks);
        root.addProperty("projectileImpactDamage", projectileImpactDamage);
        root.addProperty("rainDamage", rainDamage);
        root.addProperty("projectileKnockbackStrength", projectileKnockbackStrength);
        root.addProperty("enrageHealthFraction", enrageHealthFraction);
        root.addProperty("enrageGapScale", enrageGapScale);
        root.addProperty("volleyBoulders", volleyBoulders);
        root.addProperty("volleyMinRangeBlocks", volleyMinRangeBlocks);
        root.addProperty("volleyWindupTicks", volleyWindupTicks);
        root.addProperty("volleyCooldownTicks", volleyCooldownTicks);
        root.addProperty("volleySpreadBlocks", volleySpreadBlocks);
        root.addProperty("volleyLeadFactor", volleyLeadFactor);
        root.addProperty("slamRadiusBlocks", slamRadiusBlocks);
        root.addProperty("slamWindupTicks", slamWindupTicks);
        root.addProperty("slamCooldownTicks", slamCooldownTicks);
        root.addProperty("slamDamage", slamDamage);
        root.addProperty("rainBoulders", rainBoulders);
        root.addProperty("rainRadiusBlocks", rainRadiusBlocks);
        root.addProperty("rainWindupTicks", rainWindupTicks);
        root.addProperty("rainCooldownTicks", rainCooldownTicks);
        root.addProperty("rainStillTicks", rainStillTicks);
        root.addProperty("forceChunkLoadingEnabled", forceChunkLoadingEnabled);
        root.addProperty("preloadRadiusChunks", preloadRadiusChunks);
        root.addProperty("preloadTicketLevel", preloadTicketLevel);
        root.addProperty("maxChunkLoadsPerTick", maxChunkLoadsPerTick);
        root.addProperty("releaseTicketsAfterTicks", releaseTicketsAfterTicks);
        root.addProperty("virtualStateFlushIntervalMinutes", virtualStateFlushIntervalMinutes);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[Sky-Islands][giants] failed to write config path={} err={}", path, e.toString());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int getInt(JsonObject obj, String key, int def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
}
