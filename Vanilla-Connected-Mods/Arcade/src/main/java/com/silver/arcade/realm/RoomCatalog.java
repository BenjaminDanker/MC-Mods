package com.silver.arcade.realm;

import com.silver.arcade.realm.rooms.ConnectFour;
import com.silver.arcade.realm.rooms.MathSprint;
import com.silver.arcade.realm.rooms.Maze;
import com.silver.arcade.realm.rooms.Memory;
import com.silver.arcade.realm.rooms.Parkour;
import com.silver.arcade.realm.rooms.TypingRace;

/** Every room the arcade knows, with its party sizes and first tier. */
public final class RoomCatalog {
    private static boolean done;

    private RoomCatalog() {
    }

    public static synchronized void registerAll() {
        if (done) {
            return;
        }
        done = true;
        Rooms.add(new RoomType("typing", Room.Kind.GROUP, 1, 4, 1, TypingRace::new));
        Rooms.add(new RoomType("math", Room.Kind.GROUP, 1, 4, 1, MathSprint::new));
        Rooms.add(new RoomType("maze", Room.Kind.DUO, 1, 2, 1, Maze::new));
        Rooms.add(new RoomType("connect4", Room.Kind.SOLO, 1, 1, 1, ConnectFour::new));
        Rooms.add(new RoomType("memory", Room.Kind.DUO, 1, 2, 1, Memory::new));
        Rooms.add(new RoomType("parkour", Room.Kind.SOLO, 1, 1, 1, Parkour::new));
    }
}
