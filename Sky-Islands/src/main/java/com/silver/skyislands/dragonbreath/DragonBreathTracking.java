package com.silver.skyislands.dragonbreath;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DragonBreathTracking {
    private static final Logger LOGGER = LoggerFactory.getLogger(DragonBreathTracking.class);

    private DragonBreathTracking() {
    }

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world == null || world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            ItemStack inHand = serverPlayer.getStackInHand(hand);
            if (!SpecialDragonBreathItem.isSpecialDragonBreath(inHand)) {
                return ActionResult.PASS;
            }

            return handleSpecialUse(serverPlayer, hand, inHand);
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragonbreath] init complete (id-only NBT)");
        }
    }

    private static ActionResult handleSpecialUse(ServerPlayerEntity player, Hand hand, ItemStack inHand) {
        int usesLeft = SpecialDragonBreathItem.getUsesLeft(inHand);
        int usesMax = SpecialDragonBreathItem.getUsesMax(inHand);

        if (usesLeft <= 0) {
            // Invalid/exhausted; delete one bottle.
            deleteOneFromHand(player, hand, inHand);
            player.sendMessage(Text.literal("Your Special Dragon Breath is exhausted."), false);
            return ActionResult.SUCCESS;
        }

        // Find nearest dragon (virtual or loaded) and print once.
        EnderDragonManager.DragonLocatorResult nearest = EnderDragonManager.findNearestDragonFor(player);
        if (nearest == null) {
            player.sendMessage(Text.literal("No dragons found."), false);
        } else {
            Vec3d p = nearest.pos();
            float yaw = nearest.headingYawDegrees();
            player.sendMessage(Text.literal("Nearest dragon: x=" + MathHelper.floor(p.x) + " y=" + MathHelper.floor(p.y) + " z=" + MathHelper.floor(p.z) + " headingYaw=" + MathHelper.floor(yaw)), false);
        }

        int newUses = usesLeft - 1;
        if (newUses <= 0) {
            deleteOneFromHand(player, hand, inHand);
            return ActionResult.SUCCESS;
        }

        if (inHand.getCount() > 1) {
            // Split one bottle off the stack with reduced uses.
            ItemStack reduced = SpecialDragonBreathItem.copySingleWithUses(inHand, newUses);
            if (!tryInsertOne(player.getInventory(), reduced)) {
                player.sendMessage(Text.literal("You don't have room in your inventory."), false);
                return ActionResult.SUCCESS;
            }

            inHand.decrement(1);
            return ActionResult.SUCCESS;
        }

        // Single bottle in hand; mutate in place.
        SpecialDragonBreathItem.setUsesLeft(inHand, newUses);
        SpecialDragonBreathItem.setDisplayName(inHand, newUses, usesMax);
        return ActionResult.SUCCESS;
    }

    private static void deleteOneFromHand(ServerPlayerEntity player, Hand hand, ItemStack inHand) {
        if (inHand.getCount() > 1) {
            inHand.decrement(1);
            return;
        }
        player.setStackInHand(hand, ItemStack.EMPTY);
    }

    private static boolean tryInsertOne(PlayerInventory inv, ItemStack single) {
        if (inv == null || single == null || single.isEmpty() || single.getCount() != 1) {
            return false;
        }

        // First try to merge with an existing compatible stack.
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack existing = inv.getStack(slot);
            if (existing.isEmpty()) {
                continue;
            }
            if (ItemStack.areItemsAndComponentsEqual(existing, single) && existing.getCount() < existing.getMaxCount()) {
                existing.increment(1);
                return true;
            }
        }

        // Then look for an empty slot.
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack existing = inv.getStack(slot);
            if (existing.isEmpty()) {
                inv.setStack(slot, single);
                return true;
            }
        }

        return false;
    }
}
