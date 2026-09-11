package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.compass.PetCompassItem;
import com.silver.aipets.fabric.PetCompanionMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A bound compass that reaches world-item state is destroyed before pickup or hopper intake. */
@Mixin(ItemEntity.class)
public abstract class PetCompassItemEntityMixin {
    @Shadow
    public abstract ItemStack getStack();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aipets$removeDroppedPetCompass(CallbackInfo callback) {
        if (PetCompassItem.isCandidate(getStack())) {
            PetCompanionMod.petCompassManager().ifPresent(manager -> manager.onDropped(getStack()));
            ((ItemEntity) (Object) this).discard();
            callback.cancel();
        }
    }
}
