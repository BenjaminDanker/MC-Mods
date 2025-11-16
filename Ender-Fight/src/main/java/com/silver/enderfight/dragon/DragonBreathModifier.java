package com.silver.enderfight.dragon;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * Decorates dragon breath collection with a custom item payload. The interception happens via the
 * {@link com.silver.enderfight.mixin.GlassBottleItemMixin} but the mutation entry point is concentrated
 * here so the tagging logic stays reusable.
 */
public final class DragonBreathModifier {
    private static ConfigManager configManager;

    private DragonBreathModifier() {
    }

    public static void register(ConfigManager configManager) {
        DragonBreathModifier.configManager = configManager;
    }

    /**
     * Creates the bespoke dragon breath item once the vanilla interaction completes. Tie custom NBT or
     * component data together here so the rest of the mod can query a single flag.
     */
    public static boolean markAsSpecialDragonBreath(ItemStack stack, World world) {
        if (stack == null) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: stack was null");
            return false;
        }

        if (world == null) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: world reference was null for stack {}", stack);
            return false;
        }

        if (!isCustomBreathEnabled()) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: custom breath feature disabled");
            return false;
        }

        if (!stack.isOf(Items.DRAGON_BREATH)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: stack {} is not dragon breath", stack);
            return false;
        }

        if (isSpecialDragonBreath(stack)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: stack {} is already tagged", stack);
            return false;
        }

        EnderFightMod.LOGGER.info("Tagging dragon breath stack {} at tick {} in world {}", stack, world.getTime(),
            world.getRegistryKey().getValue());
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("CustomDragonBreath", true);
            tag.putLong("CapturedTick", world.getTime());
        });
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Special Dragon Breath"));
        EnderFightMod.LOGGER.info("Tagged dragon breath bottle with custom metadata at tick {}", world.getTime());
        return true;
    }

    public static boolean isSpecialDragonBreath(ItemStack stack) {
        if (stack == null || !stack.isOf(Items.DRAGON_BREATH)) {
            return false;
        }

        Text name = stack.getName();
        return name != null && "Special Dragon Breath".equals(name.getString());
    }

    /**
     * Removes any additional special dragon breath bottles from the player so they can only carry one.
     * Returns the number of bottles deleted for logging/metrics.
     */
    // Lol, very very special dragon breath
    // Extra as in More than one Special Dragon Breath
    public static int purgeExtraSpecialDragonBreath(ServerPlayerEntity player, String context) {
        if (player == null) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        if (inventory == null) {
            EnderFightMod.LOGGER.info("Skipping dragon breath cleanup for {}: inventory unavailable (context={})",
                player.getName().getString(), context);
            return 0;
        }

        boolean keptOne = false;
        int removed = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isSpecialDragonBreath(stack)) {
                continue;
            }

            if (!keptOne) {
                keptOne = true;
                continue;
            }

            removed += stack.getCount();
            inventory.setStack(slot, ItemStack.EMPTY);
        }

        if (removed > 0) {
            EnderFightMod.LOGGER.info("Removed {} extra special dragon breath bottles from {} ({})", removed,
                player.getName().getString(), context);
        } else {
            EnderFightMod.LOGGER.info("No extra special dragon breath bottles found for {} ({})",
                player.getName().getString(), context);
        }
        return removed;
    }

    private static boolean isCustomBreathEnabled() {
        if (configManager == null) {
            return false;
        }
        EndControlConfig config = configManager.getConfig();
        return config != null && config.customBreathEnabled();
    }
}
