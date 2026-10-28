package com.silver.arcade.realm;

import java.util.function.Supplier;

/** A kind of room the picker can choose: how many players it takes, from which tier, and how to make one. */
public record RoomType(String id, Room.Kind kind, int minPlayers, int maxPlayers, int minTier, Supplier<Room> factory) {
    public boolean fits(int players, int tier) {
        return players >= minPlayers && players <= maxPlayers && tier >= minTier;
    }
}
