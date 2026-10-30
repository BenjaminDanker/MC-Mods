package com.silver.arcade.realm;

import com.silver.arcade.realm.rooms.Archery;
import com.silver.arcade.realm.rooms.Hanoi;
import com.silver.arcade.realm.rooms.HoleInWall;
import com.silver.arcade.realm.rooms.LavaRising;
import com.silver.arcade.realm.rooms.SpotDifference;
import com.silver.arcade.realm.rooms.TreasureHunt;
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
        Rooms.add(new RoomType("typing", Room.Kind.GROUP, 1, 16, 1, TypingRace::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("math", Room.Kind.GROUP, 1, 16, 1, MathSprint::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("maze", Room.Kind.DUO, 1, 2, 1, Maze::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        // Connect Four: the bot's level 1-15 (ConnectFourBoard), one per climb tier, then it holds.
        Rooms.add(new RoomType("connect4", Room.Kind.SOLO, 1, 1, 1, ConnectFour::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("memory", Room.Kind.DUO, 1, 2, 1, Memory::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("parkour", Room.Kind.SOLO, 1, 1, 1, Parkour::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("lights_out", Room.Kind.DUO, 1, 2, 1, LightsOut::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("pattern", Room.Kind.DUO, 1, 2, 1, PatternCopy::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("archery", Room.Kind.DUO, 1, 2, 1, Archery::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("dropper", Room.Kind.GROUP, 1, 8, 1, Dropper::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("falling_floor", Room.Kind.GROUP, 1, 16, 1, FallingFloor::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("red_light", Room.Kind.GROUP, 1, 16, 1, RedLight::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("waves", Room.Kind.GROUP, 1, 8, 2, Waves::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
        // Only with company: versus and co-op rooms.
        Rooms.add(new RoomType("spleef", Room.Kind.GROUP, 2, 16, 1, Spleef::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("sumo", Room.Kind.DUO, 2, 2, 1, Sumo::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("split_simon", Room.Kind.DUO, 2, 2, 1, SplitSimon::new).curve(Difficulty.linear(1, 18, Difficulty.After.HOLD)));
        // Batch 3 (phase 3).
        Rooms.add(new RoomType("block_party", Room.Kind.GROUP, 1, 16, 1, BlockParty::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("mastermind", Room.Kind.DUO, 1, 2, 3, Mastermind::new).curve(Difficulty.linear(3, 27, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("slide", Room.Kind.DUO, 1, 2, 1, TileSlide::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("pairs", Room.Kind.DUO, 1, 2, 1, MemoryPairs::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("chairs", Room.Kind.GROUP, 2, 16, 1, MusicalChairs::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("hot_potato", Room.Kind.GROUP, 3, 16, 1, HotPotato::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("king_hill", Room.Kind.GROUP, 2, 16, 1, KingOfTheHill::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("hide_seek", Room.Kind.GROUP, 3, 16, 1, HideAndSeek::new).curve(Difficulty.linear(1, 15, Difficulty.After.HOLD)));
        // Batch 4 (solo first).
        Rooms.add(new RoomType("whack", Room.Kind.DUO, 1, 2, 1, WhackAMole::new).curve(Difficulty.linear(1, 25, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("hanoi", Room.Kind.DUO, 1, 2, 2, Hanoi::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("spot", Room.Kind.DUO, 1, 2, 2, SpotDifference::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("treasure", Room.Kind.DUO, 1, 2, 2, TreasureHunt::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("lava_rising", Room.Kind.GROUP, 1, 8, 2, LavaRising::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
        Rooms.add(new RoomType("hole_wall", Room.Kind.GROUP, 1, 5, 2, HoleInWall::new).curve(Difficulty.linear(2, 26, Difficulty.After.HOLD)));
    }
}
