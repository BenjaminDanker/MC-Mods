package com.silver.arcade.realm;

import java.util.function.Supplier;

/** A kind of room the picker can choose: how many players it takes, from which tier, and how to make one. */
public record RoomType(String id, Room.Kind kind, int minPlayers, int maxPlayers, int minTier, Supplier<Room> factory, Difficulty.Curve curve) {
    public RoomType(String id, Room.Kind kind, int minPlayers, int maxPlayers, int minTier, Supplier<Room> factory) {
        this(id, kind, minPlayers, maxPlayers, minTier, factory, null);
    }

    /** The same room with a difficulty curve. */
    public RoomType curve(Difficulty.Curve curve) {
        return new RoomType(id, kind, minPlayers, maxPlayers, minTier, factory, curve);
    }

    public boolean fits(int players, int tier) {
        return players >= minPlayers && players <= maxPlayers && tier >= minTier;
    }
}
