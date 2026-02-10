package com.silver.atlantis.spawn;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class SpecialSeaLanternItem {
    private SpecialSeaLanternItem() {
    }

    public static ItemStack createOne() {
        Identifier id = Identifier.tryParse(SpawnSpecialConfig.SPECIAL_SEA_LANTERN_ITEM_ID);
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item = Registries.ITEM.get(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, 1);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(SpawnSpecialConfig.SPECIAL_SEA_LANTERN_DISPLAY_NAME));

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, tag -> {
            tag.putString("id", SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ID);
            tag.putString("idType", SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ITEM_TYPE);
        });

        return stack;
    }

    public static boolean isSpecialSeaLantern(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        NbtCompound nbt = readCustomData(stack);
        if (nbt == null) {
            return false;
        }
        String id = nbt.getString("id").orElse("");
        String idType = nbt.getString("idType").orElse("");
        return SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ID.equals(id)
            && SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ITEM_TYPE.equals(idType);
    }

    private static NbtCompound readCustomData(ItemStack stack) {
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) {
            return null;
        }
        return custom.copyNbt();
    }
}
