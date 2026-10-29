package com.silver.arcade.realm;

import com.silver.arcade.realm.rooms.Archery;
import com.silver.arcade.realm.rooms.Hanoi;
import com.silver.arcade.realm.rooms.WhackAMole;
import com.silver.arcade.realm.rooms.BlockParty;
import com.silver.arcade.realm.rooms.HideAndSeek;
import com.silver.arcade.realm.rooms.HotPotato;
import com.silver.arcade.realm.rooms.KingOfTheHill;
import com.silver.arcade.realm.rooms.Mastermind;
import com.silver.arcade.realm.rooms.MemoryPairs;
import com.silver.arcade.realm.rooms.MusicalChairs;
import com.silver.arcade.realm.rooms.TileSlide;
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
        // Batch 3 (phase 3).
        Rooms.add(new RoomType("block_party", Room.Kind.GROUP, 1, 16, 1, BlockParty::new));
        Rooms.add(new RoomType("mastermind", Room.Kind.DUO, 1, 2, 1, Mastermind::new));
        Rooms.add(new RoomType("slide", Room.Kind.DUO, 1, 2, 1, TileSlide::new));
        Rooms.add(new RoomType("pairs", Room.Kind.DUO, 1, 2, 1, MemoryPairs::new));
        Rooms.add(new RoomType("chairs", Room.Kind.GROUP, 2, 16, 1, MusicalChairs::new));
        Rooms.add(new RoomType("hot_potato", Room.Kind.GROUP, 3, 16, 1, HotPotato::new));
        Rooms.add(new RoomType("king_hill", Room.Kind.GROUP, 2, 16, 1, KingOfTheHill::new));
        Rooms.add(new RoomType("hide_seek", Room.Kind.GROUP, 3, 16, 1, HideAndSeek::new));
        // Batch 4 (solo first).
        Rooms.add(new RoomType("whack", Room.Kind.DUO, 1, 2, 1, WhackAMole::new));
        Rooms.add(new RoomType("hanoi", Room.Kind.DUO, 1, 2, 1, Hanoi::new));
    }
}
