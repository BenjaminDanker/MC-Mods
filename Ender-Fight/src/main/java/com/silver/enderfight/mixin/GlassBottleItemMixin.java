package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.dragon.DragonBreathModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.GlassBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@link GlassBottleItem#use(World, PlayerEntity, Hand)} so the stack placed into the player's hand
 * immediately after scooping dragon breath can be tagged before anything else manipulates it.
 */
@Mixin(GlassBottleItem.class)
public abstract class GlassBottleItemMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void enderfight$tagDragonBreath(World world, PlayerEntity player, Hand hand,
        CallbackInfoReturnable<Object> cir) {
        if (world == null || world.isClient()) {
            return;
        }
        if (player == null) {
            return;
        }

        if (!isEndDimension(world)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: world {} is not an End dimension",
                world.getRegistryKey().getValue());
            return;
        }

        if (player.isSpectator()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        if (playerHasSpecialDragonBreath(inventory)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: player {} already holds a special bottle",
                player.getName().getString());
            return;
        }

        ItemStack handStack = player.getStackInHand(hand);
        EnderFightMod.LOGGER.info("GlassBottleItem#use returned. Hand={} stack={} (player={})", hand, handStack,
            player.getName().getString());

        boolean tagged = tryTagStack(player, handStack, world);
        if (!tagged) {
            tagged = tagInventoryStacks(player, inventory, world);
        }

        if (tagged) {
            EnderFightMod.LOGGER.info("Dragon breath tagging succeeded for player {}", player.getName().getString());
        } else {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: no untagged dragon breath stack found (player={})",
                player.getName().getString());
        }
    }

    private static boolean tagInventoryStacks(PlayerEntity player, PlayerInventory inventory, World world) {
        if (inventory == null) {
            return false;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (tryTagStackFromInventory(player, inventory, stack, world)) {
                return true;
            }
        }

        return false;
    }

    private static boolean playerHasSpecialDragonBreath(PlayerInventory inventory) {
        if (inventory == null) {
            return false;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (DragonBreathModifier.isSpecialDragonBreath(stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryTagStack(PlayerEntity player, ItemStack stack, World world) {
        if (stack == null || !stack.isOf(Items.DRAGON_BREATH)) {
            return false;
        }
        if (DragonBreathModifier.isSpecialDragonBreath(stack)) {
            return false;
        }
        if (stack.getCount() > 1) {
            ItemStack single = stack.copy();
            single.setCount(1);
            if (!DragonBreathModifier.markAsSpecialDragonBreath(single, world)) {
                return false;
            }
            stack.decrement(1);
            PlayerInventory inventory = player.getInventory();
            if (inventory == null || !inventory.insertStack(single)) {
                player.dropItem(single, false);
            }
            return true;
        }
        return DragonBreathModifier.markAsSpecialDragonBreath(stack, world);
    }

    private static boolean tryTagStackFromInventory(PlayerEntity player, PlayerInventory inventory, ItemStack stack,
        World world) {
        if (stack == null || !stack.isOf(Items.DRAGON_BREATH)) {
            return false;
        }
        if (DragonBreathModifier.isSpecialDragonBreath(stack)) {
            return false;
        }

        if (stack.getCount() > 1) {
            ItemStack single = stack.copy();
            single.setCount(1);
            if (!DragonBreathModifier.markAsSpecialDragonBreath(single, world)) {
                return false;
            }
            stack.decrement(1);
            if (!inventory.insertStack(single)) {
                player.dropItem(single, false);
            }
            return true;
        }

        return DragonBreathModifier.markAsSpecialDragonBreath(stack, world);
    }

    private static boolean isEndDimension(World world) {
        return world != null && world.getDimensionEntry().matchesKey(DimensionTypes.THE_END);
    }
}
