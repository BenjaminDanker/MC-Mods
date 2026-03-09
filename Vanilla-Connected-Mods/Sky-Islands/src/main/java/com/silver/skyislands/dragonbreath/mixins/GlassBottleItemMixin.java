package com.silver.skyislands.dragonbreath.mixins;

import com.silver.skyislands.dragonbreath.SpecialDragonBreathItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.GlassBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlassBottleItem.class)
public abstract class GlassBottleItemMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlassBottleItemMixin.class);

    @Inject(method = "use", at = @At("RETURN"))
    private void skyislands$tagDragonBreath(World world, PlayerEntity player, Hand hand,
        CallbackInfoReturnable<Object> cir) {
        if (world == null || world.isClient() || player == null || player.isSpectator()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        if (SpecialDragonBreathItem.countSpecialDragonBreath(inventory)
            >= SpecialDragonBreathItem.MAX_SPECIAL_DRAGON_BREATH_BOTTLES) {
            LOGGER.info("[Sky-Islands][dragonbreath] tagging skipped for {}: already at cap {}",
                player.getName().getString(),
                SpecialDragonBreathItem.MAX_SPECIAL_DRAGON_BREATH_BOTTLES);
            return;
        }

        ItemStack handStack = player.getStackInHand(hand);
        if (tryTagStack(player, handStack, world)) {
            LOGGER.info("[Sky-Islands][dragonbreath] tagged hand stack for {} in world {}",
                player.getName().getString(),
                world.getRegistryKey().getValue());
            return;
        }

        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            if (tryTagStack(player, inventory.getStack(slot), world)) {
                LOGGER.info("[Sky-Islands][dragonbreath] tagged inventory stack for {} in world {} slot {}",
                    player.getName().getString(),
                    world.getRegistryKey().getValue(),
                    slot);
                return;
            }
        }

        LOGGER.info("[Sky-Islands][dragonbreath] no eligible dragon breath stack found for {} in world {}",
            player.getName().getString(),
            world.getRegistryKey().getValue());
    }

    private static boolean tryTagStack(PlayerEntity player, ItemStack stack, World world) {
        if (stack == null || !stack.isOf(Items.DRAGON_BREATH) || SpecialDragonBreathItem.isSpecialDragonBreath(stack)) {
            return false;
        }

        if (stack.getCount() > 1) {
            ItemStack single = stack.copy();
            single.setCount(1);
            if (!SpecialDragonBreathItem.markAsSpecialDragonBreath(single, world)) {
                return false;
            }

            stack.decrement(1);
            PlayerInventory inventory = player.getInventory();
            if (inventory == null || !inventory.insertStack(single)) {
                player.dropItem(single, false);
            }
            return true;
        }

        return SpecialDragonBreathItem.markAsSpecialDragonBreath(stack, world);
    }
}