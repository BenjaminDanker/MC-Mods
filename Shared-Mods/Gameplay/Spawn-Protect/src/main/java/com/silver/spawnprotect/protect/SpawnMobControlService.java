package com.silver.spawnprotect.protect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;

/**
 * Keeps spawn area clear of mobs:
 * - removes mobs immediately when they spawn/load inside protected bounds
 * - removes mobs that path or get pushed into protected bounds
 */
public final class SpawnMobControlService {

    private static final long MOB_SCAN_INTERVAL_TICKS = 20L;

    public void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof Mob mob)) {
                return;
            }

            if (SpawnProtectionManager.INSTANCE.isAllowedEntityInProtectedBounds(mob)) {
                return;
            }

            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, mob.blockPosition())) {
                mob.discard();
            }
        });

        ServerTickEvents.END_LEVEL_TICK.register(world -> {
            if (!SpawnProtectionManager.INSTANCE.hasProtectedBoundsInWorld(world)) {
                return;
            }

            if ((world.getGameTime() % MOB_SCAN_INTERVAL_TICKS) != 0L) {
                return;
            }

            for (Mob mob : SpawnProtectionManager.INSTANCE.getMobsWithinProtectedBounds(world)) {
                if (SpawnProtectionManager.INSTANCE.isAllowedEntityInProtectedBounds(mob)) {
                    continue;
                }

                mob.discard();
            }
        });
    }
}
