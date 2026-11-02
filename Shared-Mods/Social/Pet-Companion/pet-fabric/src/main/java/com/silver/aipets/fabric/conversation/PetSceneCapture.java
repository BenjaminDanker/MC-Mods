package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.transport.PetSceneSnapshot;
import com.silver.aipets.fabric.ai.PetActionController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;

/** Cheap local facts sampled only when the owner actually submits a conversation turn. */
final class PetSceneCapture {
    private PetSceneCapture() { }

    static PetSceneSnapshot capture(ServerPlayer owner, TamableAnimal pet, String backend) {
        if (!(pet.level() instanceof ServerLevel world) || owner.level() != world) {
            return PetSceneSnapshot.unknown();
        }
        double distance = Math.sqrt(owner.distanceToSqr(pet));
        boolean ownerVisible = pet.hasLineOfSight(owner);
        String proximity = distance < 3 ? "TOUCHING" : distance < 8 ? "NEAR"
                : distance <= 16 ? "IN_SIGHT" : "FAR";
        String ownerActivity = owner.hurtTime > 0 ? "RECENTLY_HURT"
                : owner.isUsingItem() ? "USING_ITEM"
                : owner.isSprinting() || owner.getDeltaMovement().horizontalDistanceSqr() > .0025
                        ? "MOVING" : "STILL";
        float healthRatio = owner.getHealth() / Math.max(1, owner.getMaxHealth());
        String health = healthRatio <= .25f ? "CRITICAL"
                : healthRatio < .75f ? "HURT" : "HEALTHY";
        String weather = world.isThundering() ? "THUNDER"
                : world.isRainingAt(pet.blockPosition()) ? "RAIN" : "CLEAR";
        boolean threat = !world.getEntitiesOfClass(Monster.class,
                pet.getBoundingBox().inflate(12), mob -> mob.isAlive()
                        && pet.hasLineOfSight(mob)).isEmpty();
        return new PetSceneSnapshot(
                biome(world, pet.blockPosition()),
                ownerVisible ? biome(world, owner.blockPosition()) : "unknown",
                ownerVisible ? proximity : "UNKNOWN", ownerVisible,
                ownerVisible ? ownerActivity : "UNKNOWN",
                ownerVisible ? health : "UNKNOWN",
                PetActionController.currentIntent(pet), weather, threat, java.util.Optional.of(
                        PetSpatialLocationCapture.capture(world, backend, pet.getX(), pet.getY(), pet.getZ())));
    }

    private static String biome(ServerLevel world, net.minecraft.core.BlockPos position) {
        return world.getBiome(position).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("unknown");
    }
}
