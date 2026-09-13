package com.silver.skyislands.nocreepers.mixins;

import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mixin(BaseSpawner.class)
public abstract class MobSpawnerLogicMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobSpawnerLogicMixin.class);

    private static final Identifier CREEPER_ID = Identifier.fromNamespaceAndPath("minecraft", "creeper");

    @Shadow
    private SpawnData spawnEntry;

    @Shadow
    private WeightedRandomList<SpawnData> spawnPotentials;

    @Shadow
    private int spawnDelay;

    @Shadow
    private int minSpawnDelay;

    @Shadow
    private int maxSpawnDelay;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void skyIslands$blockCreeperSpawnerServerTick(ServerLevel world, net.minecraft.core.BlockPos pos, CallbackInfo ci) {
        // This is the critical path where actual spawning happens.
        if (isCreeperSpawnerConfigured()) {
            this.spawnDelay = world.getRandom().nextIntBetweenInclusive(this.minSpawnDelay, this.maxSpawnDelay);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nocreepers] blocked creeper spawner serverTick at {} delayReset={}", pos, this.spawnDelay);
            }
            ci.cancel();
        }
    }

    @Inject(method = "updateSpawns", at = @At("HEAD"), cancellable = true)
    private void skyIslands$blockCreeperSpawner(Level world, net.minecraft.core.BlockPos pos, CallbackInfo ci) {
        // Only affects mob spawner block logic. Spawn eggs and natural spawns are unaffected.
        if (isCreeperSpawnerConfigured()) {
            // Don't spawn anything; just reset the delay so it doesn't try every tick.
            this.spawnDelay = world.getRandom().nextIntBetweenInclusive(this.minSpawnDelay, this.maxSpawnDelay);
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
            for (WeightedEntry<SpawnData> weighted : this.spawnPotentials.getEntries()) {
                Object v = weighted.value();
                if (v instanceof SpawnData entry && isCreeperSpawnEntry(entry)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Sky-Islands][nocreepers] spawner configured for creeper via spawnPotentials");
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isCreeperSpawnEntry(SpawnData entry) {
        if (entry == null) {
            return false;
        }

        try {
            CompoundTag nbt = entry.getNbt();
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

    private static String extractEntityId(CompoundTag nbt) {
        if (nbt == null) {
            return "";
        }
        return nbt.getString("id").orElse("");
    }
}
