package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(
            method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z",
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
