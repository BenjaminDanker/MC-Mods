package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.compass.PetCompassItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects container, crafting, trading, and wrong-player insertion at the server slot boundary. */
@Mixin(Slot.class)
public abstract class PetCompassSlotMixin {
    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void aipets$restrictPetCompass(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (!PetCompassItem.isCandidate(stack)) {
            return;
        }
        if (!(container instanceof Inventory playerInventory)
                || !PetCompanionMod.isPetCompassAllowed(playerInventory, stack)) {
            callback.setReturnValue(false);
        }
    }
}
