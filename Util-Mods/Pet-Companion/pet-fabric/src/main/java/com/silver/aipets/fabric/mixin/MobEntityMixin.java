package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobEntityMixin {
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
        }
    }
}
