package com.silver.atlantis.spawn.drop;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

}
