package com.silver.witherfight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silver.witherfight.WitherFightMod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Renames the nether star produced when the Wither dies. Mirrors the dragon breath decoration
 * logic from {@link com.silver.enderfight.dragon.DragonBreathModifier} by tagging the item before
 * it ever hits the ground.
 */
@Mixin(Entity.class)
abstract class WitherEntityMixin {

    @Inject(method = "dropItem(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemConvertible;)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void witherfight$customizeNetherStar(ServerWorld world, ItemConvertible convertible, CallbackInfoReturnable<ItemEntity> cir) {
        if (!(convertible == Items.NETHER_STAR && (Object) this instanceof WitherEntity) || world == null) {
            return;
        }

        ItemStack stack = new ItemStack(convertible);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Special Nether Star"));

        ItemEntity dropped = ((Entity) (Object) this).dropStack(world, stack);
        WitherFightMod.LOGGER.info("Wither dropped Special Nether Star at ({}, {}, {}), entity id {}", dropped.getX(), dropped.getY(), dropped.getZ(), dropped.getId());
        cir.setReturnValue(dropped);
    }

}
