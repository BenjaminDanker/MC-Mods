package com.silver.enderfight.dragon;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Decorates dragon breath collection with a custom item payload. The interception happens via the
 * {@link com.silver.enderfight.mixin.GlassBottleItemMixin} but the mutation entry point is concentrated
 * here so the tagging logic stays reusable.
 */
public final class DragonBreathModifier {
    public static final int MAX_SPECIAL_DRAGON_BREATH_BOTTLES = 3;

    private static ConfigManager configManager;

    private static final String KEY_SPECIAL_ID = "id";
    private static final String KEY_ID_TYPE = "idType";
    private static final String KEY_USES_LEFT = "TrackingUsesLeft";
    private static final String KEY_USES_MAX = "TrackingUsesMax";

    private DragonBreathModifier() {
    }

    public static void register(ConfigManager configManager) {
        DragonBreathModifier.configManager = configManager;
    }

    /**
     * Creates the bespoke dragon breath item once the vanilla interaction completes. Tie custom NBT or
     * component data together here so the rest of the mod can query a single flag.
     */
    public static boolean markAsSpecialDragonBreath(ItemStack stack, Level world) {
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

        if (!stack.is(Items.DRAGON_BREATH)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: stack {} is not dragon breath", stack);
            return false;
        }

        if (isSpecialDragonBreath(stack)) {
            EnderFightMod.LOGGER.info("Dragon breath tagging skipped: stack {} is already tagged", stack);
            return false;
        }

        EnderFightMod.LOGGER.info("Tagging dragon breath stack {} at tick {} in world {}", stack, world.getGameTime(),
            world.dimension().identifier());

        final int usesDefault = getTrackingUsesDefault();
        final String specialId = getSpecialBreathId();

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_SPECIAL_ID, specialId);
            tag.putString(KEY_ID_TYPE, "Soulbound");
            tag.putInt(KEY_USES_LEFT, usesDefault);
            tag.putInt(KEY_USES_MAX, usesDefault);
            tag.putLong("CapturedTick", world.getGameTime());
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(buildDisplayName(usesDefault, usesDefault)));
        EnderFightMod.LOGGER.info("Tagged dragon breath bottle with custom metadata at tick {}", world.getGameTime());
        return true;
    }

    public static boolean isSpecialDragonBreath(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.DRAGON_BREATH)) {
            return false;
        }

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }

        CompoundTag nbt = custom.copyTag();
        if (nbt == null) {
            return false;
        }

        String expectedId = getSpecialBreathId();
        String id = nbt.getString(KEY_SPECIAL_ID).orElse("");
        if (!expectedId.equals(id)) {
            return false;
        }

        String idType = nbt.getString(KEY_ID_TYPE).orElse("");
        if (!"Soulbound".equals(idType)) {
            return false;
        }

        return nbt.getInt(KEY_USES_LEFT).isPresent() && nbt.getInt(KEY_USES_MAX).isPresent();
    }

    public static int getTrackingUsesLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.DRAGON_BREATH)) {
            return 0;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return 0;
        }
        CompoundTag nbt = custom.copyTag();
        if (nbt == null) {
            return 0;
        }
        return nbt.getInt(KEY_USES_LEFT).orElse(0);
    }

    public static int getTrackingUsesMax(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.DRAGON_BREATH)) {
            return 0;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return 0;
        }
        CompoundTag nbt = custom.copyTag();
        if (nbt == null) {
            return 0;
        }
        return nbt.getInt(KEY_USES_MAX).orElse(0);
    }

    public static int countSpecialDragonBreath(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSpecialDragonBreath(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean hasReachedSpecialDragonBreathLimit(Inventory inventory) {
        return countSpecialDragonBreath(inventory) >= MAX_SPECIAL_DRAGON_BREATH_BOTTLES;
    }

    /**
     * Removes any additional special dragon breath bottles from the player so they can only carry up to three.
     * Returns the number of bottles deleted for logging/metrics.
     */
    public static int purgeExtraSpecialDragonBreath(ServerPlayer player, String context) {
        if (player == null) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            EnderFightMod.LOGGER.info("Skipping dragon breath cleanup for {}: inventory unavailable (context={})",
                player.getName().getString(), context);
            return 0;
        }

        int keptCount = 0;
        int removed = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isSpecialDragonBreath(stack)) {
                continue;
            }

            int stackCount = stack.getCount();
            if (keptCount + stackCount <= MAX_SPECIAL_DRAGON_BREATH_BOTTLES) {
                // We have room to keep this entire stack
                keptCount += stackCount;
                continue;
            }

            // We need to keep some (or none) and remove the rest
            int keepFromThisStack = Math.max(0, MAX_SPECIAL_DRAGON_BREATH_BOTTLES - keptCount);
            int removeFromThisStack = stackCount - keepFromThisStack;
            
            keptCount += keepFromThisStack;
            removed += removeFromThisStack;

            if (keepFromThisStack > 0) {
                stack.setCount(keepFromThisStack);
            } else {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }

        if (removed > 0) {
            EnderFightMod.LOGGER.info("Removed {} extra special dragon breath bottles from {} ({})", removed,
                player.getName().getString(), context);
        } else {
            EnderFightMod.LOGGER.debug("No extra special dragon breath bottles found for {} ({})",
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

    private static int getTrackingUsesDefault() {
        if (configManager == null) {
            return EndControlConfig.DEFAULT_CUSTOM_BREATH_TRACKING_USES;
        }
        EndControlConfig config = configManager.getConfig();
        if (config == null) {
            return EndControlConfig.DEFAULT_CUSTOM_BREATH_TRACKING_USES;
        }
        int uses = config.customBreathTrackingUsesDefault();
        return uses > 0 ? uses : EndControlConfig.DEFAULT_CUSTOM_BREATH_TRACKING_USES;
    }

    private static String getSpecialBreathId() {
        if (configManager == null) {
            return EndControlConfig.DEFAULT_CUSTOM_BREATH_ID;
        }
        EndControlConfig config = configManager.getConfig();
        if (config == null || config.customBreathId() == null || config.customBreathId().isBlank()) {
            return EndControlConfig.DEFAULT_CUSTOM_BREATH_ID;
        }
        return config.customBreathId();
    }

    private static String buildDisplayName(int usesLeft, int usesMax) {
        return "Special Dragon Breath (" + usesLeft + "/" + usesMax + ")";
    }
}
