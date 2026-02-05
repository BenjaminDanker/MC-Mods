package com.silver.skyislands.specialitems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public final class SpecialArrowItem {
    public static final String KEY_ID = "id";
    public static final String KEY_ID_TYPE = "idType";
    public static final String ID = "special_arrow";

    private SpecialArrowItem() {
    }

    public static ItemStack createOne() {
        ItemStack stack = new ItemStack(Items.ARROW, 1);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_ID, ID);
            tag.putString(KEY_ID_TYPE, "Soulbound");
        });
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Special Arrow"));
        return stack;
    }

    public static boolean isSpecialArrow(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.ARROW)) {
            return false;
        }
        NbtCompound nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }
        String id = nbt.getString(KEY_ID).orElse("");
        String idType = nbt.getString(KEY_ID_TYPE).orElse("");
        return ID.equals(id) && "Soulbound".equals(idType);
    }

    private static NbtCompound readCustomData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyNbt();
    }
}
