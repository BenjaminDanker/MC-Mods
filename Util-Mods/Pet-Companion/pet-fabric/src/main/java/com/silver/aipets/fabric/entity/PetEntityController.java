package com.silver.aipets.fabric.entity;

import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.ai.PetFollowOwnerGoal;
import com.silver.aipets.fabric.mixin.MobEntityAccessor;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;

/** Applies pet-only vanilla-mechanic isolation and the bounded custom goal set. */
public final class PetEntityController {
    public static final String NO_DESPAWN_TAG = "no_despawn";

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

        entity.clearGoalsAndTasks();
        var goals = ((MobEntityAccessor) entity).aipets$getGoalSelector();
        goals.add(0, new SwimGoal(entity));
        goals.add(1, new PetFollowOwnerGoal(entity, PetCompanionMod.physicalConfig()));
    }
}
