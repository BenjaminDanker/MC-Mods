package com.silver.entitypruner;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

/** Identifies mobs that must never be treated as disposable by the pruner. */
public final class EntityProtection {
    /** Shared marker used by persistent companion entities, including Pet Companion pets. */
    public static final String NO_DESPAWN_TAG = "no_despawn";

    /** Marker used by Atlantis for its structure/proximity-spawned custom mobs. */
    public static final String ATLANTIS_SPAWNED_MOB_TAG = "atlantis_spawned_mob";

    private EntityProtection() {
    }

    public static boolean isProtectedMob(Entity entity) {
        return entity instanceof MobEntity mob
                && (mob.isPersistent()
                || entity.getCommandTags().contains(NO_DESPAWN_TAG)
                || entity.getCommandTags().contains(ATLANTIS_SPAWNED_MOB_TAG)
                || entity.hasCustomName());
    }
}
