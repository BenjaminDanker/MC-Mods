package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * Sumo, a versus room for two: knock the other off a small round platform with a knockback stick. Nobody gets hurt
 * (full resistance), only pushed. Who falls loses; if time runs out, both clear.
 */
public final class Sumo extends BaseRoom {
    private int radius;

    public String id() { return "sumo"; }
    public String title() { return "Sumo"; }
    public String explain() {
        return "Push your opponent off the platform with the stick. Nobody gets hurt, only knocked about. Whoever falls loses.";
    }

    @Override
    public boolean pvp() {
        return true;
    }

    @Override
    public int[] bounds() {
        return new int[] {-48, -20, -48, 48, 40, 64};
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(75);
    }

    @Override
    protected void build() {
        radius = Math.max(3, 6 - tier / 3);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d2 = x * x + z * z;
                if (d2 <= radius * radius) {
                    // A ship's deck: plank strakes with dark seams, a gold compass rose in the middle, and the red
                    // danger ring at the edge (gold rivets) exactly where it was.
                    net.minecraft.world.level.block.state.BlockState deck;
                    if (d2 >= (radius - 1) * (radius - 1)) {
                        deck = radius >= 5 && (x == 0 || z == 0) ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.CONCRETE.red().defaultBlockState();
                    } else if (d2 <= 2) {
                        deck = d2 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                    } else if ((x == 0 || z == 0) && d2 <= (radius - 2) * (radius - 2)) {
                        deck = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    } else {
                        deck = Math.floorMod(x, 3) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                            : (Math.floorMod(x + z / 4, 2) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                    }
                    arena.set(x, 0, z, deck);
                }
            }
        }
        // Underneath, a ship's hull tapering away (one block narrower than the deck at once, so no ledge catches a
        // fall): dark oak strakes with a gold band.
        for (int y = -1; y >= -4; y--) {
            int r = radius + y; // radius-1 at y -1, then narrower
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (r > 0 && x * x + z * z <= r * r) {
                        arena.set(x, y, z, y == -1 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    }
                }
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.PirateLook.build(arena, tier, radius));
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            ServerPlayer player = list.get(i);
            arena.put(player, 0.5, 1, i == 0 ? -radius + 1.5 : radius - 0.5, i == 0 ? 0F : 180F);
            ItemStack stick = new ItemStack(Items.STICK);
            stick.enchant(enchantment(Enchantments.KNOCKBACK), Math.min(2, 1 + tier / 4));
            give(player, stick);
            effect(player, MobEffects.RESISTANCE, 20 * 120, 4);
            effect(player, MobEffects.SATURATION, 20 * 120, 0);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        List<ServerPlayer> standing = undecided();
        for (ServerPlayer player : standing) {
            if (heightOf(player) < -3) {
                out(player, "Off you go.");
            }
        }
        standing = undecided();
        if (players.size() > 1 && standing.size() == 1) {
            clear(standing.get(0));
            Gamemaster.bar(standing.get(0), "Champion!");
        }
    }

    @Override
    public java.util.Set<java.util.UUID> cleared() {
        undecided().forEach(this::clear);
        return super.cleared();
    }
}
