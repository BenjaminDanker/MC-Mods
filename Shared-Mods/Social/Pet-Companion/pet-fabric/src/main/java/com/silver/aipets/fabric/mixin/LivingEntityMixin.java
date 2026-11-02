package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$ignoreDamage(
            ServerLevel world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (managed((Entity) (Object) this) || managed(source.getEntity())
                || managed(source.getDirectEntity())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void aipets$preventHealthLoss(float health, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()
                && health < ((LivingEntity) (Object) this).getMaxHealth()) ci.cancel();
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void aipets$preventDeath(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) ci.cancel();
    }

    @Inject(method = "kill(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD"), cancellable = true)
    private void aipets$preventDirectKill(ServerLevel world, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) ci.cancel();
    }

    private static boolean managed(Entity entity) {
        return entity instanceof PetEntityData data && data.aipets$isPet();
    }
}
