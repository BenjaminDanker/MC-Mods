package com.silver.witherfight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.silver.witherfight.WitherFightMod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

/**
 * Renames the nether star produced when the Wither dies. Mirrors the dragon breath decoration
 * logic from {@link com.silver.enderfight.dragon.DragonBreathModifier} by tagging the item before
 * it ever hits the ground.
 */
@Mixin(Entity.class)
abstract class WitherEntityMixin {

    @Inject(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void witherfight$customizeNetherStar(ServerLevel world, ItemLike convertible, CallbackInfoReturnable<ItemEntity> cir) {
        if (!(convertible == Items.NETHER_STAR && (Object) this instanceof WitherBoss) || world == null) {
            return;
        }

        ItemStack stack = new ItemStack(convertible);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Special Nether Star"));

        ItemEntity dropped = ((Entity) (Object) this).spawnAtLocation(world, stack);
        WitherFightMod.LOGGER.info("Wither dropped Special Nether Star at ({}, {}, {}), entity id {}", dropped.getX(), dropped.getY(), dropped.getZ(), dropped.getId());
        cir.setReturnValue(dropped);
    }

}
