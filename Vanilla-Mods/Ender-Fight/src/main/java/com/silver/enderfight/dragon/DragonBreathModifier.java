package com.silver.enderfight.dragon;

import com.silver.enderfight.EnderFightMod;
import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.config.EndControlConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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

        final int usesDefault = getTrackingUsesDefault();
        final String specialId = getSpecialBreathId();

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_SPECIAL_ID, specialId);
            tag.putString(KEY_ID_TYPE, "Soulbound");
            tag.putInt(KEY_USES_LEFT, usesDefault);
            tag.putInt(KEY_USES_MAX, usesDefault);
            tag.putLong("CapturedTick", world.getTime());
        });
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(buildDisplayName(usesDefault, usesDefault)));
        EnderFightMod.LOGGER.info("Tagged dragon breath bottle with custom metadata at tick {}", world.getTime());
        return true;
    }

    public static boolean isSpecialDragonBreath(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.DRAGON_BREATH)) {
            return false;
        }

        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return false;
        }

        NbtCompound nbt = custom.copyNbt();
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
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.DRAGON_BREATH)) {
            return 0;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return 0;
        }
        NbtCompound nbt = custom.copyNbt();
        if (nbt == null) {
            return 0;
        }
        return nbt.getInt(KEY_USES_LEFT).orElse(0);
    }

    public static int getTrackingUsesMax(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.DRAGON_BREATH)) {
            return 0;
        }
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return 0;
        }
        NbtCompound nbt = custom.copyNbt();
        if (nbt == null) {
            return 0;
        }
        return nbt.getInt(KEY_USES_MAX).orElse(0);
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
