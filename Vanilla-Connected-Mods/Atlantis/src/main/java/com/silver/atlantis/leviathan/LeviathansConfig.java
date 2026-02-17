package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
    public final List<String> entityTypeIds;
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
    public final int engageVerticalRadiusBlocks;
    public final int disengageRadiusBlocks;
    public final int disengageVerticalRadiusBlocks;
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
    public final int depthScaleTopY;
    public final int depthScaleBottomY;
    public final double depthScaleAtTop;
    public final double depthScaleAtBottom;
    public final double depthDamageAtTop;
    public final double depthDamageAtBottom;
    public final double depthHealthAtTop;
    public final double depthHealthAtBottom;

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
        List<String> entityTypeIds,
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
        int engageVerticalRadiusBlocks,
        int disengageRadiusBlocks,
        int disengageVerticalRadiusBlocks,
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
        int depthScaleTopY,
        int depthScaleBottomY,
        double depthScaleAtTop,
        double depthScaleAtBottom,
        double depthDamageAtTop,
        double depthDamageAtBottom,
        double depthHealthAtTop,
        double depthHealthAtBottom,
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
        this.entityTypeIds = List.copyOf(entityTypeIds);
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
        this.engageVerticalRadiusBlocks = engageVerticalRadiusBlocks;
        this.disengageRadiusBlocks = disengageRadiusBlocks;
        this.disengageVerticalRadiusBlocks = disengageVerticalRadiusBlocks;
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
        this.depthScaleTopY = depthScaleTopY;
        this.depthScaleBottomY = depthScaleBottomY;
        this.depthScaleAtTop = depthScaleAtTop;
        this.depthScaleAtBottom = depthScaleAtBottom;
        this.depthDamageAtTop = depthDamageAtTop;
        this.depthDamageAtBottom = depthDamageAtBottom;
        this.depthHealthAtTop = depthHealthAtTop;
        this.depthHealthAtBottom = depthHealthAtBottom;
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
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] config file missing; writing defaults path={}", path);
            write(path, defaults());
        }
        return loadStrict(path);
    }

    public static LeviathansConfig loadStrict(Path path) {
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] loading strict config path={}", path);
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
        List<String> entityTypeIds = optionalStringArray(root, "entityTypeIds", errors);
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
        Integer engageVerticalRadiusBlocks = optionalInt(root, "engageVerticalRadiusBlocks", Math.max(1, engageRadiusBlocks == null ? 64 : engageRadiusBlocks / 2), errors);
        Integer disengageRadiusBlocks = requireInt(root, "disengageRadiusBlocks", errors);
        Integer disengageVerticalRadiusBlocks = optionalInt(root, "disengageVerticalRadiusBlocks", Math.max(1, disengageRadiusBlocks == null ? 96 : disengageRadiusBlocks / 2), errors);
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
        Integer depthScaleTopY = optionalInt(root, "depthScaleTopY", 192, errors);
        Integer depthScaleBottomY = optionalInt(root, "depthScaleBottomY", -32, errors);
        Double depthScaleAtTop = optionalDouble(root, "depthScaleAtTop", 0.65d, errors);
        Double depthScaleAtBottom = optionalDouble(root, "depthScaleAtBottom", 1.45d, errors);
        Double depthDamageAtTop = optionalDouble(root, "depthDamageAtTop", 0.7d, errors);
        Double depthDamageAtBottom = optionalDouble(root, "depthDamageAtBottom", 1.8d, errors);
        Double depthHealthAtTop = optionalDouble(root, "depthHealthAtTop", 0.7d, errors);
        Double depthHealthAtBottom = optionalDouble(root, "depthHealthAtBottom", 1.8d, errors);

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
        if (engageVerticalRadiusBlocks != null && engageVerticalRadiusBlocks < 1) {
            errors.add("engageVerticalRadiusBlocks must be >= 1 (was " + engageVerticalRadiusBlocks + ")");
        }
        if (disengageRadiusBlocks != null && disengageRadiusBlocks < 1) {
            errors.add("disengageRadiusBlocks must be >= 1 (was " + disengageRadiusBlocks + ")");
        }
        if (disengageVerticalRadiusBlocks != null && disengageVerticalRadiusBlocks < 1) {
            errors.add("disengageVerticalRadiusBlocks must be >= 1 (was " + disengageVerticalRadiusBlocks + ")");
        }
        if (engageRadiusBlocks != null && disengageRadiusBlocks != null && disengageRadiusBlocks < engageRadiusBlocks) {
            errors.add("disengageRadiusBlocks must be >= engageRadiusBlocks (was " + disengageRadiusBlocks + " < " + engageRadiusBlocks + ")");
        }
        if (engageVerticalRadiusBlocks != null && disengageVerticalRadiusBlocks != null && disengageVerticalRadiusBlocks < engageVerticalRadiusBlocks) {
            errors.add("disengageVerticalRadiusBlocks must be >= engageVerticalRadiusBlocks (was " + disengageVerticalRadiusBlocks + " < " + engageVerticalRadiusBlocks + ")");
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
        if (depthScaleTopY != null && depthScaleBottomY != null && depthScaleBottomY >= depthScaleTopY) {
            errors.add("depthScaleBottomY must be < depthScaleTopY (was " + depthScaleBottomY + " >= " + depthScaleTopY + ")");
        }
        if (depthScaleAtTop != null && depthScaleAtTop <= 0.0d) {
            errors.add("depthScaleAtTop must be > 0 (was " + depthScaleAtTop + ")");
        }
        if (depthScaleAtBottom != null && depthScaleAtBottom <= 0.0d) {
            errors.add("depthScaleAtBottom must be > 0 (was " + depthScaleAtBottom + ")");
        }
        if (depthDamageAtTop != null && depthDamageAtTop <= 0.0d) {
            errors.add("depthDamageAtTop must be > 0 (was " + depthDamageAtTop + ")");
        }
        if (depthDamageAtBottom != null && depthDamageAtBottom <= 0.0d) {
            errors.add("depthDamageAtBottom must be > 0 (was " + depthDamageAtBottom + ")");
        }
        if (depthHealthAtTop != null && depthHealthAtTop <= 0.0d) {
            errors.add("depthHealthAtTop must be > 0 (was " + depthHealthAtTop + ")");
        }
        if (depthHealthAtBottom != null && depthHealthAtBottom <= 0.0d) {
            errors.add("depthHealthAtBottom must be > 0 (was " + depthHealthAtBottom + ")");
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
        List<String> effectiveEntityTypeIds = new ArrayList<>();
        if (entityTypeIds != null && !entityTypeIds.isEmpty()) {
            effectiveEntityTypeIds.addAll(entityTypeIds);
        } else if (entityTypeId != null) {
            effectiveEntityTypeIds.add(entityTypeId);
        }
        if (effectiveEntityTypeIds.isEmpty()) {
            errors.add("entityTypeIds must contain at least one valid aquatic entity type id");
        } else {
            for (String id : effectiveEntityTypeIds) {
                validateEntityType(id, errors);
            }
        }

        if (!errors.isEmpty()) {
            AtlantisMod.LOGGER.error("[Atlantis][leviathan] strict config validation failed path={} errors={}", path, errors);
            throw new ValidationException(errors);
        }

        LeviathansConfig config = new LeviathansConfig(
            entityTypeId,
            effectiveEntityTypeIds,
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
            engageVerticalRadiusBlocks,
            disengageRadiusBlocks,
            disengageVerticalRadiusBlocks,
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
            depthScaleTopY,
            depthScaleBottomY,
            depthScaleAtTop,
            depthScaleAtBottom,
            depthDamageAtTop,
            depthDamageAtBottom,
            depthHealthAtTop,
            depthHealthAtBottom,
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
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] strict config loaded path={} entityTypeId={} scale={} min={} activation={} despawn={}",
            path,
            config.entityTypeId,
            config.entityScale,
            config.minimumLeviathans,
            config.activationRadiusBlocks,
            config.despawnRadiusBlocks);
        return config;
    }

    public static LeviathansConfig defaults() {
        return new LeviathansConfig(
            "minecraft:salmon",
            List.of(
                "minecraft:salmon",
                "minecraft:cod",
                "minecraft:tropical_fish",
                "minecraft:pufferfish",
                "minecraft:squid",
                "minecraft:glow_squid"
            ),
            8.0d,
            5,
            true,
            5000,
            30000,
            153,
            147,
            0.45d,
            256,
            384,
            256,
            true,
            true,
            120,
            64,
            192,
            96,
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
            300,
            6,
            0.55d,
            1.65d,
            0.6d,
            2.2d,
            0.6d,
            2.2d,
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
            JsonArray entityTypeIds = new JsonArray();
            for (String id : config.entityTypeIds) {
                entityTypeIds.add(id);
            }
            root.add("entityTypeIds", entityTypeIds);
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
            root.addProperty("engageVerticalRadiusBlocks", config.engageVerticalRadiusBlocks);
            root.addProperty("disengageRadiusBlocks", config.disengageRadiusBlocks);
            root.addProperty("disengageVerticalRadiusBlocks", config.disengageVerticalRadiusBlocks);
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
            root.addProperty("depthScaleTopY", config.depthScaleTopY);
            root.addProperty("depthScaleBottomY", config.depthScaleBottomY);
            root.addProperty("depthScaleAtTop", config.depthScaleAtTop);
            root.addProperty("depthScaleAtBottom", config.depthScaleAtBottom);
            root.addProperty("depthDamageAtTop", config.depthDamageAtTop);
            root.addProperty("depthDamageAtBottom", config.depthDamageAtBottom);
            root.addProperty("depthHealthAtTop", config.depthHealthAtTop);
            root.addProperty("depthHealthAtBottom", config.depthHealthAtBottom);

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
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] wrote config path={} entityTypeId={} scale={} min={}",
                path,
                config.entityTypeId,
                config.entityScale,
                config.minimumLeviathans);
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
            "entityTypeIds",
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
            "engageVerticalRadiusBlocks",
            "disengageRadiusBlocks",
            "disengageVerticalRadiusBlocks",
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
            "depthScaleTopY",
            "depthScaleBottomY",
            "depthScaleAtTop",
            "depthScaleAtBottom",
            "depthDamageAtTop",
            "depthDamageAtBottom",
            "depthHealthAtTop",
            "depthHealthAtBottom",
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

    private static Integer optionalInt(JsonObject root, String key, int defaultValue, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            return defaultValue;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be integer");
            return defaultValue;
        }
        String literal = e.getAsJsonPrimitive().getAsString();
        try {
            BigDecimal decimal = new BigDecimal(literal);
            if (decimal.stripTrailingZeros().scale() > 0) {
                errors.add(key + " must be integer (was " + literal + ")");
                return defaultValue;
            }
            return decimal.intValueExact();
        } catch (Exception ex) {
            errors.add(key + " must be valid int (was " + literal + ")");
            return defaultValue;
        }
    }

    private static Double optionalDouble(JsonObject root, String key, double defaultValue, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            return defaultValue;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be number");
            return defaultValue;
        }
        String literal = e.getAsJsonPrimitive().getAsString();
        try {
            double value = Double.parseDouble(literal);
            if (!Double.isFinite(value)) {
                errors.add(key + " must be finite number (was " + literal + ")");
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException ex) {
            errors.add(key + " must be valid number (was " + literal.toLowerCase(Locale.ROOT) + ")");
            return defaultValue;
        }
    }

    private static List<String> optionalStringArray(JsonObject root, String key, List<String> errors) {
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull()) {
            return List.of();
        }
        if (!e.isJsonArray()) {
            errors.add(key + " must be array of strings");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        JsonArray array = e.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                errors.add(key + "[" + i + "] must be string");
                continue;
            }
            String value = item.getAsString().trim();
            if (value.isEmpty()) {
                errors.add(key + "[" + i + "] must not be blank");
                continue;
            }
            out.add(value);
        }
        return out;
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
