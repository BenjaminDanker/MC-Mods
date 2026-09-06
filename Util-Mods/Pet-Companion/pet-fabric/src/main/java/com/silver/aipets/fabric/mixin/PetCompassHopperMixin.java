package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.compass.PetCompassItem;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents automation from moving a bound compass between inventories. */
@Mixin(HopperBlockEntity.class)
public abstract class PetCompassHopperMixin {
    @Inject(
            method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    private static void aipets$blockPetCompassTransfer(
            Inventory from,
            Inventory to,
            ItemStack stack,
            Direction side,
            CallbackInfoReturnable<ItemStack> callback) {
        if (PetCompassItem.isCandidate(stack)) {
            callback.setReturnValue(stack);
        }
    }

    @Inject(
            method = "canExtract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void aipets$blockPetCompassExtraction(
            Inventory from,
            Inventory to,
            ItemStack stack,
            int slot,
            Direction side,
            CallbackInfoReturnable<Boolean> callback) {
        if (PetCompassItem.isCandidate(stack)) {
            callback.setReturnValue(false);
        }
    }
}
