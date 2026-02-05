package com.silver.skyislands.specialitems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public final class SpecialFeatherItem {
    public static final String KEY_SPECIAL_ID = "SpecialItemId";
    public static final String SPECIAL_ID = "Special Feather";

    private SpecialFeatherItem() {
    }

    public static ItemStack createOne() {
        ItemStack stack = new ItemStack(Items.FEATHER, 1);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> tag.putString(KEY_SPECIAL_ID, SPECIAL_ID));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Special Feather"));
        return stack;
    }

    public static boolean isSpecialFeather(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FEATHER)) {
            return false;
        }
        NbtCompound nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }
        String id = nbt.getString(KEY_SPECIAL_ID).orElse("");
        return SPECIAL_ID.equals(id);
    }

    private static NbtCompound readCustomData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyNbt();
    }
}
