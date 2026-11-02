package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.compass.PetCompassItem;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents automation from moving a bound compass between inventories. */
@Mixin(HopperBlockEntity.class)
public abstract class PetCompassHopperMixin {
    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    private static void aipets$blockPetCompassTransfer(
            Container from,
            Container to,
            ItemStack stack,
            Direction side,
            CallbackInfoReturnable<ItemStack> callback) {
        if (PetCompassItem.isCandidate(stack)) {
            callback.setReturnValue(stack);
        }
    }

    @Inject(
            method = "canTakeItemFromContainer(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void aipets$blockPetCompassExtraction(
            Container from,
            Container to,
            ItemStack stack,
            int slot,
            Direction side,
            CallbackInfoReturnable<Boolean> callback) {
        if (PetCompassItem.isCandidate(stack)) {
            callback.setReturnValue(false);
        }
    }
}
