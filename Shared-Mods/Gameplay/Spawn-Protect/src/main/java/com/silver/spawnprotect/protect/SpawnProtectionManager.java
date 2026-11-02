package com.silver.spawnprotect.protect;

import com.silver.spawnprotect.SpawnProtectMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Runtime access point for all spawn-protection checks.
 */
public final class SpawnProtectionManager {

    public static final SpawnProtectionManager INSTANCE = new SpawnProtectionManager();

    private volatile SpawnProtectConfig config = SpawnProtectConfig.defaultConfig();
    private static final Predicate<Mob> ANY_MOB = mob -> true;

    private SpawnProtectionManager() {
    }

    public void load() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("spawn-protect.properties");
        config = SpawnProtectConfig.loadOrCreate(configFile);
        SpawnProtectMod.LOGGER.info("Spawn Protect loaded for dimension {}", config.dimensionId());
    }

    public boolean isWithinProtectedBounds(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        SpawnProtectConfig snapshot = config;
        return snapshot.contains(world, pos);
    }

    public boolean shouldBlockBreak(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (isAllowedBypass(player)) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(player), pos);
    }

    public boolean shouldBlockPlace(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (isAllowedBypass(player)) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(player), pos);
    }

    public boolean shouldBlockDrop(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        if (isAllowedBypass(player)) {
            return false;
        }

        return isWithinProtectedBounds(playerWorld(player), player.blockPosition());
    }

    public boolean shouldBlockPvp(ServerPlayer attacker, ServerPlayer victim) {
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

        return isWithinProtectedBounds(playerWorld(attacker), attacker.blockPosition())
            || isWithinProtectedBounds(playerWorld(victim), victim.blockPosition());
    }

    public boolean hasProtectedBoundsInWorld(ServerLevel world) {
        if (world == null) {
            return false;
        }

        SpawnProtectConfig snapshot = config;
        return snapshot.matchesDimension(world.dimension().identifier());
    }

    public List<Mob> getMobsWithinProtectedBounds(ServerLevel world) {
        if (world == null) {
            return List.of();
        }

        SpawnProtectConfig snapshot = config;
        if (!snapshot.matchesDimension(world.dimension().identifier())) {
            return List.of();
        }

        return world.getEntities(EntityTypeTest.forClass(Mob.class), snapshot.protectedBox(), ANY_MOB);
    }

    public boolean isAllowedEntityInProtectedBounds(Entity entity) {
        if (entity == null) {
            return false;
        }

        return entity instanceof Villager
            || entity instanceof AbstractHorse
            || entity instanceof Donkey
            || entity instanceof Llama
            || entity instanceof TraderLlama
            || entity instanceof Camel
            || entity instanceof Cat
            || (entity instanceof Wolf wolf && wolf.isTame());
    }

    private boolean isAllowedBypass(ServerPlayer player) {
        SpawnProtectConfig snapshot = config;
        return snapshot.allowOpBypass() && AuthorizationChecks.has(player, PermissionNodes.SPAWNPROTECT_BYPASS);
    }

    private static ServerLevel playerWorld(ServerPlayer player) {
        return player.level();
    }
}
