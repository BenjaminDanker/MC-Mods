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
                if (x * x + z * z <= radius * radius) {
                    arena.set(x, 0, z, (x * x + z * z) >= (radius - 1) * (radius - 1) ? Blocks.CONCRETE.red().defaultBlockState()
                        : Blocks.CONCRETE.white().defaultBlockState());
                }
            }
        }
        com.silver.arcade.realm.deco.PirateLook.build(arena, tier, radius);
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
