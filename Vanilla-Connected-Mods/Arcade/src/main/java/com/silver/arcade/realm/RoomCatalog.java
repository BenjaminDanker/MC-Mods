package com.silver.arcade.realm;

import com.silver.arcade.realm.rooms.Archery;
import com.silver.arcade.realm.rooms.ConnectFour;
import com.silver.arcade.realm.rooms.Dropper;
import com.silver.arcade.realm.rooms.FallingFloor;
import com.silver.arcade.realm.rooms.LightsOut;
import com.silver.arcade.realm.rooms.MathSprint;
import com.silver.arcade.realm.rooms.Maze;
import com.silver.arcade.realm.rooms.Memory;
import com.silver.arcade.realm.rooms.Parkour;
import com.silver.arcade.realm.rooms.PatternCopy;
import com.silver.arcade.realm.rooms.RedLight;
import com.silver.arcade.realm.rooms.SplitSimon;
import com.silver.arcade.realm.rooms.Spleef;
import com.silver.arcade.realm.rooms.Sumo;
import com.silver.arcade.realm.rooms.TypingRace;
import com.silver.arcade.realm.rooms.Waves;

/** Every room the arcade knows, with its party sizes and first tier (parties above 4 come only from the Grand Game). */
public final class RoomCatalog {
    private static boolean done;

    private RoomCatalog() {
    }

    public static synchronized void registerAll() {
        if (done) {
            return;
        }
        done = true;
        Rooms.add(new RoomType("typing", Room.Kind.GROUP, 1, 16, 1, TypingRace::new));
        Rooms.add(new RoomType("math", Room.Kind.GROUP, 1, 16, 1, MathSprint::new));
        Rooms.add(new RoomType("maze", Room.Kind.DUO, 1, 2, 1, Maze::new));
        Rooms.add(new RoomType("connect4", Room.Kind.SOLO, 1, 1, 1, ConnectFour::new));
        Rooms.add(new RoomType("memory", Room.Kind.DUO, 1, 2, 1, Memory::new));
        Rooms.add(new RoomType("parkour", Room.Kind.SOLO, 1, 1, 1, Parkour::new));
        Rooms.add(new RoomType("lights_out", Room.Kind.DUO, 1, 2, 1, LightsOut::new));
        Rooms.add(new RoomType("pattern", Room.Kind.DUO, 1, 2, 1, PatternCopy::new));
        Rooms.add(new RoomType("archery", Room.Kind.DUO, 1, 2, 1, Archery::new));
        Rooms.add(new RoomType("dropper", Room.Kind.GROUP, 1, 8, 1, Dropper::new));
        Rooms.add(new RoomType("falling_floor", Room.Kind.GROUP, 1, 16, 1, FallingFloor::new));
        Rooms.add(new RoomType("red_light", Room.Kind.GROUP, 1, 16, 1, RedLight::new));
        Rooms.add(new RoomType("waves", Room.Kind.GROUP, 1, 8, 2, Waves::new));
        // Only with company: versus and co-op rooms.
        Rooms.add(new RoomType("spleef", Room.Kind.GROUP, 2, 16, 1, Spleef::new));
        Rooms.add(new RoomType("sumo", Room.Kind.DUO, 2, 2, 1, Sumo::new));
        Rooms.add(new RoomType("split_simon", Room.Kind.DUO, 2, 2, 1, SplitSimon::new));
    }
}
