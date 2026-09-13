package com.silver.skyislands.specialitems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public final class SpecialArrowItem {
    public static final String KEY_ID = "id";
    public static final String KEY_ID_TYPE = "idType";
    public static final String ID = "special_arrow";

    private SpecialArrowItem() {
    }

    public static ItemStack createOne() {
        ItemStack stack = new ItemStack(Items.ARROW, 1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(KEY_ID, ID);
            tag.putString(KEY_ID_TYPE, "Soulbound");
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Special Arrow"));
        return stack;
    }

    public static boolean isSpecialArrow(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.ARROW.builtInRegistryHolder())) {
            return false;
        }
        CompoundTag nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }
        String id = nbt.getString(KEY_ID).orElse("");
        String idType = nbt.getString(KEY_ID_TYPE).orElse("");
        return ID.equals(id) && "Soulbound".equals(idType);
    }

    private static CompoundTag readCustomData(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyTag();
    }
}
