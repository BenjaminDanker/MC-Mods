package com.silver.playersync;

import java.sql.SQLException;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Finding and removing items marked with a custom data {@code key} = {@code value}: in a player's stored
 * (between-server) items and in what they carry right now. Used when an item must be taken back everywhere, e.g. a
 * portal ticket that was used up.
 */
final class StoredItems {
    private StoredItems() {
    }

    /** Removes matching items from the player's stored items; returns how many. Database: off the server thread. */
    static int removeStored(String uuid, String key, String value) throws SQLException {
        Optional<SyncStore.Stored> found = SyncStore.find(uuid);
        if (found.isEmpty()) {
            return 0;
        }
        PlayerSnapshot.Saved saved = found.get().saved();
        Filtered main = filterList(saved.main(), key, value);
        Filtered armor = filterList(saved.armor(), key, value);
        Filtered ender = filterList(saved.enderChestInventory(), key, value);
        Filtered off = matches(ItemCodec.decode(saved.off()), key, value) ? new Filtered("", 1) : new Filtered(saved.off(), 0);
        int removed = main.removed() + armor.removed() + ender.removed() + off.removed();
        if (removed > 0) {
            SyncStore.replaceInventories(uuid, main.kept(), off.kept(), armor.kept(), ender.kept());
        }
        return removed;
    }

    static int countStored(String uuid, String key, String value) throws SQLException {
        Optional<SyncStore.Stored> found = SyncStore.find(uuid);
        if (found.isEmpty()) {
            return 0;
        }
        PlayerSnapshot.Saved saved = found.get().saved();
        int count = matches(ItemCodec.decode(saved.off()), key, value) ? 1 : 0;
        for (String list : new String[] {saved.main(), saved.armor(), saved.enderChestInventory()}) {
            for (ItemCodec.Entry entry : ItemCodec.entries(list)) {
                if (matches(ItemCodec.decode(entry.json()), key, value)) {
                    count++;
                }
            }
        }
        return count;
    }

    private record Filtered(String kept, int removed) {
    }

    /** Drops matching entries from a stored list; entries that can't be read are kept, never thrown away. */
    private static Filtered filterList(String stored, String key, String value) {
        StringBuilder kept = new StringBuilder();
        int removed = 0;
        for (ItemCodec.Entry entry : ItemCodec.entries(stored)) {
            if (entry.slot() >= 0 && matches(ItemCodec.decode(entry.json()), key, value)) {
                removed++;
            } else {
                kept.append(entry.raw()).append('&');
            }
        }
        return new Filtered(kept.toString(), removed);
    }

    private static boolean matches(ItemStack stack, String key, String value) {
        return !stack.isEmpty() && ItemCodec.hasCustom(stack, key, value);
    }

    /** Server thread: removes matching items from what the player carries (inventory and ender chest); returns how many. */
    static int removeCarried(ServerPlayer player, String key, String value) {
        int removed = removeFrom(player.getInventory(), key, value) + removeFrom(player.getEnderChestInventory(), key, value);
        if (removed > 0) {
            player.getInventory().setChanged();
            player.getEnderChestInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    static int countCarried(ServerPlayer player, String key, String value) {
        return countIn(player.getInventory(), key, value) + countIn(player.getEnderChestInventory(), key, value);
    }

    private static int removeFrom(Container container, String key, String value) {
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (matches(container.getItem(slot), key, value)) {
                container.setItem(slot, ItemStack.EMPTY);
                removed++;
            }
        }
        return removed;
    }

    private static int countIn(Container container, String key, String value) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (matches(container.getItem(slot), key, value)) {
                count++;
            }
        }
        return count;
    }
}
