package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.realm.Room;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Shared bookkeeping: who is in the room, who cleared, who failed; finished once everyone is decided. */
abstract class BaseRoom implements Room {
    protected Arena arena;
    protected int tier;
    protected final Map<UUID, ServerPlayer> players = new LinkedHashMap<>();
    protected final Set<UUID> cleared = new HashSet<>();
    protected final Set<UUID> failed = new HashSet<>();

    @Override
    public void start(Arena arena, List<ServerPlayer> players, int tier) {
        this.arena = arena;
        this.tier = tier;
        players.forEach(p -> this.players.put(p.getUUID(), p));
        players.forEach(p -> p.getInventory().clearContent());
        build();
    }

    /** Builds the room and places the players. */
    protected abstract void build();

    protected boolean decided(UUID id) {
        return cleared.contains(id) || failed.contains(id);
    }

    protected void clear(ServerPlayer player) {
        if (!decided(player.getUUID())) {
            cleared.add(player.getUUID());
            com.silver.arcade.realm.Gamemaster.bar(player, "Cleared!");
        }
    }

    protected void fail(ServerPlayer player) {
        if (!decided(player.getUUID())) {
            failed.add(player.getUUID());
            com.silver.arcade.realm.Gamemaster.bar(player, "Failed.");
        }
    }

    @Override
    public boolean finished() {
        return players.keySet().stream().allMatch(this::decided);
    }

    @Override
    public Set<UUID> cleared() {
        return Set.copyOf(cleared);
    }

    @Override
    public void rejoined(ServerPlayer player) {
        players.replace(player.getUUID(), player);
    }

    @Override
    public void left(UUID player) {
        if (!decided(player)) {
            failed.add(player);
        }
    }

    /** Knocked out of the room: failed, and watching the rest from above (spectators stay inside the room's box). */
    protected void out(ServerPlayer player, String why) {
        fail(player);
        com.silver.arcade.realm.Gamemaster.bar(player, why);
        player.resetFallDistance();
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        arena.put(player, 0.5, 10, 0.5, 0F);
    }

    /** Start spot i of n on a grid two blocks apart, centred, rows going back (-z); x stays within +-half. */
    protected static double[] spot(int i, int half) {
        int perRow = Math.max(1, half + 1);
        return new double[] {-half + 2 * (i % perRow) + 0.5, -2.0 * (i / perRow) + 0.5};
    }

    /** Players still playing (not cleared or failed). */
    protected java.util.List<ServerPlayer> undecided() {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer player : players.values()) {
            if (!decided(player.getUUID()) && !player.isRemoved()) {
                out.add(player);
            }
        }
        return out;
    }

    /** Height above the arena floor (y 0 is the floor block's level). */
    protected double heightOf(ServerPlayer player) {
        return player.getY() - arena.origin().getY();
    }

    protected void give(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }

    protected void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amplifier) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, ticks, amplifier, false, false));
    }

    protected net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return arena.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key);
    }

    /** Seconds -> ticks. */
    protected static int seconds(double s) {
        return (int) Math.round(s * 20);
    }

    /** A block state from its command form ("red_terracotta", "lantern[hanging=true]"), cached. */
    protected static net.minecraft.world.level.block.state.BlockState st(String state) {
        return com.silver.arcade.realm.deco.Deco.st(state);
    }
}
