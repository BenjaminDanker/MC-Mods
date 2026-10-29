package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * King of the hill, a versus room: a stepped pyramid with a gold crown on top. Every second you stand on the crown
 * alone scores a point; knock rivals off with your stick. First to the target wins; at the bell, the top score wins.
 */
public final class KingOfTheHill extends BaseRoom {
    private static final int BASE = 7;
    private final Map<UUID, Integer> score = new HashMap<>();

    public String id() { return "king_hill"; }
    public String title() { return "King of the Hill"; }
    public String explain() {
        return "Stand alone on the gold crown at the top to score a point a second. Knock the others off with your stick. "
            + "First to the target wins; when time runs out, the highest score wins.";
    }

    @Override
    public boolean pvp() {
        return true;
    }

    int target() { return Math.min(40, 15 + tier * 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(target() * 3 + 30);
    }

    @Override
    protected void build() {
        for (int level = 0; level <= BASE; level++) {
            int r = BASE - level;
            arena.fill(-r, level, -r, r, level, r, level == BASE ? Blocks.GOLD_BLOCK.defaultBlockState()
                : (level % 2 == 0 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState()));
        }
        arena.fill(-BASE - 4, 0, -BASE - 4, BASE + 4, 0, BASE + 4, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            double a = Math.PI * 2 * i / Math.max(1, list.size());
            ServerPlayer player = list.get(i);
            arena.put(player, Math.cos(a) * (BASE + 2) + 0.5, 1, Math.sin(a) * (BASE + 2) + 0.5, 0F);
            ItemStack stick = new ItemStack(Items.STICK);
            stick.enchant(enchantment(Enchantments.KNOCKBACK), 1);
            give(player, stick);
            effect(player, MobEffects.RESISTANCE, 20 * 200, 4);
            effect(player, MobEffects.SATURATION, 20 * 200, 0);
            score.put(player.getUUID(), 0);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (tickInRoom % 20 != 0 || players.size() < 1) {
            return;
        }
        ServerPlayer king = null;
        int onTop = 0;
        for (ServerPlayer player : undecided()) {
            if (heightOf(player) < -2) {
                arena.put(player, 0.5, 1, BASE + 2.5, 0F);
            }
            if (heightOf(player) >= BASE + 0.9 && Math.abs(player.getX() - arena.origin().getX() - 0.5) < 1.2
                    && Math.abs(player.getZ() - arena.origin().getZ() - 0.5) < 1.2) {
                king = player;
                onTop++;
            }
        }
        if (onTop == 1) {
            int points = score.merge(king.getUUID(), 1, Integer::sum);
            Gamemaster.bar(king, "Crowned: " + points + " / " + target());
            if (points >= target()) {
                clear(king);
                for (ServerPlayer player : undecided()) {
                    fail(player);
                    Gamemaster.bar(player, king.getName().getString() + " rules the hill.");
                }
            }
        }
    }

    @Override
    public java.util.Set<UUID> cleared() {
        if (!finished()) {
            // The bell: the top score wins (ties share it).
            int best = score.values().stream().max(Integer::compare).orElse(0);
            for (ServerPlayer player : undecided()) {
                if (best > 0 && score.getOrDefault(player.getUUID(), 0) == best) {
                    clear(player);
                } else {
                    fail(player);
                }
            }
        }
        return super.cleared();
    }
}
