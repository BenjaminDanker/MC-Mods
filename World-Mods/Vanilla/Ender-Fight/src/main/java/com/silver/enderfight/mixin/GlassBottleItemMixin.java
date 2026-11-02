package com.silver.enderfight.mixin;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.dragon.DragonBreathModifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks {@link BottleItem#use(Level, Player, InteractionHand)} so the stack placed into the player's hand
 * immediately after scooping dragon breath can be tagged before anything else manipulates it.
 */
@Mixin(BottleItem.class)
public abstract class GlassBottleItemMixin {
    @Inject(method = "use", at = @At("RETURN"))
    private void enderfight$tagDragonBreath(Level world, Player player, InteractionHand hand,
        CallbackInfoReturnable<Object> cir) {
        if (world == null || world.isClientSide()) {
            return;
        }
        if (player == null) {
            return;
        }

        if (!isManagedEndDimension(world)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: world {} is not an End dimension",
                world.dimension().identifier());
            return;
        }

        if (player.isSpectator()) {
            return;
        }

        Inventory inventory = player.getInventory();
        int specialBottleCount = DragonBreathModifier.countSpecialDragonBreath(inventory);
        if (specialBottleCount >= DragonBreathModifier.MAX_SPECIAL_DRAGON_BREATH_BOTTLES) {
            EnderFightMod.LOGGER.info(
                "Dragon breath tagging skipped: player {} already holds {} special bottles (limit={})",
                player.getName().getString(), specialBottleCount,
                DragonBreathModifier.MAX_SPECIAL_DRAGON_BREATH_BOTTLES);
            return;
        }

        ItemStack handStack = player.getItemInHand(hand);
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

    private static boolean tagInventoryStacks(Player player, Inventory inventory, Level world) {
        if (inventory == null) {
            return false;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (tryTagStackFromInventory(player, inventory, stack, world)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryTagStack(Player player, ItemStack stack, Level world) {
        if (stack == null || !stack.is(Items.DRAGON_BREATH)) {
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
            stack.shrink(1);
            Inventory inventory = player.getInventory();
            if (inventory == null || !inventory.add(single)) {
                player.drop(single, false);
            }
            return true;
        }
        return DragonBreathModifier.markAsSpecialDragonBreath(stack, world);
    }

    private static boolean tryTagStackFromInventory(Player player, Inventory inventory, ItemStack stack,
        Level world) {
        if (stack == null || !stack.is(Items.DRAGON_BREATH)) {
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
            stack.shrink(1);
            if (!inventory.add(single)) {
                player.drop(single, false);
            }
            return true;
        }

        return DragonBreathModifier.markAsSpecialDragonBreath(stack, world);
    }

    private static boolean isManagedEndDimension(Level world) {
        return world != null && com.silver.enderfight.portal.PortalInterceptor.isManagedEndDimension(world.dimension());
    }
}
