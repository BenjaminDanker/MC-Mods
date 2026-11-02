package com.silver.entitypruner;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Identifies mobs that must never be treated as disposable by the pruner. */
public final class EntityProtection {
    /** Shared marker used by persistent companion entities, including Pet Companion pets. */
    public static final String NO_DESPAWN_TAG = "no_despawn";

    /** Marker used by Atlantis for its structure/proximity-spawned custom mobs. */
    public static final String ATLANTIS_SPAWNED_MOB_TAG = "atlantis_spawned_mob";

    private EntityProtection() {
    }

    public static boolean isProtectedMob(Entity entity) {
        return entity instanceof Mob mob
                && (mob.requiresCustomPersistence()
                || entity.entityTags().contains(NO_DESPAWN_TAG)
                || entity.entityTags().contains(ATLANTIS_SPAWNED_MOB_TAG)
                || entity.hasCustomName());
    }
}
