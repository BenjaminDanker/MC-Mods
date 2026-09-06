package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.passive.PassiveEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PassiveEntity.class)
public abstract class PassiveEntityMixin {
    @Inject(method = "setBreedingAge", at = @At("HEAD"), cancellable = true)
    private void aipets$preventAgeChange(int age, CallbackInfo ci) {
        if ((Object) this instanceof PetEntityData data && data.aipets$isPet() && age != 0) {
            ci.cancel();
        }
    }
}
