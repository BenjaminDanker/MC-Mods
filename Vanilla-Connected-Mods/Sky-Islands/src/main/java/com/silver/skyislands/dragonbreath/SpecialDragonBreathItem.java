package com.silver.skyislands.dragonbreath;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class SpecialDragonBreathItem {
    public static final String KEY_SPECIAL_ID = "id";
    public static final String KEY_ID_TYPE = "idType";
    public static final String KEY_USES_LEFT = "TrackingUsesLeft";
    public static final String KEY_USES_MAX = "TrackingUsesMax";

    public static final String DEFAULT_SPECIAL_ID = "special_dragon_breath";
    public static final int DEFAULT_TRACKING_USES = 5;
    public static final int MAX_SPECIAL_DRAGON_BREATH_BOTTLES = 3;

    private SpecialDragonBreathItem() {
    }

    public static boolean markAsSpecialDragonBreath(ItemStack stack, Level world) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.DRAGON_BREATH.builtInRegistryHolder())) {
            return false;
        }
        if (isSpecialDragonBreath(stack)) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_SPECIAL_ID, DEFAULT_SPECIAL_ID);
            tag.putString(KEY_ID_TYPE, "Soulbound");
            tag.putInt(KEY_USES_LEFT, DEFAULT_TRACKING_USES);
            tag.putInt(KEY_USES_MAX, DEFAULT_TRACKING_USES);
            if (world != null) {
                tag.putLong("CapturedTick", world.getGameTime());
            }
        });
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal(buildDisplayName(DEFAULT_TRACKING_USES, DEFAULT_TRACKING_USES)));
        return true;
    }

    public static boolean isSpecialDragonBreath(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.DRAGON_BREATH.builtInRegistryHolder())) {
            return false;
        }

        CompoundTag nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }

        String expected = DEFAULT_SPECIAL_ID;
        String id = nbt.getString(KEY_SPECIAL_ID).orElse("");
        if (!expected.equals(id)) {
            return false;
        }

        String idType = nbt.getString(KEY_ID_TYPE).orElse("");
        if (!"Soulbound".equals(idType)) {
            return false;
        }

        // Strict requirement: uses-left must be present for it to be a valid special bottle.
        return nbt.getInt(KEY_USES_LEFT).isPresent() && nbt.getInt(KEY_USES_MAX).isPresent();
    }

    public static int getUsesLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CompoundTag nbt = readCustomData(stack);
        if (nbt == null) {
            return 0;
        }

        int uses = nbt.getInt(KEY_USES_LEFT).orElse(0);
        return Math.max(0, uses);
    }

    public static int getUsesMax(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        CompoundTag nbt = readCustomData(stack);
        if (nbt == null) {
            return 0;
        }
        int uses = nbt.getInt(KEY_USES_MAX).orElse(0);
        return Math.max(0, uses);
    }

    public static void setUsesLeft(ItemStack stack, int usesLeft) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY_USES_LEFT, Math.max(0, usesLeft)));
    }

    public static void setDisplayName(ItemStack stack, int usesLeft, int usesMax) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(buildDisplayName(usesLeft, usesMax)));
    }

    public static ItemStack copySingleWithUses(ItemStack template, int usesLeft) {
        ItemStack single = template.copy();
        single.setCount(1);

        int usesMax = getUsesMax(template);
        CustomData.update(DataComponents.CUSTOM_DATA, single, tag -> {
            tag.putString(KEY_SPECIAL_ID, DEFAULT_SPECIAL_ID);
            tag.putString(KEY_ID_TYPE, "Soulbound");
            tag.putInt(KEY_USES_LEFT, Math.max(0, usesLeft));
            tag.putInt(KEY_USES_MAX, Math.max(0, usesMax));
        });

        setDisplayName(single, Math.max(0, usesLeft), Math.max(0, usesMax));
        return single;
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

    public static int purgeExtraSpecialDragonBreath(ServerPlayer player, String context) {
        if (player == null) {
            return 0;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
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
                keptCount += stackCount;
                continue;
            }

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

        return removed;
    }

    private static String buildDisplayName(int usesLeft, int usesMax) {
        return "Special Dragon Breath (" + usesLeft + "/" + usesMax + ")";
    }

    private static CompoundTag readCustomData(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyTag();
    }
}
