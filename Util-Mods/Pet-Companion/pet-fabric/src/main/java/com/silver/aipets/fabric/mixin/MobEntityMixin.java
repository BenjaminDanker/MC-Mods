package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void aipets$preventTargetAssignment(LivingEntity target, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet() && target != null) {
            ci.cancel();
        }
    }

    @Inject(method = "canTarget(Lnet/minecraft/entity/EntityType;)Z", at = @At("HEAD"), cancellable = true)
    private void aipets$preventTypeTarget(EntityType<?> type, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "tryAttack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventAttack(
            ServerWorld world, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aipets$maintainPetSafety(CallbackInfo ci) {
        if (!((Object) this instanceof PetEntityData data) || !data.aipets$isPet()) {
            return;
        }
        MobEntity mob = (MobEntity) (Object) this;
        mob.setTarget(null);
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
        if (mob.hasVehicle()) {
            mob.stopRiding();
        }
        if (data.aipets$isSleeping()) {
            mob.getNavigation().stop();
        }
    }
}
