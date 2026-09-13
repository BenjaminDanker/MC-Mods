package com.silver.skyislands.dragonbreath;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DragonBreathTracking {
    private static final Logger LOGGER = LoggerFactory.getLogger(DragonBreathTracking.class);

    private DragonBreathTracking() {
    }

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world == null || world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }

            ItemStack inHand = serverPlayer.getItemInHand(hand);
            if (!SpecialDragonBreathItem.isSpecialDragonBreath(inHand)) {
                return InteractionResult.PASS;
            }

            return handleSpecialUse(serverPlayer, hand, inHand);
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragonbreath] init complete (id-only NBT)");
        }
    }

    private static InteractionResult handleSpecialUse(ServerPlayer player, InteractionHand hand, ItemStack inHand) {
        int usesLeft = SpecialDragonBreathItem.getUsesLeft(inHand);
        int usesMax = SpecialDragonBreathItem.getUsesMax(inHand);

        if (usesLeft <= 0) {
            // Invalid/exhausted; delete one bottle.
            deleteOneFromHand(player, hand, inHand);
            player.sendSystemMessage(Component.literal("Your Special Dragon Breath is exhausted."), false);
            return InteractionResult.SUCCESS;
        }

        // Find nearest dragon (virtual or loaded) and print once.
        EnderDragonManager.DragonLocatorResult nearest = EnderDragonManager.findNearestDragonFor(player);
        if (nearest == null) {
            player.sendSystemMessage(Component.literal("No dragons found."), false);
        } else {
            Vec3 p = nearest.pos();
            float yaw = nearest.headingYawDegrees();
            player.sendSystemMessage(Component.literal("Nearest dragon: x=" + Mth.floor(p.x) + " y=" + Mth.floor(p.y) + " z=" + Mth.floor(p.z) + " headingYaw=" + Mth.floor(yaw)), false);
        }

        int newUses = usesLeft - 1;
        if (newUses <= 0) {
            deleteOneFromHand(player, hand, inHand);
            return InteractionResult.SUCCESS;
        }

        if (inHand.getCount() > 1) {
            // Split one bottle off the stack with reduced uses.
            ItemStack reduced = SpecialDragonBreathItem.copySingleWithUses(inHand, newUses);
            if (!tryInsertOne(player.getInventory(), reduced)) {
                player.sendSystemMessage(Component.literal("You don't have room in your inventory."), false);
                return InteractionResult.SUCCESS;
            }

            inHand.shrink(1);
            return InteractionResult.SUCCESS;
        }

        // Single bottle in hand; mutate in place.
        SpecialDragonBreathItem.setUsesLeft(inHand, newUses);
        SpecialDragonBreathItem.setDisplayName(inHand, newUses, usesMax);
        return InteractionResult.SUCCESS;
    }

    private static void deleteOneFromHand(ServerPlayer player, InteractionHand hand, ItemStack inHand) {
        if (inHand.getCount() > 1) {
            inHand.shrink(1);
            return;
        }
        player.setItemInHand(hand, ItemStack.EMPTY);
    }

    private static boolean tryInsertOne(Inventory inv, ItemStack single) {
        if (inv == null || single == null || single.isEmpty() || single.getCount() != 1) {
            return false;
        }

        // First try to merge with an existing compatible stack.
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existing, single) && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(1);
                return true;
            }
        }

        // Then look for an empty slot.
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing.isEmpty()) {
                inv.setItem(slot, single);
                return true;
            }
        }

        return false;
    }
}
