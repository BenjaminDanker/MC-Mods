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
import net.minecraft.world.level.block.state.BlockState;

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

    @Override
    public int[] bounds() {
        return new int[] {-40, -20, -40, 40, 50, 40};
    }

    int target() { return Math.min(42, 12 + tier * 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(target() * 3 + 30);
    }

    @Override
    protected void build() {
        // The temple pyramid: the same stepped hill (full blocks, one step a level, a single crown block on top),
        // carved: mossy and plain stone-brick tiers, a gold corner on every step, a stair strip up the middle of each
        // face, gilded glyph bands on the odd tiers and a black frame round the gold crown so it stands out.
        for (int level = 1; level <= BASE; level++) {
            int r = BASE - level;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    arena.set(x, level, z, templeBlock(level, r, x, z));
                }
            }
        }
        // the courtyard: mossy paving with a gold-edged processional cross and a gold rim
        for (int x = -BASE - 4; x <= BASE + 4; x++) {
            for (int z = -BASE - 4; z <= BASE + 4; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                BlockState floor;
                if (m == BASE + 4) {
                    floor = Blocks.GOLD_BLOCK.defaultBlockState();
                } else if (m == BASE + 3) {
                    floor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                } else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {
                    floor = Math.floorMod(m, 3) == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
                } else {
                    floor = Math.floorMod(x * 7 + z * 3, 5) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : (Math.floorMod(x + z, 2) == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                }
                arena.set(x, 0, z, floor);
            }
        }
        // braziers at the courtyard corners
        for (int cx : new int[] {-BASE - 3, BASE + 3}) {
            for (int cz : new int[] {-BASE - 3, BASE + 3}) {
                arena.set(cx, 1, cz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                arena.set(cx, 2, cz, Blocks.GOLD_BLOCK.defaultBlockState());
                arena.set(cx, 3, cz, Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
        List<ServerPlayer> list = List.copyOf(players.values());
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.TempleLook.build(arena, tier, BASE));
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

    private static BlockState templeBlock(int level, int r, int x, int z) {
        if (level == BASE) {
            return Blocks.GOLD_BLOCK.defaultBlockState(); // the crown
        }
        if (level == BASE - 1) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(); // dark frame round the crown
        }
        boolean edge = Math.max(Math.abs(x), Math.abs(z)) == r;
        if (!edge) {
            return Blocks.STONE_BRICKS.defaultBlockState(); // hidden under the next tier
        }
        if (Math.abs(x) == r && Math.abs(z) == r) {
            return Blocks.GOLD_BLOCK.defaultBlockState();
        }
        int along = Math.abs(x) == r ? z : x;
        if (Math.abs(along) <= 1) {
            return Blocks.CHISELED_STONE_BRICKS.defaultBlockState(); // the stair strip up each face
        }
        if (level % 2 == 1) {
            return Math.floorMod(along, 3) == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Math.floorMod(along * 5 + level, 7) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
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
