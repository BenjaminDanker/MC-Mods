package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Archery: targets light up one at a time on the far wall; hit each lit target with an arrow before the time runs out. */
public final class Archery extends BaseRoom {
    private final Random random = new Random();
    private final List<BlockPos> targets = new ArrayList<>();
    private int current;
    private int distance;

    public String id() { return "archery"; }
    public String title() { return "Archery"; }
    public String explain() {
        return "A target lights up on the far wall. Hit it with an arrow and the next one lights. Hit them all before time runs out.";
    }

    @Override
    public int[] bounds() {
        return new int[] {-48, -24, -30, 48, 50, 30 + 70};
    }

    int count() { return Math.min(12, 3 + tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(20 + count() * 7);
    }

    @Override
    protected void build() {
        distance = Math.min(30, 12 + tier * 2);
        arena.fill(-4, 0, -2, 4, 0, 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
        arena.fill(-10, 0, distance, 10, 12, distance, Blocks.HAY_BLOCK.defaultBlockState());
        for (int i = 0; i < count(); i++) {
            int x = random.nextInt(17) - 8;
            int y = 2 + random.nextInt(9);
            targets.add(new BlockPos(x, y, distance - 1));
        }
        light();
        com.silver.arcade.realm.deco.TourneyLook.build(arena, tier, distance);
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5, 1, 0.5, 0F);
            give(player, new ItemStack(Items.BOW));
            give(player, new ItemStack(Items.ARROW, 64));
        }
    }

    private void light() {
        if (current < targets.size()) {
            BlockPos t = targets.get(current);
            arena.set(t.getX(), t.getY(), t.getZ(), Blocks.TARGET.defaultBlockState());
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (current >= targets.size()) {
            return;
        }
        BlockPos t = arena.at(targets.get(current).getX(), targets.get(current).getY(), targets.get(current).getZ());
        AABB box = new AABB(t).inflate(0.35);
        for (AbstractArrow arrow : arena.level().getEntitiesOfClass(AbstractArrow.class, box)) {
            arrow.discard();
            arena.level().setBlock(t, Blocks.HAY_BLOCK.defaultBlockState(), 2);
            arena.level().playSound(null, t, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1F, 1F);
            current++;
            players.values().forEach(p -> Gamemaster.bar(p, current + " of " + targets.size()));
            if (current >= targets.size()) {
                players.values().forEach(this::clear);
            } else {
                light();
            }
            return;
        }
    }
}
