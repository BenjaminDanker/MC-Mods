package com.silver.playersync;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * How items are stored in the player table: one item as JSON, a list of items as {@code json~slot&json~slot&...}
 * (the slot is where the item sat, so it goes back there). This format is what the table already holds.
 */
final class ItemCodec {
    /** Custom data marking an item that follows its owner everywhere ({@code idType} = Soulbound or CraftedSoulbound). */
    private static final String SOULBOUND_KEY = "idType";

    private ItemCodec() {
    }

    record Placed(int slot, ItemStack stack) {
    }

    static boolean soulbound(ItemStack stack) {
        String type = customString(stack, SOULBOUND_KEY);
        return "Soulbound".equals(type) || "CraftedSoulbound".equals(type);
    }

    /** True when the item's custom data has {@code key} = {@code value}. */
    static boolean hasCustom(ItemStack stack, String key, String value) {
        return value != null && value.equals(customString(stack, key));
    }

    private static String customString(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag().getString(key).orElse(null);
    }

    static String encode(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(PlayerSyncMod.ops(), stack).resultOrPartial(PlayerSyncMod.LOGGER::error).orElseThrow().toString();
    }

    /** The item stored as {@code json}, or empty when it can't be read. */
    static ItemStack decode(String json) {
        if (json == null || json.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(PlayerSyncMod.ops(), JsonParser.parseString(json))
            .resultOrPartial(PlayerSyncMod.LOGGER::error).orElse(ItemStack.EMPTY);
    }

    /** Appends {@code stack} at {@code slot} to a stored list. */
    static void append(StringBuilder list, ItemStack stack, int slot) {
        list.append(encode(stack)).append('~').append(slot).append('&');
    }

    /** Encodes the first {@code count} slots of {@code container} that pass {@code keep}. */
    static String encodeSlots(Container container, int count, java.util.function.Predicate<ItemStack> keep) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && keep.test(stack)) {
                append(out, stack, slot);
            }
        }
        return out.toString();
    }

    /**
     * Reads a stored list to give it back to a player. An entry that can't be read stops the whole load (the player
     * then isn't saved over, so nothing they had stored is lost).
     */
    static List<Placed> decodeList(String stored) {
        List<Placed> items = new ArrayList<>();
        for (Entry entry : entries(stored)) {
            items.add(new Placed(entry.slot(), decodeStrict(entry.json())));
        }
        return items;
    }

    /** The item stored as {@code json}; throws when it can't be read. */
    static ItemStack decodeStrict(String json) {
        ItemStack stack = decode(json);
        if (stack.isEmpty() && json != null && !json.isEmpty()) {
            throw new IllegalStateException("unreadable stored item: " + json);
        }
        return stack;
    }

    /** One raw list entry; {@code slot} is -1 when the entry has no slot part. */
    record Entry(String json, int slot, String raw) {
    }

    static List<Entry> entries(String stored) {
        List<Entry> entries = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return entries;
        }
        for (String raw : stored.split("&")) {
            if (raw.isEmpty()) {
                continue;
            }
            int cut = raw.lastIndexOf('~');
            int slot = -1;
            String json = raw;
            if (cut > 0 && cut < raw.length() - 1) {
                try {
                    slot = Integer.parseInt(raw.substring(cut + 1));
                    json = raw.substring(0, cut);
                } catch (NumberFormatException notASlot) {
                    // keep the whole entry as the item
                }
            }
            entries.add(new Entry(json, slot, raw));
        }
        return entries;
    }
}
