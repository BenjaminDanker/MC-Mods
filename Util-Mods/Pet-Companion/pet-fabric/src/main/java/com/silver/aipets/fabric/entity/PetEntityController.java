package com.silver.aipets.fabric.entity;

import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.ai.PetFollowOwnerGoal;
import com.silver.aipets.fabric.mixin.MobEntityAccessor;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FloatGoal;

/** Applies pet-only vanilla-mechanic isolation and the bounded custom goal set. */
public final class PetEntityController {
    public static final String NO_DESPAWN_TAG = "no_despawn";
    private static final Map<TamableAnimal, Boolean> CONFIGURED_GOALS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PetEntityController() {
    }

    public static void configure(TamableAnimal entity) {
        if (!(entity instanceof PetEntityData data) || !data.aipets$isPet()) {
            return;
        }

        entity.setNoAi(false);
        entity.setInvulnerable(true);
        entity.setPersistenceRequired();
        entity.addTag(NO_DESPAWN_TAG);
        entity.setAge(0);
        entity.resetLove();
        entity.setTarget(null);
        entity.setHealth(entity.getMaxHealth());
        if (entity.isPassenger()) {
            entity.stopRiding();
        }

        // Reconciliation calls configure repeatedly. Rebuilding a live goal selector can
        // interrupt its selector iteration and leave the replacement follow goal dormant.
        // Weak object identity keeps this idempotent while allowing unloaded entities to vanish.
        if (CONFIGURED_GOALS.putIfAbsent(entity, Boolean.TRUE) == null) {
            entity.removeFreeWill();
            var goals = ((MobEntityAccessor) entity).aipets$getGoalSelector();
            goals.addGoal(0, new FloatGoal(entity));
            goals.addGoal(1, new PetFollowOwnerGoal(entity, PetCompanionMod.physicalConfig()));
        }
    }
}
