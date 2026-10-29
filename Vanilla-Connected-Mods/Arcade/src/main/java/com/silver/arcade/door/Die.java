package com.silver.arcade.door;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Gamemaster's Die: given after a first visit (and by his altar near each spawn). Soulbound (it travels between
 * servers, and does not take a soulbound slot). Right-click to open a door.
 */
public final class Die {
    static final String ID = "gamemaster_die";

    private Die() {
    }

    public static ItemStack create() {
        ItemStack die = new ItemStack(Items.ECHO_SHARD);
        CompoundTag data = new CompoundTag();
        data.putString("id", ID);
        data.putString("idType", "Soulbound");
        die.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        die.set(DataComponents.ITEM_NAME, Component.literal("Gamemaster's Die").withStyle(ChatFormatting.LIGHT_PURPLE));
        die.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        die.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Roll it (right-click) to open his door.").withStyle(ChatFormatting.GRAY),
            Component.literal("Friends nearby may follow you through.").withStyle(ChatFormatting.DARK_GRAY))));
        return die;
    }

    public static boolean is(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && ID.equals(data.copyTag().getStringOr("id", ""));
    }

    public static boolean carries(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (is(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }
}
