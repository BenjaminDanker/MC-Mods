package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.List;
import java.util.Random;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Hot potato, a versus room: one player wears the lit TNT. Hit someone to pass it on. When the fuse runs out, whoever
 * wears it is out (a harmless bang). New fuse, new holder, until one player is left.
 */
public final class HotPotato extends BaseRoom {
    private static final int HALF = 8;
    private final Random random = new Random();
    private ServerPlayer holder;
    private long fuseEnds;
    private long passedAt;

    public String id() { return "hot_potato"; }
    public String title() { return "Hot Potato"; }
    public String explain() {
        return "Whoever wears the TNT must hit someone to pass it on. When the fuse runs out, the one wearing it is out. Nobody gets hurt.";
    }

    @Override
    public boolean pvp() {
        return true;
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(180);
    }

    @Override
    public int[] bounds() {
        return new int[] {-40, -20, -40, 40, 40, 40};
    }

    int fuse() { return seconds(Math.max(8, 20 - tier)) + random.nextInt(seconds(6)); }

    @Override
    protected void build() {
        arena.fill(-HALF, 0, -HALF, HALF, 0, HALF, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        List<ServerPlayer> list = List.copyOf(players.values());
        com.silver.arcade.realm.deco.FrontierLook.build(arena, tier, HALF);
        for (int i = 0; i < list.size(); i++) {
            double a = Math.PI * 2 * i / Math.max(1, list.size());
            arena.put(list.get(i), Math.cos(a) * (HALF - 2) + 0.5, 1, Math.sin(a) * (HALF - 2) + 0.5, 0F);
            effect(list.get(i), MobEffects.RESISTANCE, 20 * 200, 4);
            effect(list.get(i), MobEffects.SATURATION, 20 * 200, 0);
        }
    }

    private void give(ServerPlayer next, long now) {
        if (holder != null) {
            holder.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        holder = next;
        passedAt = now;
        holder.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
        Gamemaster.title(holder, "You have the potato!", "Hit someone, quick");
    }

    private long now;

    @Override
    public void tick(long tickInRoom) {
        now = tickInRoom;
        List<ServerPlayer> playing = undecided();
        for (ServerPlayer player : playing) {
            if (heightOf(player) < -2) {
                arena.put(player, 0.5, 1, 0.5, 0F);
            }
        }
        if (players.size() > 1 && playing.size() == 1) {
            clear(playing.get(0));
            playing.get(0).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            Gamemaster.bar(playing.get(0), "Last one unburnt!");
            return;
        }
        if (playing.size() < 2 || tickInRoom < 60) {
            return;
        }
        if (holder == null || decided(holder.getUUID()) || holder.isRemoved()) {
            give(playing.get(random.nextInt(playing.size())), tickInRoom);
            fuseEnds = tickInRoom + fuse();
            return;
        }
        if (tickInRoom % 10 == 0) {
            arena.level().sendParticles(ParticleTypes.SMOKE, holder.getX(), holder.getY() + 2.2, holder.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
            arena.level().playSound(null, holder.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
        if (tickInRoom >= fuseEnds) {
            arena.level().sendParticles(ParticleTypes.EXPLOSION, holder.getX(), holder.getY() + 1, holder.getZ(), 1, 0, 0, 0, 0);
            arena.level().playSound(null, holder.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1F, 1F);
            holder.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            out(holder, "BOOM. You're out.");
            holder = null;
        }
    }

    @Override
    public boolean playerHit(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == holder && !decided(victim.getUUID()) && now - passedAt > 10) {
            give(victim, now);
        }
        return true; // knockback, no damage (resistance)
    }

    @Override
    public void rejoined(ServerPlayer player) {
        super.rejoined(player);
        if (holder != null && holder.getUUID().equals(player.getUUID())) {
            holder = player;
        }
    }
}
