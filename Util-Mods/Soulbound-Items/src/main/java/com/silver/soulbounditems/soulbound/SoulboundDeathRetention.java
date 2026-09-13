package com.silver.soulbounditems.soulbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Carries crafted Soulbound stacks across the old-player/new-player death transition. */
public final class SoulboundDeathRetention {
    private static final Map<UUID, ItemStack[]> PENDING_RETENTION = new ConcurrentHashMap<>();

    private SoulboundDeathRetention() {
    }

    public static void captureAndRemove(ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        ItemStack[] retained = new ItemStack[size];
        boolean found = false;

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!SoulboundRules.isSoulboundItem(stack)) {
                continue;
            }
            retained[slot] = stack.copy();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            found = true;
        }

        if (found) {
            PENDING_RETENTION.put(player.getUUID(), retained);
            player.getInventory().setChanged();
        }
    }

    public static void restore(ServerPlayer player, ServerPlayer previousPlayer) {
        ItemStack[] retained = PENDING_RETENTION.remove(previousPlayer.getUUID());
        if (retained == null) {
            return;
        }

        int size = Math.min(retained.length, player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (retained[slot] != null) {
                player.getInventory().setItem(slot, retained[slot]);
            }
        }
        player.getInventory().setChanged();
    }
}
