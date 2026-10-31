package com.silver.arcade.door;

import com.silver.arcade.ArcadeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This world's prize table: the band with the highest minTier at or below the cashed-out tier is drawn; each band
 * draws its pool {@code rolls + tier / 5} times. Items unknown on this server are skipped (and logged).
 */
public final class Rewards {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private final ArcadeConfig config;

    public Rewards(ArcadeConfig config) {
        this.config = config;
    }

    static ArcadeConfig.RewardBand bandFor(List<ArcadeConfig.RewardBand> bands, int tier) {
        ArcadeConfig.RewardBand best = null;
        for (ArcadeConfig.RewardBand band : bands) {
            if (band.minTier <= tier && (best == null || band.minTier > best.minTier)) {
                best = band;
            }
        }
        return best;
    }

    static int draws(ArcadeConfig.RewardBand band, int tier) {
        return Math.max(1, band.rolls + tier / 5);
    }

    static ArcadeConfig.RewardItem pick(List<ArcadeConfig.RewardItem> pool, RandomSource random) {
        int total = 0;
        for (ArcadeConfig.RewardItem item : pool) {
            total += Math.max(0, item.weight);
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (ArcadeConfig.RewardItem item : pool) {
            roll -= Math.max(0, item.weight);
            if (roll < 0) {
                return item;
            }
        }
        return null;
    }

    public void give(ServerPlayer player, int tier) {
        ArcadeConfig.RewardBand band = bandFor(config.rewards, tier);
        if (band == null) {
            LOGGER.warn("No arcade prize band for tier {} on this server", tier);
            return;
        }
        List<ItemStack> given = new ArrayList<>();
        RandomSource random = player.getRandom();
        for (int i = 0; i < draws(band, tier); i++) {
            ArcadeConfig.RewardItem choice = pick(band.pool, random);
            if (choice == null) {
                break;
            }
            if (choice.lootTable != null && !choice.lootTable.isBlank()) {
                for (ItemStack stack : roll(player, choice.lootTable)) {
                    given.add(stack.copy());
                    player.getInventory().placeItemBackInInventory(stack);
                }
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(choice.item));
            if (item == null || item == Items.AIR) {
                LOGGER.warn("Arcade prize item {} is not on this server", choice.item);
                continue;
            }
            int count = choice.min + random.nextInt(Math.max(1, choice.max - choice.min + 1));
            ItemStack stack = new ItemStack(item, count);
            given.add(stack.copy());
            player.getInventory().placeItemBackInInventory(stack);
        }
        if (band.skillPoints > 0) {
            givePoints(player, band.skillPoints);
        }
        player.sendSystemMessage(Component.literal("The Gamemaster keeps his word. Your prize for reaching tier " + tier + ":")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        for (ItemStack stack : given) {
            player.sendSystemMessage(Component.literal("  " + stack.getCount() + " x ").append(stack.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        if (band.skillPoints > 0) {
            player.sendSystemMessage(Component.literal("  +" + band.skillPoints + " skill points").withStyle(ChatFormatting.AQUA));
        }
    }

    private static List<ItemStack> roll(ServerPlayer player, String table) {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.parse(table));
        var loot = level.getServer().reloadableRegistries().getLootTable(key);
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, player.position())
            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, player)
            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        List<ItemStack> out = new ArrayList<>(loot.getRandomItems(params));
        if (out.isEmpty()) {
            LOGGER.warn("Arcade prize loot table {} gave nothing on this server", table);
        }
        return out;
    }

    /** Magic: skill points go through Player Sync (called by name; off the server thread). */
    private static void givePoints(ServerPlayer player, int points) {
        String name = player.getName().getString();
        String uuid = player.getStringUUID();
        Thread.ofVirtual().start(() -> {
            try {
                Class.forName("com.silver.playersync.PlayerSync").getMethod("addSkillPoints", String.class, String.class, int.class)
                    .invoke(null, name, uuid, points);
            } catch (ReflectiveOperationException failure) {
                LOGGER.error("ALERT: could not add {} arcade skill points for {}", points, name, failure);
            }
        });
    }
}
