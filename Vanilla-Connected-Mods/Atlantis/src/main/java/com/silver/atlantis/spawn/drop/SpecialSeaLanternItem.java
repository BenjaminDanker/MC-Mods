package com.silver.atlantis.spawn.drop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SpecialSeaLanternItem {
    private SpecialSeaLanternItem() {
    }

    public static ItemStack createOne() {
        Identifier id = Identifier.tryParse(SpawnSpecialConfig.SPECIAL_SEA_LANTERN_ITEM_ID);
        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(SpawnSpecialConfig.SPECIAL_SEA_LANTERN_DISPLAY_NAME));

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("id", SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ID);
            tag.putString("idType", SpawnSpecialConfig.SPECIAL_SEA_LANTERN_CUSTOM_ITEM_TYPE);
        });

        return stack;
    }

}
