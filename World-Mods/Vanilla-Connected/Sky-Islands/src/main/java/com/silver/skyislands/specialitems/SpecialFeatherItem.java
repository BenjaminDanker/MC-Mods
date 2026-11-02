package com.silver.skyislands.specialitems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class SpecialFeatherItem {
    public static final String KEY_ID = "id";
    public static final String ID = "special_feather";

    private SpecialFeatherItem() {
    }

    public static ItemStack createOne() {
        ItemStack stack = new ItemStack(Items.FEATHER, 1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_ID, ID);
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Special Feather"));
        return stack;
    }

    public static boolean isSpecialFeather(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.FEATHER.builtInRegistryHolder())) {
            return false;
        }
        CompoundTag nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }
        String id = nbt.getString(KEY_ID).orElse("");
        return ID.equals(id);
    }

    private static CompoundTag readCustomData(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyTag();
    }
}
