package com.silver.enderfight.dragon;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.GlassBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * Decorates dragon breath collection with a custom item payload. The actual interception relies on
 * Fabric's {@link UseItemCallback}; should the underlying behaviour change, migrate the logic to an
 * appropriate mixin but keep the mutation entry point concentrated in this class.
 */
public final class DragonBreathModifier {
    private DragonBreathModifier() {
    }

    public static void register(ConfigManager configManager) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            EndControlConfig config = configManager.getConfig();
            if (!config.customBreathEnabled()) {
                return ActionResult.PASS;
            }

            if (!(stack.getItem() instanceof GlassBottleItem)) {
                return ActionResult.PASS;
            }

            return handleDragonBreathCapture(serverPlayer, world, hand, stack);
        });
    }

    private static ActionResult handleDragonBreathCapture(ServerPlayerEntity player, World world, Hand hand, ItemStack stack) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        // Delegate to a stub so custom behaviour stays isolated and testable.
        ActionResult result = transformCollectedItem(stack, world);
        if (result instanceof ActionResult.Success success) {
            ItemStack mutated = success.getNewHandStack();
            player.setStackInHand(hand, mutated);
            player.playSound(SoundEvents.ITEM_BOTTLE_FILL_DRAGONBREATH, 1.0F, 1.0F);
            return success;
        }

        return ActionResult.PASS;
    }

    /**
     * Creates the bespoke dragon breath item once the vanilla interaction completes. Tie custom NBT or
     * component data together here so the rest of the mod can query a single flag.
     */
    public static ActionResult transformCollectedItem(ItemStack baseStack, World world) {
        if (baseStack.getItem() != Items.DRAGON_BREATH) {
            // In vanilla this would already be dragon breath at this point; we short-circuit otherwise.
            return ActionResult.PASS;
        }

        ItemStack mutated = baseStack.copy();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, mutated, tag -> {
            tag.putBoolean("CustomDragonBreath", true);
            tag.putLong("CapturedTick", world.getTime());
        });
        EnderFightMod.LOGGER.debug("Tagged dragon breath bottle with custom metadata");
        return ActionResult.SUCCESS.withNewHandStack(mutated);
    }
}
