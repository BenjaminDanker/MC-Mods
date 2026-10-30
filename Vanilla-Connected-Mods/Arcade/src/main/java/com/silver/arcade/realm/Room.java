package com.silver.arcade.realm;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * One challenge room. A room is created for one group of players at one tier, built in its own arena, ticked until
 * {@link #finished()}, then torn down. Tier 1 is easy; there is no upper limit, so every knob must keep scaling
 * (or saturate gracefully) as tiers climb.
 */
public interface Room {
    enum Kind { SOLO, DUO, GROUP }

    /** Stable id (used for "explained once" tracking). */
    String id();

    String title();

    /** The Gamemaster's explanation, said the first time a player meets this room (or on !explain). */
    String explain();

    /** Hard time limit at this tier. */
    int timeLimitTicks(int tier);

    /** Builds the room in the arena and places the players at their starts. */
    void start(Arena arena, List<ServerPlayer> players, int tier);

    /** Every tick while running. */
    void tick(long tickInRoom);

    /** True once every player has either cleared or failed. */
    boolean finished();

    /** Who cleared the room (asked once it is finished or the time ran out). */
    Set<UUID> cleared();

    /** Chat typed by a player in this room; return true to keep it out of public chat. */
    default boolean chat(ServerPlayer player, String message) {
        return false;
    }

    /** A player right-clicked a block in the arena; return true when the room handled it. */
    default boolean use(ServerPlayer player, BlockPos pos) {
        return false;
    }

    /** A player hit (left-clicked) a block in the arena; return true when the room handled it. */
    default boolean hit(ServerPlayer player, BlockPos pos) {
        return false;
    }

    /** A player fell below the arena floor. Default: back to their start. */
    default void fell(ServerPlayer player) {
    }

    /** A player reconnected within the grace period: the room swaps in their new player object. */
    default void rejoined(ServerPlayer player) {
    }

    /**
     * Where spectators of this room may fly, as offsets from the arena origin {minX, minY, minZ, maxX, maxY, maxZ}.
     * Default: a generous box around the usual room footprint.
     */
    default int[] bounds() {
        return new int[] {-48, -24, -48, 48, 40, 48};
    }

    /** A player left mid-room: they fail it. */
    void left(UUID player);

    /** How far players in this room see, in chunks (View Extend): 6 covers a room and its backdrop; big open maps ask for more. */
    default int viewDistance() {
        return 6;
    }

    /** Whether players get hungry here (the arcade has no food; off unless a room's game is about it). */
    default boolean hunger() {
        return false;
    }

    /** The player used the Give up item: they leave the room as failed (others play on). */
    default void giveUp(ServerPlayer player) {
        left(player.getUUID());
    }

    /** Hide everyone's name tag in this room (name tags show through walls; hide-and-seek style rooms need this off). */
    default boolean hideNameTags() {
        return false;
    }

    /**
     * In a pvp room, one player hit another (tagging, passing the potato). Return false to cancel the damage itself
     * (the hit still happened); default lets it through.
     */
    default boolean playerHit(ServerPlayer attacker, ServerPlayer victim) {
        return true;
    }

    /** Whether players in this room may hurt each other (versus rooms). Off by default. */
    default boolean pvp() {
        return false;
    }

    /** The player would have died here (the death is prevented). Default: they fail the room. */
    default void died(ServerPlayer player) {
        left(player.getUUID());
    }

    /** A stand-in when no room fits: everyone clears it at once. */
    final class Pass implements Room {
        public String id() { return "pass"; }
        public String title() { return "A free step"; }
        public String explain() { return "Nothing here. Take it."; }
        public int timeLimitTicks(int tier) { return 1; }
        public void start(Arena arena, List<ServerPlayer> players, int tier) { }
        public void tick(long tickInRoom) { }
        public boolean finished() { return true; }
        public Set<UUID> cleared() { return Set.of(); }
        public void left(UUID player) { }
    }
}
