package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.ai.PetActionController;
import com.silver.aipets.fabric.ai.PetSoundGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aipets$recoverInvalidPosition(CallbackInfo ci) {
        if (!((Object) this instanceof PetEntityData data) || !data.aipets$isPet()) return;
        Mob pet = (Mob) (Object) this;
        if (!(pet.level() instanceof ServerLevel world)) return;
        if (Double.isFinite(pet.getX()) && Double.isFinite(pet.getY())
                && Double.isFinite(pet.getZ()) && pet.getY() >= world.getMinY() - 8) return;
        java.util.UUID owner = data.aipets$getOwnerUuid();
        pet.discard();
        PetCompanionMod.entityRecoveryCoordinator().ifPresent(recovery ->
                recovery.recoverOwner(world.getServer(), owner));
        ci.cancel();
    }

    @Inject(method = "playAmbientSound", at = @At("HEAD"), cancellable = true)
    private void aipets$onlyControlledAmbient(CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data
                && PetSoundGate.cancelAmbient(data.aipets$isPet(),
                        PetActionController.controlledSound())) ci.cancel();
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void aipets$preventTargetAssignment(LivingEntity target, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet() && target != null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventAttack(
            ServerLevel world, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aipets$maintainPetSafety(CallbackInfo ci) {
        if (!((Object) this instanceof PetEntityData data) || !data.aipets$isPet()) {
            return;
        }
        Mob mob = (Mob) (Object) this;
        mob.setTarget(null);
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
        if (mob.isPassenger()) {
            mob.stopRiding();
        }
        if (data.aipets$isSleeping()) {
            mob.getNavigation().stop();
        } else if (mob instanceof TamableAnimal pet) {
            PetActionController.tick(pet);
        }
    }
}
