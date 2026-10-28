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
    public void left(UUID player) {
        if (!decided(player)) {
            failed.add(player);
        }
    }

    /** Seconds -> ticks. */
    protected static int seconds(double s) {
        return (int) Math.round(s * 20);
    }
}
