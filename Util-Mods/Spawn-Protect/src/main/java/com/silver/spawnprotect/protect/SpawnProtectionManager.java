package com.silver.spawnprotect.protect;

import com.silver.spawnprotect.SpawnProtectMod;
import net.minecraft.entity.mob.MobEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Runtime access point for all spawn-protection checks.
 */
public final class SpawnProtectionManager {

    public static final SpawnProtectionManager INSTANCE = new SpawnProtectionManager();

    private volatile SpawnProtectConfig config = SpawnProtectConfig.defaultConfig();
    private static final Predicate<MobEntity> ANY_MOB = mob -> true;

    private SpawnProtectionManager() {
    }

    public void load() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("spawn-protect.properties");
        config = SpawnProtectConfig.loadOrCreate(configFile);
        SpawnProtectMod.LOGGER.info("Spawn Protect loaded for dimension {}", config.dimensionId());
    }

    public boolean isWithinProtectedBounds(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        SpawnProtectConfig snapshot = config;
        return snapshot.contains(world, pos);
    }

    public boolean shouldBlockBreak(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (isAllowedBypass(player)) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(player), pos);
    }

    public boolean shouldBlockPlace(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (isAllowedBypass(player)) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(player), pos);
    }

    public boolean shouldBlockPvp(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        if (attacker == null || victim == null) {
            return false;
        }

        SpawnProtectConfig snapshot = config;
        if (!snapshot.disablePvp()) {
            return false;
        }

        if (snapshot.allowOpBypass() && (isAllowedBypass(attacker) || isAllowedBypass(victim))) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(attacker), attacker.getBlockPos())
            || isWithinProtectedBounds(playerWorld(victim), victim.getBlockPos());
    }

    public boolean hasProtectedBoundsInWorld(ServerWorld world) {
        if (world == null) {
            return false;
        }

        SpawnProtectConfig snapshot = config;
        return snapshot.matchesDimension(world.getRegistryKey().getValue());
    }

    public List<MobEntity> getMobsWithinProtectedBounds(ServerWorld world) {
        if (world == null) {
            return List.of();
        }

        SpawnProtectConfig snapshot = config;
        if (!snapshot.matchesDimension(world.getRegistryKey().getValue())) {
            return List.of();
        }

        return world.getEntitiesByClass(MobEntity.class, snapshot.protectedBox(), ANY_MOB);
    }

    private boolean isAllowedBypass(ServerPlayerEntity player) {
        SpawnProtectConfig snapshot = config;
        return snapshot.allowOpBypass() && player.hasPermissionLevel(2);
    }

    private static ServerWorld playerWorld(ServerPlayerEntity player) {
        return (ServerWorld) player.getEntityWorld();
    }
}
