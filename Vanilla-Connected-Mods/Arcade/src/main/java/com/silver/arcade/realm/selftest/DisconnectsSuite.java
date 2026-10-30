package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import com.silver.arcade.selftest.TestDb;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Leaving and coming back: the room waits, the partner is rehomed, late rejoin, resumed climbs, death as a spectator, ledgers. */
final class DisconnectsSuite {
    private DisconnectsSuite() {
    }

    static void run(T t) throws Exception {
        pausedRoom(t);
        partnerRehomed(t);
        deathSpectates(t);
        suspendedRun(t);
        ledgerRecovery(t);
    }

    private static Object role() {
        return SelfTest.ENV.roleObject;
    }

    private static void start(T t, String[] bots, boolean together) {
        t.run(() -> Reflect.call(role(), "startRun", new ArrayList<>(Pool.players(t, bots)), together));
    }

    // 1. kill a bot mid-room: the clock freezes; back within 90 s: the same room goes on
    private static void pausedRoom(T t) throws Exception {
        Cleanup.force("lights_out 1");
        String[] bots = Pool.ensure(t, 1);
        String a = bots[0];
        UUID id = t.on(() -> t.server.getPlayerList().getPlayerByName(a).getUUID());
        start(t, bots, false);
        t.until(() -> Live.find(id) != null, 200);
        Live live = t.on(() -> Live.find(id));
        t.until(() -> !live.arena.decorating(), 20 * 60);
        double[] where = t.on(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(a);
            return new double[] {p.getX(), p.getY(), p.getZ()};
        });
        Bots.kill(t, a);
        t.ticks(5);
        boolean away = t.on(() -> Reflect.<Map<UUID, Long>>get(live.run, "away").containsKey(id));
        t.check("a bot killed mid-room is marked away (the room waits for it)", away, "");
        long e1 = t.on(() -> live.elapsed(t.tick()));
        t.ticks(100);
        long e2 = t.on(() -> live.elapsed(t.tick()));
        t.check("the room's clock stands still while the bot is away", Math.abs(e2 - e1) <= 3, e1 + " -> " + e2 + " ticks of play");
        Bots.respawn(t, a);
        boolean back = t.until(() -> !Reflect.<Map<UUID, Long>>get(live.run, "away").containsKey(id), 100);
        t.check("coming back within 90 s ends the pause", back, "");
        Live now = t.on(() -> Live.find(id));
        t.check("...into the same room", now != null && now.room == live.room, "");
        boolean inside = t.on(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(a);
            return live.arena.contains(p.blockPosition()) && p.getX() > 256;
        });
        if (!inside) {
            // a Carpet bot starts from its saved data; if the position was not restored, put it where it was
            t.report.notes.add("disconnects: respawned bot was not inside its arena after reconnecting (position restore is Carpet's, not the product's); moved it back");
            t.run(() -> t.server.getPlayerList().getPlayerByName(a).teleportTo(t.server.overworld(), where[0], where[1], where[2], Set.of(), 0F, 0F, false));
        }
        Ctx ctx = new Ctx(t, now, bots, 1, "after reconnect");
        t.guard("reconnect: the room can still be solved", () -> Drivers.ALL.get("lights_out").play(ctx, -1));
        boolean won = t.until(now::over, 20 * 30) && t.on(() -> now.cleared().contains(id));
        t.check("...and the reconnected bot clears it", won, "");
        t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(a)) == null, 400);
        // finish the climb (gold)
        Object ca = t.until(() -> Reflect.get(now.run, "choiceArena") != null, 100) ? t.on(() -> Reflect.get(now.run, "choiceArena")) : null;
        if (ca instanceof Arena choose) {
            t.run(() -> choose.put(t.server.getPlayerList().getPlayerByName(a), -4.5, 1, 0.5, 180F));
        }
        t.until(() -> Live.runOf(id) == null, 200);
        T.sleep(1200);
        try (TestDb db = new TestDb()) {
            db.deleteFor(List.of(id)); // the cash-out above left a prize row; it is the test's own
        }
    }

    // 2. two bots in one room; one stays away past 90 s: the partner gets a room of their own; the leaver rejoins late
    private static void partnerRehomed(T t) throws Exception {
        Cleanup.force("memory 1");
        String[] bots = Pool.ensure(t, 2);
        UUID ida = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[0]).getUUID());
        UUID idb = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[1]).getUUID());
        Live live = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            start(t, bots, false);
            t.until(() -> Live.find(ida) != null && Live.find(idb) != null, 200);
            live = t.on(() -> Live.find(ida));
            Live other = t.on(() -> Live.find(idb));
            if (live != null && other != null && live.party == other.party) {
                break;
            }
            // the climb split them: end it and try again
            for (UUID id : List.of(ida, idb)) {
                Live l = t.on(() -> Live.find(id));
                if (l != null) {
                    t.run(() -> l.expire(t.tick()));
                }
            }
            t.until(() -> Live.runOf(ida) == null && Live.runOf(idb) == null, 400);
            t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[0])) == null && Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[1])) == null, 400);
            live = null;
        }
        if (live == null) {
            t.fail("two bots can share a duo room", "the climb split them ten times in a row");
            return;
        }
        Live shared = live;
        Object oldRoom = shared.room;
        t.until(() -> !shared.arena.decorating(), 20 * 60);
        long mark = PacketLog.mark(bots[1]);
        Bots.kill(t, bots[0]);
        t.until(() -> Reflect.<Map<UUID, Long>>get(shared.run, "away").containsKey(ida), 40);
        t.check("a partner is told the room waits for the one who lost connection", PacketLog.hasText(PacketLog.since(bots[1], mark), "ClientboundSystemChatPacket", "lost their connection"), "");
        // 90 s of play-clock later (GRACE_TICKS): the partner is moved on
        int grace = Reflect.getStatic(Class.forName("com.silver.arcade.realm.Run"), "GRACE_TICKS");
        boolean dropped = t.until(() -> Live.find(idb) != null && Live.find(idb).room != oldRoom, grace + 400);
        t.check("past 90 s the leaver is dropped and the partner gets a room of their own", dropped, "");
        t.check("...the partner is told", PacketLog.hasText(PacketLog.since(bots[1], mark), "ClientboundSystemChatPacket", "hasn't come back"), "");
        T.sleep(1200);
        try (TestDb db = new TestDb()) {
            t.check("the leaver's climb is kept in the database (arcade_suspended)", db.count("arcade_suspended", ida) == 1, db.count("arcade_suspended", ida) + " rows");
        }
        // late rejoin: the leaver is back inside the 15 minutes while the climb still goes on
        Bots.respawn(t, bots[0]);
        boolean rejoined = t.until(() -> Live.find(ida) != null, 400);
        t.check("a late rejoin puts the leaver back into the climb", rejoined, "");
        Live lateRoom = t.on(() -> Live.find(ida));
        t.check("...in a fresh room, not the one they left", lateRoom != null && lateRoom.room != oldRoom, "");
        T.sleep(600);
        try (TestDb db = new TestDb()) {
            t.check("...and the kept climb row is used up", db.count("arcade_suspended", ida) == 0, db.count("arcade_suspended", ida) + " rows");
        }
        GrandSuite.finishAll(t, List.of(ida, idb));
    }

    // 3. a deadly hit in a multi-player room: spectator, health restored, held inside the room's box
    private static void deathSpectates(T t) throws Exception {
        Cleanup.force("waves 1");
        String[] bots = Pool.ensure(t, 2);
        UUID ida = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[0]).getUUID());
        UUID idb = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[1]).getUUID());
        start(t, bots, true);
        t.until(() -> Live.find(ida) != null, 200);
        Live live = t.on(() -> Live.find(ida));
        t.until(() -> !live.arena.decorating(), 20 * 60);
        t.run(() -> {
            ServerPlayer p = t.server.getPlayerList().getPlayerByName(bots[0]);
            p.hurtServer(t.server.overworld(), t.server.overworld().damageSources().generic(), 1000F);
        });
        t.ticks(5);
        ServerPlayer p0 = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[0]));
        t.check("a deadly hit in a room does not kill: the bot watches as a spectator", t.on(() -> p0.isSpectator() && p0.getHealth() > 0 && !p0.isDeadOrDying()),
            t.on(() -> "mode " + p0.gameMode() + " health " + p0.getHealth()));
        Ctx ctx = new Ctx(t, live, bots, 1, "death");
        Drivers.spectatorClamp(ctx, 0);
        t.check("the other bot is still playing", t.on(() -> !live.over()), "");
        t.run(() -> live.expire(t.tick()));
        t.until(live::over, 200);
        GrandSuite.finishAll(t, List.of(ida, idb));
    }

    // 4. a climb left suspended (server stop) is resumed on the choice platform within 15 minutes, and lost after
    private static void suspendedRun(T t) throws Exception {
        Cleanup.unforce();
        String name = Pool.name(0);
        String[] bots = Pool.ensure(t, 1);
        UUID id = t.on(() -> t.server.getPlayerList().getPlayerByName(name).getUUID());
        try (TestDb db = new TestDb()) {
            db.insert("REPLACE INTO arcade_suspended (uuid, tier, prize_tier, origin) VALUES (?, ?, ?, ?)", id.toString(), 4, 3, "vanilla");
        }
        Bots.kill(t, name);
        long mark = PacketLog.mark(name);
        Bots.respawn(t, name);
        boolean resumed = t.until(() -> Live.runOf(id) != null, 400);
        t.check("a suspended climb is resumed when the player joins within 15 minutes", resumed, "");
        Object run = t.on(() -> Live.runOf(id));
        Object choice = t.until(() -> Reflect.get(run, "choiceArena") != null, 100) ? t.on(() -> Reflect.get(run, "choiceArena")) : null;
        t.check("...on the choice platform with tier 3 earned and tier 4 next", choice != null && t.on(() -> Reflect.<Integer>get(run, "prizeTier")) == 3
            && t.on(() -> Reflect.<Integer>call(run, "tier")) == 4, "");
        t.check("...with the Gamemaster's welcome back", PacketLog.hasText(PacketLog.since(name, mark), "ClientboundSystemChatPacket", "You came back"), "");
        if (choice instanceof Arena platform) {
            t.run(() -> platform.put(t.server.getPlayerList().getPlayerByName(name), -4.5, 1, 0.5, 180F));
        }
        t.until(() -> Live.runOf(id) == null, 300);
        T.sleep(1200);
        List<String> prizes;
        try (TestDb db = new TestDb()) {
            prizes = db.prizes(id);
            db.deleteFor(List.of(id));
        }
        t.check("...gold then records the tier-3 prize", prizes.contains("vanilla:3"), prizes.toString());
        // too long: the stored climb is older than 15 minutes
        Bots.kill(t, name);
        try (TestDb db = new TestDb()) {
            db.insert("REPLACE INTO arcade_suspended (uuid, tier, prize_tier, origin, since) VALUES (?, ?, ?, ?, NOW() - INTERVAL 20 MINUTE)", id.toString(), 4, 3, "vanilla");
        }
        mark = PacketLog.mark(name);
        Bots.respawn(t, name);
        t.ticks(60);
        t.check("a climb older than 15 minutes is not resumed", t.on(() -> Live.runOf(id) == null), "");
        t.check("...the player is told it is gone", PacketLog.hasText(PacketLog.since(name, mark), "ClientboundSystemChatPacket", "gone too long"), "");
        try (TestDb db = new TestDb()) {
            t.check("...and the stored row is removed", db.count("arcade_suspended", id) == 0, "");
        }
    }

    // 5. what a stop leaves behind (ledger files with the placed blocks) is cleared on the next start
    private static void ledgerRecovery(T t) throws Exception {
        Cleanup.force("lights_out 1");
        String[] bots = Pool.ensure(t, 1);
        UUID id = t.on(() -> t.server.getPlayerList().getPlayerByName(bots[0]).getUUID());
        start(t, bots, false);
        t.until(() -> Live.find(id) != null, 200);
        Live live = t.on(() -> Live.find(id));
        t.until(() -> !live.arena.decorating(), 20 * 60);
        int slot = Reflect.get(live.arena, "slot");
        List<BlockPos> placed = t.on(() -> {
            live.arena.flushLedger();
            return new ArrayList<>(Reflect.<java.util.Deque<BlockPos>>get(live.arena, "placed"));
        });
        Path dir = t.server.getWorldPath(LevelResource.ROOT).resolve("arcade-arenas");
        Path file = dir.resolve("slot-" + slot + ".bin");
        t.check("while a room stands its ledger is on disk", Files.exists(file) && Files.size(file) == placed.size() * 8L, file.getFileName() + " " + (Files.exists(file) ? Files.size(file) : -1) + " bytes for " + placed.size() + " blocks");
        // the server stops: the run is abandoned with the ledger kept and nothing cleared
        t.run(() -> {
            Arena.stopping = true;
            try {
                Reflect.call(live.run, "abandon");
            } finally {
                Arena.stopping = false;
            }
        });
        t.ticks(5);
        int solid = t.on(() -> (int) placed.stream().filter(pos -> !t.server.overworld().getBlockState(pos).isAir()).count());
        t.check("after a stop the room's blocks are still standing (nothing cleared at shutdown)", solid > placed.size() / 2, solid + " of " + placed.size());
        t.check("...and its ledger is still on disk", Files.exists(file), "");
        // the next start clears them
        t.run(() -> Arena.clearLeftovers(t.server.overworld(), dir));
        int left = t.on(() -> (int) placed.stream().filter(pos -> !t.server.overworld().getBlockState(pos).isAir()).count());
        t.check("the next start clears every block the ledger lists", left == 0, left + " blocks left");
        t.check("...and removes the ledger", !Files.exists(file), "");
        t.run(() -> Reflect.<BitSet>getStatic(Arena.class, "USED").clear(slot)); // a new process starts with no slot taken
        T.sleep(1200);
        try (TestDb db = new TestDb()) {
            db.deleteFor(List.of(id)); // abandon() kept this bot's climb in the database
        }
        GrandSuite.finishAll(t, List.of(id));
    }
}
