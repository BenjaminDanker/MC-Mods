package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Hold the ring, together: waves of monsters come over the walls; beat every wave. Whoever is still on their feet at
 * the end clears (a fallen player watches the rest). Armour counts, as everywhere.
 */
public final class Waves extends BaseRoom {
    private static final int HALF = 8;
    private final Random random = new Random();
    private final List<Mob> alive = new ArrayList<>();
    private int wave;
    private long nextWaveAt = 80;

    public String id() { return "waves"; }
    public String title() { return "Hold the Ring"; }
    public String explain() {
        return "Monsters come in waves. Beat every wave and still be standing at the end. Fall, and you watch the rest.";
    }

    // 25 levels: more and bigger waves, more skeletons; at most 24 mobs at once so groups aren't swamped.
    int waves() { return Math.min(8, 2 + tier / 4); }
    int perWave() { return Math.min(24, Math.max(1, players.size()) * Math.min(9, 2 + tier / 3)); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(60 + waves() * 45);
    }

    @Override
    protected void build() {
        // The colosseum pit: raked sand-coloured stone in rings, a red terracotta emblem in the middle.
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                double r = Math.hypot(x, z);
                arena.set(x, 0, z, st(r < 1.5 ? "red_terracotta" : r < 2.5 ? "gold_block" : ((int) r) % 3 == 0 ? "cut_sandstone"
                    : Math.floorMod(x * 3 + z, 5) == 0 ? "sandstone" : "smooth_sandstone"));
            }
        }
        for (int i = -HALF; i <= HALF; i++) {
            for (int y = 1; y <= 2; y++) {
                arena.set(i, y, -HALF, Blocks.SANDSTONE_WALL.defaultBlockState());
                arena.set(i, y, HALF, Blocks.SANDSTONE_WALL.defaultBlockState());
                arena.set(-HALF, y, i, Blocks.SANDSTONE_WALL.defaultBlockState());
                arena.set(HALF, y, i, Blocks.SANDSTONE_WALL.defaultBlockState());
            }
        }
        // A roof keeps the sun off the monsters (and the fight in shade); lanterns light it.
        // the awning: a striped purple-and-gold velarium (solid, same height)
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                arena.set(x, 6, z, st(Math.abs(x) == HALF || Math.abs(z) == HALF ? "gold_block" : Math.floorMod(x, 4) < 2 ? "purple_wool" : "white_wool"));
            }
        }
        for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            arena.set(c[0], 5, c[1], Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.ColosseumLook.build(arena, tier, HALF));
        int i = 0;
        for (ServerPlayer player : players.values()) {
            double[] at = spot(i++, 4);
            arena.put(player, at[0], 1, at[1] + 2, 0F);
            give(player, new ItemStack(Items.IRON_SWORD));
            give(player, new ItemStack(Items.BREAD, 8));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setHealth(player.getMaxHealth());
        }
    }

    @Override
    public void tick(long tickInRoom) {
        alive.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
        if (undecided().isEmpty()) {
            return;
        }
        if (alive.isEmpty() && tickInRoom >= nextWaveAt) {
            if (wave >= waves()) {
                for (ServerPlayer player : undecided()) {
                    clear(player);
                    Gamemaster.bar(player, "The ring holds!");
                }
                return;
            }
            wave++;
            spawnWave();
            players.values().forEach(p -> Gamemaster.title(p, "Wave " + wave, "of " + waves()));
            nextWaveAt = tickInRoom + 100;
        }
    }

    private void spawnWave() {
        for (int i = 0; i < perWave(); i++) {
            EntityType<? extends Mob> type = switch (random.nextInt(tier >= 12 ? 4 : tier >= 3 ? 3 : 2)) { // from level 12 skeletons come twice as often
                case 0 -> EntityTypes.HUSK;
                case 1 -> EntityTypes.SPIDER;
                default -> EntityTypes.SKELETON;
            };
            Mob mob = type.create(arena.level(), EntitySpawnReason.MOB_SUMMONED);
            if (mob == null) {
                continue;
            }
            mob.setPersistenceRequired();
            if (mob.getType() == EntityTypes.SKELETON) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning, roof or not
            }
            double angle = random.nextDouble() * Math.PI * 2;
            alive.add(arena.spawn(mob, Math.cos(angle) * (HALF - 1.5) + 0.5, 1, Math.sin(angle) * (HALF - 1.5) + 0.5));
        }
    }

    @Override
    public void died(ServerPlayer player) {
        fail(player); // the run turns them into a spectator
    }
}
