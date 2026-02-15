package com.silver.atlantis.leviathan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LeviathansConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "atlantis-leviathans.json";

    public final String entityTypeId;
    public final double entityScale;
    public final int minimumLeviathans;

    public final boolean virtualTravelEnabled;
    public final int roamMinDistanceBlocks;
    public final int roamMaxDistanceBlocks;
    public final int spawnY;
    public final int spawnYRandomRange;
    public final double virtualSpeedBlocksPerTick;

    public final int activationRadiusBlocks;
    public final int despawnRadiusBlocks;
    public final int minSpawnDistanceBlocks;
    public final boolean autoDistancesFromServer;

    public final boolean combatEnabled;
    public final int engageRadiusBlocks;
    public final int disengageRadiusBlocks;
    public final int chargeDurationTicks;
    public final int chargeCooldownTicks;
    public final double chargeSpeedBlocksPerTick;
    public final double chargeMinHitSpeed;
    public final double chargeDamage;
    public final int chargeHitCooldownTicks;
    public final double passOvershootBlocks;
    public final int turnaroundTicks;
    // Max consecutive ticks with blocked line-of-engagement before disengage.
    public final int lineOfEngagementTimeoutTicks;
    // How long to circle around last known target anchor after disconnect (default 5 minutes).
    public final int disconnectLingerTicks;
    // Minimum dot(forward, toTarget) required for charge hit validation.
    public final double chargeDirectionDotThreshold;
    // Extra target hitbox expansion used in swept-volume charge collision.
    public final double chargeHitboxExpandBlocks;
    // Optional knockback magnitude applied on successful charge hit.
    public final double chargeKnockbackStrength;

    public final boolean requireWaterForSpawn;
    public final int solidAvoidanceProbeDistanceBlocks;
    public final int solidAvoidanceVerticalClearanceBlocks;
    public final double solidAvoidanceTurnStrength;
    public final int minWaterDepthBlocksForTravel;

    public final boolean forceChunkLoadingEnabled;
    public final int preloadRadiusChunks;
    public final int preloadAheadChunks;
    public final int preloadTicketLevel;
    public final int maxChunkLoadsPerTick;
    public final int releaseTicketsAfterTicks;

    public final int virtualStateFlushIntervalMinutes;

    private LeviathansConfig(
        String entityTypeId,
        double entityScale,
        int minimumLeviathans,
        boolean virtualTravelEnabled,
        int roamMinDistanceBlocks,
        int roamMaxDistanceBlocks,
        int spawnY,
        int spawnYRandomRange,
        double virtualSpeedBlocksPerTick,
        int activationRadiusBlocks,
        int despawnRadiusBlocks,
        int minSpawnDistanceBlocks,
        boolean autoDistancesFromServer,
        boolean combatEnabled,
        int engageRadiusBlocks,
        int disengageRadiusBlocks,
        int chargeDurationTicks,
        int chargeCooldownTicks,
        double chargeSpeedBlocksPerTick,
        double chargeMinHitSpeed,
        double chargeDamage,
        int chargeHitCooldownTicks,
        double passOvershootBlocks,
        int turnaroundTicks,
        int lineOfEngagementTimeoutTicks,
        int disconnectLingerTicks,
        double chargeDirectionDotThreshold,
        double chargeHitboxExpandBlocks,
        double chargeKnockbackStrength,
        boolean requireWaterForSpawn,
        int solidAvoidanceProbeDistanceBlocks,
        int solidAvoidanceVerticalClearanceBlocks,
        double solidAvoidanceTurnStrength,
        int minWaterDepthBlocksForTravel,
        boolean forceChunkLoadingEnabled,
        int preloadRadiusChunks,
        int preloadAheadChunks,
        int preloadTicketLevel,
        int maxChunkLoadsPerTick,
        int releaseTicketsAfterTicks,
        int virtualStateFlushIntervalMinutes
    ) {
        this.entityTypeId = entityTypeId;
        this.entityScale = entityScale;
        this.minimumLeviathans = minimumLeviathans;
        this.virtualTravelEnabled = virtualTravelEnabled;
        this.roamMinDistanceBlocks = roamMinDistanceBlocks;
        this.roamMaxDistanceBlocks = roamMaxDistanceBlocks;
        this.spawnY = spawnY;
        this.spawnYRandomRange = spawnYRandomRange;
        this.virtualSpeedBlocksPerTick = virtualSpeedBlocksPerTick;
        this.activationRadiusBlocks = activationRadiusBlocks;
        this.despawnRadiusBlocks = despawnRadiusBlocks;
        this.minSpawnDistanceBlocks = minSpawnDistanceBlocks;
        this.autoDistancesFromServer = autoDistancesFromServer;
        this.combatEnabled = combatEnabled;
        this.engageRadiusBlocks = engageRadiusBlocks;
        this.disengageRadiusBlocks = disengageRadiusBlocks;
        this.chargeDurationTicks = chargeDurationTicks;
        this.chargeCooldownTicks = chargeCooldownTicks;
        this.chargeSpeedBlocksPerTick = chargeSpeedBlocksPerTick;
        this.chargeMinHitSpeed = chargeMinHitSpeed;
        this.chargeDamage = chargeDamage;
        this.chargeHitCooldownTicks = chargeHitCooldownTicks;
        this.passOvershootBlocks = passOvershootBlocks;
        this.turnaroundTicks = turnaroundTicks;
        this.lineOfEngagementTimeoutTicks = lineOfEngagementTimeoutTicks;
        this.disconnectLingerTicks = disconnectLingerTicks;
        this.chargeDirectionDotThreshold = chargeDirectionDotThreshold;
        this.chargeHitboxExpandBlocks = chargeHitboxExpandBlocks;
        this.chargeKnockbackStrength = chargeKnockbackStrength;
        this.requireWaterForSpawn = requireWaterForSpawn;
        this.solidAvoidanceProbeDistanceBlocks = solidAvoidanceProbeDistanceBlocks;
        this.solidAvoidanceVerticalClearanceBlocks = solidAvoidanceVerticalClearanceBlocks;
        this.solidAvoidanceTurnStrength = solidAvoidanceTurnStrength;
        this.minWaterDepthBlocksForTravel = minWaterDepthBlocksForTravel;
        this.forceChunkLoadingEnabled = forceChunkLoadingEnabled;
        this.preloadRadiusChunks = preloadRadiusChunks;
        this.preloadAheadChunks = preloadAheadChunks;
        this.preloadTicketLevel = preloadTicketLevel;
        this.maxChunkLoadsPerTick = maxChunkLoadsPerTick;
        this.releaseTicketsAfterTicks = releaseTicketsAfterTicks;
        this.virtualStateFlushIntervalMinutes = virtualStateFlushIntervalMinutes;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static LeviathansConfig loadOrCreateStrict() {
        Path path = configPath();
        if (!Files.exists(path)) {
            write(path, defaults());
        }
        return loadStrict(path);
    }

    public static LeviathansConfig loadStrict(Path path) {
        JsonObject root;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new ValidationException(List.of("io: unable to read config path=" + path + " error=" + e.getMessage()));
        } catch (IllegalStateException | JsonParseException e) {
            throw new ValidationException(List.of("json: invalid JSON object in " + path + " error=" + e.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        enforceKnownKeys(root, errors);

        String entityTypeId = requireString(root, "entityTypeId", errors);
        Double entityScale = requireDouble(root, "entityScale", errors);
        Integer minimumLeviathans = requireInt(root, "minimumLeviathans", errors);

        Boolean virtualTravelEnabled = requireBoolean(root, "virtualTravelEnabled", errors);
        Integer roamMinDistanceBlocks = requireInt(root, "roamMinDistanceBlocks", errors);
        Integer roamMaxDistanceBlocks = requireInt(root, "roamMaxDistanceBlocks", errors);
        Integer spawnY = requireInt(root, "spawnY", errors);
        Integer spawnYRandomRange = requireInt(root, "spawnYRandomRange", errors);
        Double virtualSpeedBlocksPerTick = requireDouble(root, "virtualSpeedBlocksPerTick", errors);

        Integer activationRadiusBlocks = requireInt(root, "activationRadiusBlocks", errors);
        Integer despawnRadiusBlocks = requireInt(root, "despawnRadiusBlocks", errors);
        Integer minSpawnDistanceBlocks = requireInt(root, "minSpawnDistanceBlocks", errors);
        Boolean autoDistancesFromServer = requireBoolean(root, "autoDistancesFromServer", errors);

        Boolean combatEnabled = requireBoolean(root, "combatEnabled", errors);
        Integer engageRadiusBlocks = requireInt(root, "engageRadiusBlocks", errors);
        Integer disengageRadiusBlocks = requireInt(root, "disengageRadiusBlocks", errors);
        Integer chargeDurationTicks = requireInt(root, "chargeDurationTicks", errors);
        Integer chargeCooldownTicks = requireInt(root, "chargeCooldownTicks", errors);
        Double chargeSpeedBlocksPerTick = requireDouble(root, "chargeSpeedBlocksPerTick", errors);
        Double chargeMinHitSpeed = requireDouble(root, "chargeMinHitSpeed", errors);
        Double chargeDamage = requireDouble(root, "chargeDamage", errors);
        Integer chargeHitCooldownTicks = requireInt(root, "chargeHitCooldownTicks", errors);
        Double passOvershootBlocks = requireDouble(root, "passOvershootBlocks", errors);
        Integer turnaroundTicks = requireInt(root, "turnaroundTicks", errors);
        Integer lineOfEngagementTimeoutTicks = requireInt(root, "lineOfEngagementTimeoutTicks", errors);
        Integer disconnectLingerTicks = requireInt(root, "disconnectLingerTicks", errors);
        Double chargeDirectionDotThreshold = requireDouble(root, "chargeDirectionDotThreshold", errors);
        Double chargeHitboxExpandBlocks = requireDouble(root, "chargeHitboxExpandBlocks", errors);
        Double chargeKnockbackStrength = requireDouble(root, "chargeKnockbackStrength", errors);

        Boolean requireWaterForSpawn = requireBoolean(root, "requireWaterForSpawn", errors);
        Integer solidAvoidanceProbeDistanceBlocks = requireInt(root, "solidAvoidanceProbeDistanceBlocks", errors);
        Integer solidAvoidanceVerticalClearanceBlocks = requireInt(root, "solidAvoidanceVerticalClearanceBlocks", errors);
        Double solidAvoidanceTurnStrength = requireDouble(root, "solidAvoidanceTurnStrength", errors);
        Integer minWaterDepthBlocksForTravel = requireInt(root, "minWaterDepthBlocksForTravel", errors);

        Boolean forceChunkLoadingEnabled = requireBoolean(root, "forceChunkLoadingEnabled", errors);
        Integer preloadRadiusChunks = requireInt(root, "preloadRadiusChunks", errors);
        Integer preloadAheadChunks = requireInt(root, "preloadAheadChunks", errors);
        Integer preloadTicketLevel = requireInt(root, "preloadTicketLevel", errors);
        Integer maxChunkLoadsPerTick = requireInt(root, "maxChunkLoadsPerTick", errors);
        Integer releaseTicketsAfterTicks = requireInt(root, "releaseTicketsAfterTicks", errors);

        Integer virtualStateFlushIntervalMinutes = requireInt(root, "virtualStateFlushIntervalMinutes", errors);

        if (entityScale != null && entityScale <= 0.0d) {
            errors.add("entityScale must be > 0 (was " + entityScale + ")");
        }
        if (minimumLeviathans != null && minimumLeviathans < 1) {
            errors.add("minimumLeviathans must be >= 1 (was " + minimumLeviathans + ")");
        }
        if (roamMinDistanceBlocks != null && roamMinDistanceBlocks < 1) {
            errors.add("roamMinDistanceBlocks must be >= 1 (was " + roamMinDistanceBlocks + ")");
        }
        if (roamMaxDistanceBlocks != null && roamMaxDistanceBlocks < 1) {
            errors.add("roamMaxDistanceBlocks must be >= 1 (was " + roamMaxDistanceBlocks + ")");
        }
        if (roamMinDistanceBlocks != null && roamMaxDistanceBlocks != null && roamMaxDistanceBlocks < roamMinDistanceBlocks) {
            errors.add("roamMaxDistanceBlocks must be >= roamMinDistanceBlocks (was " + roamMaxDistanceBlocks + " < " + roamMinDistanceBlocks + ")");
        }
        if (spawnY != null && (spawnY < -64 || spawnY > 512)) {
            errors.add("spawnY must be in [-64, 512] (was " + spawnY + ")");
        }
        if (spawnYRandomRange != null && spawnYRandomRange < 0) {
            errors.add("spawnYRandomRange must be >= 0 (was " + spawnYRandomRange + ")");
        }
        if (virtualSpeedBlocksPerTick != null && virtualSpeedBlocksPerTick <= 0.0d) {
            errors.add("virtualSpeedBlocksPerTick must be > 0 (was " + virtualSpeedBlocksPerTick + ")");
        }
        if (activationRadiusBlocks != null && activationRadiusBlocks < 1) {
            errors.add("activationRadiusBlocks must be >= 1 (was " + activationRadiusBlocks + ")");
        }
        if (despawnRadiusBlocks != null && despawnRadiusBlocks < 1) {
            errors.add("despawnRadiusBlocks must be >= 1 (was " + despawnRadiusBlocks + ")");
        }
        if (activationRadiusBlocks != null && despawnRadiusBlocks != null && despawnRadiusBlocks <= activationRadiusBlocks) {
            errors.add("despawnRadiusBlocks must be > activationRadiusBlocks (was " + despawnRadiusBlocks + " <= " + activationRadiusBlocks + ")");
        }
        if (minSpawnDistanceBlocks != null && minSpawnDistanceBlocks < 0) {
            errors.add("minSpawnDistanceBlocks must be >= 0 (was " + minSpawnDistanceBlocks + ")");
        }
        if (engageRadiusBlocks != null && engageRadiusBlocks < 1) {
            errors.add("engageRadiusBlocks must be >= 1 (was " + engageRadiusBlocks + ")");
        }
        if (disengageRadiusBlocks != null && disengageRadiusBlocks < 1) {
            errors.add("disengageRadiusBlocks must be >= 1 (was " + disengageRadiusBlocks + ")");
        }
        if (engageRadiusBlocks != null && disengageRadiusBlocks != null && disengageRadiusBlocks < engageRadiusBlocks) {
            errors.add("disengageRadiusBlocks must be >= engageRadiusBlocks (was " + disengageRadiusBlocks + " < " + engageRadiusBlocks + ")");
        }
        if (chargeDurationTicks != null && chargeDurationTicks < 1) {
            errors.add("chargeDurationTicks must be >= 1 (was " + chargeDurationTicks + ")");
        }
        if (chargeCooldownTicks != null && chargeCooldownTicks < 0) {
            errors.add("chargeCooldownTicks must be >= 0 (was " + chargeCooldownTicks + ")");
        }
        if (chargeSpeedBlocksPerTick != null && chargeSpeedBlocksPerTick <= 0.0d) {
            errors.add("chargeSpeedBlocksPerTick must be > 0 (was " + chargeSpeedBlocksPerTick + ")");
        }
        if (chargeMinHitSpeed != null && chargeMinHitSpeed <= 0.0d) {
            errors.add("chargeMinHitSpeed must be > 0 (was " + chargeMinHitSpeed + ")");
        }
        if (chargeSpeedBlocksPerTick != null && chargeMinHitSpeed != null && chargeMinHitSpeed > chargeSpeedBlocksPerTick) {
            errors.add("chargeMinHitSpeed must be <= chargeSpeedBlocksPerTick (was " + chargeMinHitSpeed + " > " + chargeSpeedBlocksPerTick + ")");
        }
        if (chargeDamage != null && chargeDamage <= 0.0d) {
            errors.add("chargeDamage must be > 0 (was " + chargeDamage + ")");
        }
        if (chargeHitCooldownTicks != null && chargeHitCooldownTicks < 0) {
            errors.add("chargeHitCooldownTicks must be >= 0 (was " + chargeHitCooldownTicks + ")");
        }
        if (passOvershootBlocks != null && passOvershootBlocks < 0.0d) {
            errors.add("passOvershootBlocks must be >= 0 (was " + passOvershootBlocks + ")");
        }
        if (turnaroundTicks != null && turnaroundTicks < 1) {
            errors.add("turnaroundTicks must be >= 1 (was " + turnaroundTicks + ")");
        }
        if (lineOfEngagementTimeoutTicks != null && lineOfEngagementTimeoutTicks < 1) {
            errors.add("lineOfEngagementTimeoutTicks must be >= 1 (was " + lineOfEngagementTimeoutTicks + ")");
        }
        if (disconnectLingerTicks != null && disconnectLingerTicks < 1) {
            errors.add("disconnectLingerTicks must be >= 1 (was " + disconnectLingerTicks + ")");
        }
        if (chargeDirectionDotThreshold != null && (chargeDirectionDotThreshold <= 0.0d || chargeDirectionDotThreshold > 1.0d)) {
            errors.add("chargeDirectionDotThreshold must be in (0, 1] (was " + chargeDirectionDotThreshold + ")");
        }
        if (chargeHitboxExpandBlocks != null && chargeHitboxExpandBlocks < 0.0d) {
            errors.add("chargeHitboxExpandBlocks must be >= 0 (was " + chargeHitboxExpandBlocks + ")");
        }
        if (chargeKnockbackStrength != null && chargeKnockbackStrength < 0.0d) {
            errors.add("chargeKnockbackStrength must be >= 0 (was " + chargeKnockbackStrength + ")");
        }
        if (solidAvoidanceProbeDistanceBlocks != null && solidAvoidanceProbeDistanceBlocks < 1) {
            errors.add("solidAvoidanceProbeDistanceBlocks must be >= 1 (was " + solidAvoidanceProbeDistanceBlocks + ")");
        }
        if (solidAvoidanceVerticalClearanceBlocks != null && solidAvoidanceVerticalClearanceBlocks < 0) {
            errors.add("solidAvoidanceVerticalClearanceBlocks must be >= 0 (was " + solidAvoidanceVerticalClearanceBlocks + ")");
        }
        if (solidAvoidanceTurnStrength != null && solidAvoidanceTurnStrength <= 0.0d) {
            errors.add("solidAvoidanceTurnStrength must be > 0 (was " + solidAvoidanceTurnStrength + ")");
        }
        if (minWaterDepthBlocksForTravel != null && minWaterDepthBlocksForTravel < 1) {
            errors.add("minWaterDepthBlocksForTravel must be >= 1 (was " + minWaterDepthBlocksForTravel + ")");
        }
        if (preloadRadiusChunks != null && preloadRadiusChunks < 0) {
            errors.add("preloadRadiusChunks must be >= 0 (was " + preloadRadiusChunks + ")");
        }
        if (preloadAheadChunks != null && preloadAheadChunks < 0) {
            errors.add("preloadAheadChunks must be >= 0 (was " + preloadAheadChunks + ")");
        }
        if (preloadTicketLevel != null && (preloadTicketLevel < 1 || preloadTicketLevel > 33)) {
            errors.add("preloadTicketLevel must be in [1, 33] (was " + preloadTicketLevel + ")");
        }
        if (maxChunkLoadsPerTick != null && maxChunkLoadsPerTick < 0) {
            errors.add("maxChunkLoadsPerTick must be >= 0 (was " + maxChunkLoadsPerTick + ")");
        }
        if (releaseTicketsAfterTicks != null && releaseTicketsAfterTicks < 1) {
            errors.add("releaseTicketsAfterTicks must be >= 1 (was " + releaseTicketsAfterTicks + ")");
        }
        if (virtualStateFlushIntervalMinutes != null && virtualStateFlushIntervalMinutes <= 0) {
            errors.add("virtualStateFlushIntervalMinutes must be > 0 (was " + virtualStateFlushIntervalMinutes + ")");
        }

        if (entityTypeId != null) {
            validateEntityType(entityTypeId, errors);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return new LeviathansConfig(
            entityTypeId,
            entityScale,
            minimumLeviathans,
            virtualTravelEnabled,
            roamMinDistanceBlocks,
            roamMaxDistanceBlocks,
            spawnY,
            spawnYRandomRange,
            virtualSpeedBlocksPerTick,
            activationRadiusBlocks,
            despawnRadiusBlocks,
            minSpawnDistanceBlocks,
            autoDistancesFromServer,
            combatEnabled,
            engageRadiusBlocks,
            disengageRadiusBlocks,
            chargeDurationTicks,
            chargeCooldownTicks,
            chargeSpeedBlocksPerTick,
            chargeMinHitSpeed,
            chargeDamage,
            chargeHitCooldownTicks,
            passOvershootBlocks,
            turnaroundTicks,
            lineOfEngagementTimeoutTicks,
            disconnectLingerTicks,
            chargeDirectionDotThreshold,
            chargeHitboxExpandBlocks,
            chargeKnockbackStrength,
            requireWaterForSpawn,
            solidAvoidanceProbeDistanceBlocks,
            solidAvoidanceVerticalClearanceBlocks,
            solidAvoidanceTurnStrength,
            minWaterDepthBlocksForTravel,
            forceChunkLoadingEnabled,
            preloadRadiusChunks,
            preloadAheadChunks,
            preloadTicketLevel,
            maxChunkLoadsPerTick,
            releaseTicketsAfterTicks,
            virtualStateFlushIntervalMinutes
        );
    }

    public static LeviathansConfig defaults() {
        return new LeviathansConfig(
            "minecraft:salmon",
            8.0d,
            5,
            true,
            5000,
            30000,
            90,
            24,
            0.45d,
            256,
            384,
            256,
            true,
            true,
            120,
            192,
            40,
            30,
            1.25d,
            0.9d,
            16.0d,
            8,
            24.0d,
            40,
            200,
            20 * 60 * 5,
            0.45d,
            0.75d,
            1.0d,
            true,
            12,
            16,
            0.35d,
            2,
            true,
            2,
            8,
            2,
            4,
            600,
            5
        );
    }

    public static void write(Path path, LeviathansConfig config) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("entityTypeId", config.entityTypeId);
            root.addProperty("entityScale", config.entityScale);
            root.addProperty("minimumLeviathans", config.minimumLeviathans);

            root.addProperty("virtualTravelEnabled", config.virtualTravelEnabled);
            root.addProperty("roamMinDistanceBlocks", config.roamMinDistanceBlocks);
            root.addProperty("roamMaxDistanceBlocks", config.roamMaxDistanceBlocks);
            root.addProperty("spawnY", config.spawnY);
            root.addProperty("spawnYRandomRange", config.spawnYRandomRange);
            root.addProperty("virtualSpeedBlocksPerTick", config.virtualSpeedBlocksPerTick);

            root.addProperty("activationRadiusBlocks", config.activationRadiusBlocks);
            root.addProperty("despawnRadiusBlocks", config.despawnRadiusBlocks);
            root.addProperty("minSpawnDistanceBlocks", config.minSpawnDistanceBlocks);
            root.addProperty("autoDistancesFromServer", config.autoDistancesFromServer);

            root.addProperty("combatEnabled", config.combatEnabled);
            root.addProperty("engageRadiusBlocks", config.engageRadiusBlocks);
            root.addProperty("disengageRadiusBlocks", config.disengageRadiusBlocks);
            root.addProperty("chargeDurationTicks", config.chargeDurationTicks);
            root.addProperty("chargeCooldownTicks", config.chargeCooldownTicks);
            root.addProperty("chargeSpeedBlocksPerTick", config.chargeSpeedBlocksPerTick);
            root.addProperty("chargeMinHitSpeed", config.chargeMinHitSpeed);
            root.addProperty("chargeDamage", config.chargeDamage);
            root.addProperty("chargeHitCooldownTicks", config.chargeHitCooldownTicks);
            root.addProperty("passOvershootBlocks", config.passOvershootBlocks);
            root.addProperty("turnaroundTicks", config.turnaroundTicks);
            root.addProperty("lineOfEngagementTimeoutTicks", config.lineOfEngagementTimeoutTicks);
            root.addProperty("disconnectLingerTicks", config.disconnectLingerTicks);
            root.addProperty("chargeDirectionDotThreshold", config.chargeDirectionDotThreshold);
            root.addProperty("chargeHitboxExpandBlocks", config.chargeHitboxExpandBlocks);
            root.addProperty("chargeKnockbackStrength", config.chargeKnockbackStrength);

            root.addProperty("requireWaterForSpawn", config.requireWaterForSpawn);
            root.addProperty("solidAvoidanceProbeDistanceBlocks", config.solidAvoidanceProbeDistanceBlocks);
            root.addProperty("solidAvoidanceVerticalClearanceBlocks", config.solidAvoidanceVerticalClearanceBlocks);
            root.addProperty("solidAvoidanceTurnStrength", config.solidAvoidanceTurnStrength);
            root.addProperty("minWaterDepthBlocksForTravel", config.minWaterDepthBlocksForTravel);

            root.addProperty("forceChunkLoadingEnabled", config.forceChunkLoadingEnabled);
            root.addProperty("preloadRadiusChunks", config.preloadRadiusChunks);
            root.addProperty("preloadAheadChunks", config.preloadAheadChunks);
            root.addProperty("preloadTicketLevel", config.preloadTicketLevel);
            root.addProperty("maxChunkLoadsPerTick", config.maxChunkLoadsPerTick);
            root.addProperty("releaseTicketsAfterTicks", config.releaseTicketsAfterTicks);

            root.addProperty("virtualStateFlushIntervalMinutes", config.virtualStateFlushIntervalMinutes);

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException(List.of("io: unable to write config path=" + path + " error=" + e.getMessage()));
        }
    }

    private static void validateEntityType(String entityTypeId, List<String> errors) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            errors.add("entityTypeId must be a valid namespaced id (was " + entityTypeId + ")");
            return;
        }

        if (!Registries.ENTITY_TYPE.containsId(id)) {
            errors.add("entityTypeId does not exist in registry (was " + entityTypeId + ")");
            return;
        }

        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        SpawnGroup spawnGroup = type.getSpawnGroup();
        boolean aquatic = spawnGroup == SpawnGroup.WATER_CREATURE
            || spawnGroup == SpawnGroup.WATER_AMBIENT
            || spawnGroup == SpawnGroup.UNDERGROUND_WATER_CREATURE
            || spawnGroup == SpawnGroup.AXOLOTLS;
        if (!aquatic) {
            errors.add("entityTypeId must be aquatic spawn group (was " + entityTypeId + ", group=" + spawnGroup + ")");
        }
    }

    private static void enforceKnownKeys(JsonObject root, List<String> errors) {
        Set<String> known = new LinkedHashSet<>(List.of(
            "entityTypeId",
            "entityScale",
            "minimumLeviathans",
            "virtualTravelEnabled",
            "roamMinDistanceBlocks",
            "roamMaxDistanceBlocks",
            "spawnY",
            "spawnYRandomRange",
            "virtualSpeedBlocksPerTick",
            "activationRadiusBlocks",
            "despawnRadiusBlocks",
            "minSpawnDistanceBlocks",
            "autoDistancesFromServer",
            "combatEnabled",
            "engageRadiusBlocks",
            "disengageRadiusBlocks",
            "chargeDurationTicks",
            "chargeCooldownTicks",
            "chargeSpeedBlocksPerTick",
            "chargeMinHitSpeed",
            "chargeDamage",
            "chargeHitCooldownTicks",
            "passOvershootBlocks",
            "turnaroundTicks",
            "lineOfEngagementTimeoutTicks",
            "disconnectLingerTicks",
            "chargeDirectionDotThreshold",
            "chargeHitboxExpandBlocks",
            "chargeKnockbackStrength",
            "requireWaterForSpawn",
            "solidAvoidanceProbeDistanceBlocks",
            "solidAvoidanceVerticalClearanceBlocks",
            "solidAvoidanceTurnStrength",
            "minWaterDepthBlocksForTravel",
            "forceChunkLoadingEnabled",
            "preloadRadiusChunks",
            "preloadAheadChunks",
            "preloadTicketLevel",
            "maxChunkLoadsPerTick",
            "releaseTicketsAfterTicks",
            "virtualStateFlushIntervalMinutes"
        ));

        for (String key : root.keySet()) {
            if (!known.contains(key)) {
                errors.add("unknown key: " + key);
            }
        }
    }

    private static String requireString(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            errors.add(key + " is required and must be string");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            errors.add(key + " must be string");
            return null;
        }
        String value = e.getAsString().trim();
        if (value.isEmpty()) {
            errors.add(key + " must not be blank");
            return null;
        }
        return value;
    }

    private static Boolean requireBoolean(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            errors.add(key + " is required and must be boolean");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            errors.add(key + " must be boolean");
            return null;
        }
        return e.getAsBoolean();
    }

    private static Integer requireInt(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            errors.add(key + " is required and must be integer");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be integer");
            return null;
        }

        String literal = e.getAsJsonPrimitive().getAsString();
        try {
            BigDecimal decimal = new BigDecimal(literal);
            if (decimal.stripTrailingZeros().scale() > 0) {
                errors.add(key + " must be integer (was " + literal + ")");
                return null;
            }
            return decimal.intValueExact();
        } catch (Exception ex) {
            errors.add(key + " must be valid int (was " + literal + ")");
            return null;
        }
    }

    private static Double requireDouble(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            errors.add(key + " is required and must be number");
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be number");
            return null;
        }

        String literal = e.getAsJsonPrimitive().getAsString();
        try {
            double value = Double.parseDouble(literal);
            if (!Double.isFinite(value)) {
                errors.add(key + " must be finite number (was " + literal + ")");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add(key + " must be valid number (was " + literal.toLowerCase(Locale.ROOT) + ")");
            return null;
        }
    }

    public static final class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }
}
