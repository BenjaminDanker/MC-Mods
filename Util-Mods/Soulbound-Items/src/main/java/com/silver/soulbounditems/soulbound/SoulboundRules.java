package com.silver.soulbounditems.soulbound;

import com.silver.soulbounditems.SoulboundItemsMod;
import com.silver.soulbounditems.config.SoulboundItemsConfig;
import com.silver.soulbounditems.mpds.MpdsSoulboundApi;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SoulboundRules {
    private static final String KEY_ID_TYPE = "idType";
    private static final String TYPE_CRAFTED_SOULBOUND = "CraftedSoulbound";

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

    public static int countSoulboundItems(PlayerEntity player) {
        int count = 0;
        int size = player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isSoulboundItem(stack)) {
                count++;
            }
        }
        return count;
    }

    public static int getSoulboundMaxOrUnlimited(ServerPlayerEntity player) {
        try {
            return Math.max(0, MpdsSoulboundApi.getSoulboundMax(player.getName().getString(), player.getUuidAsString()));
        } catch (Exception e) {
            SoulboundItemsMod.LOGGER.error("Failed to read MPDS SoulboundMax for {}", player.getName().getString(), e);
            return Integer.MAX_VALUE;
        }
    }

    public static boolean isAtOrOverLimit(ServerPlayerEntity player) {
        int max = getSoulboundMaxOrUnlimited(player);
        if (max == Integer.MAX_VALUE) {
            return false;
        }
        return countSoulboundItems(player) >= max;
    }

    public static void applyOverflowFlatDamage(ServerPlayerEntity player) {
        float hearts = SoulboundItemsConfig.getOverflowDamageHearts();
        if (hearts <= 0.0f) {
            return;
        }

        float damagePoints = hearts * 2.0f;
        float newHealth = player.getHealth() - damagePoints;
        player.setHealth(Math.max(0.0f, newHealth));
    }
}
