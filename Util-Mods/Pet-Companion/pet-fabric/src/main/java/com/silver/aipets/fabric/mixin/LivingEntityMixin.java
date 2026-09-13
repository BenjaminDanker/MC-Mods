package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }
}
