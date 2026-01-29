package com.silver.skyislands.nocreepers.mixins;

import net.minecraft.block.spawner.MobSpawnerEntry;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.collection.Weighted;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobSpawnerLogicMixin.class);

    private static final Identifier CREEPER_ID = Identifier.of("minecraft", "creeper");

    @Shadow
    private MobSpawnerEntry spawnEntry;

    @Shadow
    private Pool<MobSpawnerEntry> spawnPotentials;

    @Shadow
    private int spawnDelay;

    @Shadow
    private int minSpawnDelay;

    @Shadow
    private int maxSpawnDelay;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void skyIslands$blockCreeperSpawnerServerTick(ServerWorld world, net.minecraft.util.math.BlockPos pos, CallbackInfo ci) {
        // This is the critical path where actual spawning happens.
        if (isCreeperSpawnerConfigured()) {
            this.spawnDelay = world.getRandom().nextBetween(this.minSpawnDelay, this.maxSpawnDelay);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nocreepers] blocked creeper spawner serverTick at {} delayReset={}", pos, this.spawnDelay);
            }
            ci.cancel();
        }
    }

    @Inject(method = "updateSpawns", at = @At("HEAD"), cancellable = true)
    private void skyIslands$blockCreeperSpawner(World world, net.minecraft.util.math.BlockPos pos, CallbackInfo ci) {
        // Only affects mob spawner block logic. Spawn eggs and natural spawns are unaffected.
        if (isCreeperSpawnerConfigured()) {
            // Don't spawn anything; just reset the delay so it doesn't try every tick.
            this.spawnDelay = world.getRandom().nextBetween(this.minSpawnDelay, this.maxSpawnDelay);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nocreepers] blocked creeper spawner updateSpawns at {} delayReset={}", pos, this.spawnDelay);
            }
            ci.cancel();
        }
    }

    private boolean isCreeperSpawnerConfigured() {
        if (isCreeperSpawnEntry(this.spawnEntry)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nocreepers] spawner configured for creeper via spawnEntry");
            }
            return true;
        }

        if (this.spawnPotentials != null && !this.spawnPotentials.isEmpty()) {
            for (Weighted<MobSpawnerEntry> weighted : this.spawnPotentials.getEntries()) {
                Object v = weighted.value();
                if (v instanceof MobSpawnerEntry entry && isCreeperSpawnEntry(entry)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Sky-Islands][nocreepers] spawner configured for creeper via spawnPotentials");
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isCreeperSpawnEntry(MobSpawnerEntry entry) {
        if (entry == null) {
            return false;
        }

        try {
            NbtCompound nbt = entry.getNbt();
            if (nbt == null) {
                return false;
            }

            // In 1.21.x, spawner spawn entries are commonly shaped as:
            // { "entity": { "id": "minecraft:creeper", ... }, ... }
            // but some code paths may still use a flat { "id": "minecraft:creeper" }.
            String idString = extractEntityId(nbt);
            if (idString.isEmpty()) {
                idString = extractEntityId(nbt.getCompoundOrEmpty("entity"));
            }
            if (idString.isEmpty()) {
                idString = extractEntityId(nbt.getCompoundOrEmpty("Entity"));
            }
            if (idString.isEmpty()) {
                idString = extractEntityId(nbt.getCompoundOrEmpty("SpawnData"));
            }

            if (idString.isEmpty()) {
                return false;
            }

            String normalized = idString.toLowerCase();
            if (normalized.equals("creeper")) {
                return true;
            }

            Identifier id = Identifier.tryParse(normalized);
            return CREEPER_ID.equals(id);
        } catch (Throwable ignored) {
            // Fail open: don't break spawners if NBT shape changes.
            return false;
        }
    }

    private static String extractEntityId(NbtCompound nbt) {
        if (nbt == null) {
            return "";
        }
        return nbt.getString("id").orElse("");
    }
}
