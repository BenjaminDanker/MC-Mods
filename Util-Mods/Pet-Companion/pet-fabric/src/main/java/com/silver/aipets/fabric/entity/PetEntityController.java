package com.silver.aipets.fabric.entity;

import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.ai.PetFollowOwnerGoal;
import com.silver.aipets.fabric.mixin.MobEntityAccessor;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Applies pet-only vanilla-mechanic isolation and the bounded custom goal set. */
public final class PetEntityController {
    public static final String NO_DESPAWN_TAG = "no_despawn";
    private static final Map<TameableEntity, Boolean> CONFIGURED_GOALS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PetEntityController() {
    }

    public static void configure(TameableEntity entity) {
        if (!(entity instanceof PetEntityData data) || !data.aipets$isPet()) {
            return;
        }

        entity.setAiDisabled(false);
        entity.setInvulnerable(true);
        entity.setPersistent();
        entity.addCommandTag(NO_DESPAWN_TAG);
        entity.setBreedingAge(0);
        entity.resetLoveTicks();
        entity.setTarget(null);
        entity.setHealth(entity.getMaxHealth());
        if (entity.hasVehicle()) {
            entity.stopRiding();
        }

        // Reconciliation calls configure repeatedly. Rebuilding a live goal selector can
        // interrupt its selector iteration and leave the replacement follow goal dormant.
        // Weak object identity keeps this idempotent while allowing unloaded entities to vanish.
        if (CONFIGURED_GOALS.putIfAbsent(entity, Boolean.TRUE) == null) {
            entity.clearGoalsAndTasks();
            var goals = ((MobEntityAccessor) entity).aipets$getGoalSelector();
            goals.add(0, new SwimGoal(entity));
            goals.add(1, new PetFollowOwnerGoal(entity, PetCompanionMod.physicalConfig()));
        }
    }
}
