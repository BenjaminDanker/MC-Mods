package com.silver.soulbounditems.soulbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps crafted Soulbound stacks out of the death drop. They stay in the dead player's inventory, so a logout on the
 * death screen saves them (and Player Sync sees them), and are copied to the new player when it respawns.
 */
public final class SoulboundDeathRetention {
    private SoulboundDeathRetention() {
    }

    /** Takes Soulbound stacks out of the inventory before the death drop; returns null when there are none. */
    public static ItemStack[] holdBack(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        ItemStack[] held = new ItemStack[size];
        boolean found = false;

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!SoulboundRules.isSoulboundItem(stack)) {
                continue;
            }
            held[slot] = stack;
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            found = true;
        }
        return found ? held : null;
    }

    /** Puts the held stacks back into their slots once the death drop is done. */
    public static void putBack(ServerPlayer player, ItemStack[] held) {
        int size = Math.min(held.length, player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (held[slot] != null) {
                player.getInventory().setItem(slot, held[slot]);
            }
        }
        player.getInventory().setChanged();
    }

    /** Copies the dead player's Soulbound stacks to the respawned player. */
    public static void restore(ServerPlayer player, ServerPlayer previousPlayer) {
        int size = Math.min(previousPlayer.getInventory().getContainerSize(), player.getInventory().getContainerSize());
        boolean changed = false;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = previousPlayer.getInventory().getItem(slot);
            if (SoulboundRules.isSoulboundItem(stack)) {
                player.getInventory().setItem(slot, stack.copy());
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
        }
    }
}
