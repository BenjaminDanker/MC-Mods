package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"), cancellable = true)
    private void aipets$preventDamageDispatch(DamageSource source, float amount, CallbackInfo ci) {
        if (managed((Entity) (Object) this) || managed(source.getEntity())
                || managed(source.getDirectEntity())) ci.cancel();
    }

    @Inject(method = "kill(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD"), cancellable = true)
    private void aipets$preventPetKill(ServerLevel world, CallbackInfo ci) {
        if (managed((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "setInvulnerable", at = @At("HEAD"), cancellable = true)
    private void aipets$preserveInvulnerability(boolean value, CallbackInfo ci) {
        if (!value && managed((Entity) (Object) this)) ci.cancel();
    }

    private static boolean managed(Entity entity) {
        return entity instanceof PetEntityData data && data.aipets$isPet();
    }

    @Inject(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventVehicleTransport(
            Entity vehicle,
            boolean force,
            boolean teleporting,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }
}
