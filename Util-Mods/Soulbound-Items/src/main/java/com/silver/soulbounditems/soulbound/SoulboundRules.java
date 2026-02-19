package com.silver.soulbounditems.soulbound;

import com.silver.soulbounditems.SoulboundItemsMod;
import com.silver.soulbounditems.mpds.MpdsSoulboundApi;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SoulboundRules {
    private static final String KEY_ID_TYPE = "idType";
    private static final String TYPE_CRAFTED_SOULBOUND = "CraftedSoulbound";
    private static final float OVERFLOW_DAMAGE_HEARTS = 4.0f;

    private SoulboundRules() {
    }

    public static boolean isSoulboundItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        String idType = customData.copyNbt().getString(KEY_ID_TYPE).orElse("");
        return TYPE_CRAFTED_SOULBOUND.equals(idType);
    }

    public static int[] snapshotSoulboundCounts(ServerPlayerEntity player) {
        int size = player.getInventory().size();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            counts[i] = isSoulboundItem(stack) ? stack.getCount() : 0;
        }
        return counts;
    }

    public static EnforcementResult enforceOverflowByDroppingNewest(ServerPlayerEntity player, int[] previousCounts) {
        int max = getSoulboundMaxOrUnlimited(player);
        if (max == Integer.MAX_VALUE) {
            int[] unchanged = snapshotSoulboundCounts(player);
            return new EnforcementResult(unchanged, false);
        }

        int[] currentCounts = snapshotSoulboundCounts(player);
        int currentTotal = sum(currentCounts);
        int overflow = currentTotal - max;
        if (overflow <= 0) {
            return new EnforcementResult(currentCounts, false);
        }

        boolean droppedAny = false;

        while (overflow > 0) {
            int preferredSlot = findIncreasedSoulboundSlot(currentCounts, previousCounts);
            int slot = preferredSlot >= 0 ? preferredSlot : findAnySoulboundSlot(currentCounts);
            if (slot < 0) {
                break;
            }

            if (currentCounts[slot] <= 0) {
                continue;
            }

            ItemStack stack = player.getInventory().getStack(slot);
            if (stack == null || stack.isEmpty()) {
                currentCounts[slot] = 0;
                continue;
            }

            ItemStack dropped = stack.split(1);
            if (!dropped.isEmpty()) {
                player.dropItem(dropped, true, false);
                overflow--;
                droppedAny = true;
                currentCounts[slot] = Math.max(0, currentCounts[slot] - 1);
            } else {
                currentCounts[slot] = 0;
            }
        }

        if (droppedAny) {
            applyOverflowFlatDamage(player);
            player.getInventory().markDirty();
        }

        return new EnforcementResult(currentCounts, overflow > 0);
    }

    public static int getSoulboundMaxOrUnlimited(ServerPlayerEntity player) {
        try {
            return Math.max(0, MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUuidAsString()));
        } catch (Exception e) {
            SoulboundItemsMod.LOGGER.error("Failed to read MPDS SoulboundMax for {}", player.getName().getString(), e);
            return Integer.MAX_VALUE;
        }
    }

    private static void applyOverflowFlatDamage(ServerPlayerEntity player) {
        float damagePoints = OVERFLOW_DAMAGE_HEARTS * 2.0f;
        player.damage(player.getCommandSource().getWorld(), player.getDamageSources().generic(), damagePoints);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static int findIncreasedSoulboundSlot(int[] currentCounts, int[] previousCounts) {
        int size = Math.min(currentCounts.length, previousCounts.length);
        for (int i = 0; i < size; i++) {
            if (currentCounts[i] > previousCounts[i]) {
                return i;
            }
        }
        return -1;
    }

    private static int findAnySoulboundSlot(int[] currentCounts) {
        for (int i = 0; i < currentCounts.length; i++) {
            if (currentCounts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    public record EnforcementResult(int[] updatedCounts, boolean stillOverflowing) {
    }
}
