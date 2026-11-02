package com.silver.skyislands.dragonbreath.mixins;

import com.silver.skyislands.dragonbreath.SpecialDragonBreathItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BottleItem.class)
public abstract class GlassBottleItemMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlassBottleItemMixin.class);

    @Inject(method = "use", at = @At("RETURN"))
    private void skyislands$tagDragonBreath(Level world, Player player, InteractionHand hand,
        CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (world == null || world.isClientSide() || player == null || player.isSpectator()) {
            return;
        }

        Inventory inventory = player.getInventory();
        if (SpecialDragonBreathItem.countSpecialDragonBreath(inventory)
            >= SpecialDragonBreathItem.MAX_SPECIAL_DRAGON_BREATH_BOTTLES) {
            LOGGER.info("[Sky-Islands][dragonbreath] tagging skipped for {}: already at cap {}",
                player.getName().getString(),
                SpecialDragonBreathItem.MAX_SPECIAL_DRAGON_BREATH_BOTTLES);
            return;
        }

        ItemStack handStack = player.getItemInHand(hand);
        if (tryTagStack(player, handStack, world)) {
            LOGGER.info("[Sky-Islands][dragonbreath] tagged hand stack for {} in world {}",
                player.getName().getString(),
                world.dimension().identifier());
            return;
        }

        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (tryTagStack(player, inventory.getItem(slot), world)) {
                LOGGER.info("[Sky-Islands][dragonbreath] tagged inventory stack for {} in world {} slot {}",
                    player.getName().getString(),
                    world.dimension().identifier(),
                    slot);
                return;
            }
        }

        LOGGER.info("[Sky-Islands][dragonbreath] no eligible dragon breath stack found for {} in world {}",
            player.getName().getString(),
            world.dimension().identifier());
    }

    private static boolean tryTagStack(Player player, ItemStack stack, Level world) {
        if (stack == null || !stack.is(Items.DRAGON_BREATH.builtInRegistryHolder()) || SpecialDragonBreathItem.isSpecialDragonBreath(stack)) {
            return false;
        }

        if (stack.getCount() > 1) {
            ItemStack single = stack.copy();
            single.setCount(1);
            if (!SpecialDragonBreathItem.markAsSpecialDragonBreath(single, world)) {
                return false;
            }

            stack.shrink(1);
            Inventory inventory = player.getInventory();
            if (inventory == null || !inventory.add(single)) {
                player.drop(single, false);
            }
            return true;
        }

        return SpecialDragonBreathItem.markAsSpecialDragonBreath(stack, world);
    }
}
