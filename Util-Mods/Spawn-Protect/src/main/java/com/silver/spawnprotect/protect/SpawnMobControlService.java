package com.silver.spawnprotect.protect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.mob.MobEntity;

/**
 * Keeps spawn area clear of mobs:
 * - removes mobs immediately when they spawn/load inside protected bounds
 * - removes mobs that path or get pushed into protected bounds
 */
public final class SpawnMobControlService {

    private static final long MOB_SCAN_INTERVAL_TICKS = 20L;

    public void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof MobEntity mob)) {
                return;
            }

            if (SpawnProtectionManager.INSTANCE.isAllowedEntityInProtectedBounds(mob)) {
                return;
            }

            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, mob.getBlockPos())) {
                mob.discard();
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!SpawnProtectionManager.INSTANCE.hasProtectedBoundsInWorld(world)) {
                return;
            }

            if ((world.getTime() % MOB_SCAN_INTERVAL_TICKS) != 0L) {
                return;
            }

            for (MobEntity mob : SpawnProtectionManager.INSTANCE.getMobsWithinProtectedBounds(world)) {
                if (SpawnProtectionManager.INSTANCE.isAllowedEntityInProtectedBounds(mob)) {
                    continue;
                }

                mob.discard();
            }
        });
    }
}
