package com.silver.skyislands.dragonbreath;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

public final class SpecialDragonBreathItem {
    public static final String KEY_SPECIAL_ID = "SpecialDragonBreathId";
    public static final String KEY_USES_LEFT = "TrackingUsesLeft";
    public static final String KEY_USES_MAX = "TrackingUsesMax";

    // Default namespace aligns with where the item is minted (Ender-Fight).
    public static final String DEFAULT_SPECIAL_ID = "enderfight:special_dragon_breath";

    private SpecialDragonBreathItem() {
    }

    public static boolean isSpecialDragonBreath(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.DRAGON_BREATH)) {
            return false;
        }

        NbtCompound nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }

        String expected = DEFAULT_SPECIAL_ID;
        String id = nbt.getString(KEY_SPECIAL_ID).orElse("");
        if (!expected.equals(id)) {
            return false;
        }

        // Strict requirement: uses-left must be present for it to be a valid special bottle.
        return nbt.getInt(KEY_USES_LEFT).isPresent() && nbt.getInt(KEY_USES_MAX).isPresent();
    }

    public static int getUsesLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        NbtCompound nbt = readCustomData(stack);
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
        NbtCompound nbt = readCustomData(stack);
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
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> tag.putInt(KEY_USES_LEFT, Math.max(0, usesLeft)));
    }

    public static void setDisplayName(ItemStack stack, int usesLeft, int usesMax) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal(buildDisplayName(usesLeft, usesMax)));
    }

    public static ItemStack copySingleWithUses(ItemStack template, int usesLeft) {
        ItemStack single = template.copy();
        single.setCount(1);

        int usesMax = getUsesMax(template);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, single, tag -> {
            tag.putString(KEY_SPECIAL_ID, DEFAULT_SPECIAL_ID);
            tag.putInt(KEY_USES_LEFT, Math.max(0, usesLeft));
            tag.putInt(KEY_USES_MAX, Math.max(0, usesMax));
        });

        setDisplayName(single, Math.max(0, usesLeft), Math.max(0, usesMax));
        return single;
    }

    private static String buildDisplayName(int usesLeft, int usesMax) {
        return "Special Dragon Breath (" + usesLeft + "/" + usesMax + ")";
    }

    private static NbtCompound readCustomData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyNbt();
    }
}
