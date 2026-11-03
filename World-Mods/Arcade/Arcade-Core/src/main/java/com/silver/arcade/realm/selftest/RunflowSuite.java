package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.realm.RoomType;
import com.silver.arcade.realm.Rooms;
import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import com.silver.arcade.selftest.TestDb;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * A whole climb: three rooms in a row. The emerald side goes on at the next tier in a different room; the gold side cashes
 * out and leaves the prize for the bot's origin server at the right tier; standing on neither for 20 s cashes out too.
 */
final class RunflowSuite {
    private RunflowSuite() {
    }

    static void run(T t) throws Exception {
        Cleanup.unforce();
        String name = Bots.PREFIX + "f1";
        Bots.spawn(t, name, 0.5, 101, 0.5, false);
        UUID id = t.on(() -> t.server.getPlayerList().getPlayerByName(name).getUUID());
        Bots.kill(t, name);
        try (TestDb db = new TestDb()) {
            db.clearPrizeLog(List.of(id));
            db.insert("REPLACE INTO arcade_entries (uuid, name, origin) VALUES (?, ?, ?)", id.toString(), name, "magic");
        }
        Bots.spawn(t, name, 0.5, 101, 0.5, false);
        t.ticks(30);
        t.run(() -> Pool.park(t, t.server.getPlayerList().getPlayerByName(name), 0));
        t.ticks(5);
        t.run(() -> Reflect.call(SelfTest.ENV.roleObject, "startRun", new ArrayList<>(List.of(t.server.getPlayerList().getPlayerByName(name))), false));
        String previous = null;
        Object runObject = null;
        for (int round = 1; round <= 3; round++) {
            String label = "climb round " + round;
            boolean started = t.until(() -> Live.find(id) != null, 200);
            t.check(label + ": a room starts for the bot", started, "");
            if (!started) {
                return;
            }
            Live live = t.on(() -> Live.find(id));
            runObject = live.run;
            String type = live.room.id();
            int tier = t.on(() -> {
                Object v = Reflect.get(live.room, "tier");
                return (Integer) v;
            });
            // a room plays at its own level for the climb tier (its curve), which can lag the tier
            int level = t.on(() -> {
                Object v = Reflect.get(live.party, "level");
                return (Integer) v;
            });
            t.check(label + ": the room (" + type + ") is at its level (" + level + ") for climb tier " + round, tier == level && level >= 1 && level <= round, "tier " + tier + ", level " + level);
            if (previous != null) {
                t.check(label + ": a different room type from the last (" + previous + " -> " + type + ")", !type.equals(previous), "");
            }
            previous = type;
            t.until(() -> !live.arena.decorating(), 20 * 60);
            Drivers.Driver driver = Drivers.ALL.get(type);
            Ctx ctx = new Ctx(t, live, new String[] {name}, round, label + " " + type);
            t.guard(label + ": driver ran", () -> driver.play(ctx, -1));
            boolean over = t.until(live::over, 20 * 60);
            boolean won = t.on(() -> live.cleared().contains(id));
            t.check(label + ": the bot clears " + type, over && won, "");
            Object run = live.run;
            boolean choice = t.until(() -> Reflect.get(run, "choiceArena") != null, 100);
            t.check(label + ": the choice platform opens", choice, "");
            int nextTier = t.on(() -> Reflect.<Integer>call(run, "tier"));
            int prizeTier = t.on(() -> Reflect.<Integer>get(run, "prizeTier"));
            t.check(label + ": the prize tier is " + round + " and the next tier " + (round + 1), prizeTier == round && nextTier == round + 1,
                "prize " + prizeTier + ", next " + nextTier);
            Arena platform = t.on(() -> Reflect.get(run, "choiceArena"));
            if (round < 3) {
                t.run(() -> platform.put(t.server.getPlayerList().getPlayerByName(name), 4.5, 1, 0.5, 180F));
                long at = t.tick();
                boolean next = t.until(() -> Live.find(id) != null && Live.find(id).room != live.room, 200);
                t.check(label + ": the emerald side goes on to a new room (after " + (t.tick() - at) + " ticks)", next, "");
            } else {
                t.run(() -> platform.put(t.server.getPlayerList().getPlayerByName(name), -4.5, 1, 0.5, 180F));
            }
        }
        boolean home = t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(name)) == null && Live.runOf(id) == null, 20 * 15);
        t.check("gold: the bot ends in the hall (adventure, empty inventory, full health) and the run is over", home,
            String.valueOf(t.on(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(name)))));
        T.sleep(1500);
        try (TestDb db = new TestDb()) {
            t.check("gold: the prize row is for the bot's origin server (magic) at tier 3", db.prizes(id).equals(List.of("magic:3")), String.valueOf(db.prizes(id)));
        }
        // standing on neither side for 20 s cashes out
        Cleanup.force("typing_race 1");
        t.run(() -> Reflect.call(SelfTest.ENV.roleObject, "startRun", new ArrayList<>(List.of(t.server.getPlayerList().getPlayerByName(name))), false));
        t.until(() -> Live.find(id) != null, 200);
        Live live = t.on(() -> Live.find(id));
        t.until(() -> !live.arena.decorating(), 20 * 60);
        Ctx ctx = new Ctx(t, live, new String[] {name}, 1, "undecided");
        t.guard("undecided climb: driver ran", () -> Drivers.ALL.get("typing_race").play(ctx, -1));
        boolean choice = t.until(() -> Reflect.get(live.run, "choiceArena") != null, 200);
        long at = t.tick();
        boolean cashed = t.until(() -> Live.runOf(id) == null, 20 * 30);
        long waited = t.tick() - at;
        t.check("doing nothing on the choice platform cashes out after about 20 s (" + waited + " ticks)", choice && cashed && waited > 300 && waited < 460,
            "waited " + waited + " ticks");
        T.sleep(1500);
        try (TestDb db = new TestDb()) {
            List<String> prizes = db.prizes(id);
            t.check("...and records the tier-1 prize as well", prizes.equals(List.of("magic:3", "magic:1")), prizes.toString());
        }
        Bots.kill(t, name);
        partyRules(t);
    }

    private static final java.util.Set<String> PUZZLES = java.util.Set.of("lights_out", "copy_the_picture", "crack_the_code",
        "sliding_picture", "pairs", "whack_a_mole", "tower", "spot_the_difference");

    private static void startRun(T t, String[] bots, boolean together) {
        t.run(() -> Reflect.call(SelfTest.ENV.roleObject, "startRun", new ArrayList<>(Pool.players(t, bots)), together));
    }

    private static int countdownOf(T t, Live live) {
        return t.on(() -> Reflect.<Integer>get(live.party, "countdown"));
    }

    private static void endAll(T t, UUID... ids) {
        for (UUID id : ids) {
            Live live = t.on(() -> Live.find(id));
            if (live != null) {
                t.run(() -> live.expire(t.tick()));
            }
        }
        t.until(() -> {
            for (UUID id : ids) {
                if (Live.runOf(id) != null) {
                    return false;
                }
            }
            return true;
        }, 20 * 40);
        t.until(() -> {
            for (UUID id : ids) {
                if (Pool.hallProblem(t.server.getPlayerList().getPlayer(id)) != null) {
                    return false;
                }
            }
            return true;
        }, 20 * 20);
    }

    /** Hold before Go!, puzzles only for one, the waiting line for a finished party, the give-up message. */
    private static void partyRules(T t) throws Exception {
        // (a) a bot that walks off during the countdown is put back
        Cleanup.force("lights_out 1");
        String[] one = Pool.ensure(t, 1);
        UUID ida = t.on(() -> t.server.getPlayerList().getPlayerByName(one[0]).getUUID());
        startRun(t, one, false);
        t.until(() -> Live.find(ida) != null, 200);
        Live solo = t.on(() -> Live.find(ida));
        boolean counting = t.until(() -> {
            Object c = Reflect.get(solo.party, "countdown");
            return c instanceof Integer n && n > 10;
        }, 20 * 60);
        t.check("hold: the countdown runs after the room is built", counting, "");
        if (counting) {
            double[] start = t.on(() -> {
                var pos = t.server.getPlayerList().getPlayerByName(one[0]).position();
                return new double[] {pos.x, pos.y, pos.z};
            });
            t.run(() -> {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(one[0]);
                p.teleportTo(p.level(), start[0] + 2.0, start[1], start[2] + 2.0, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
            });
            t.ticks(4);
            double away = t.on(() -> {
                var pos = t.server.getPlayerList().getPlayerByName(one[0]).position();
                return Math.hypot(pos.x - start[0], pos.z - start[2]);
            });
            t.check("hold: a bot that walks during the countdown is put back at once", away < 0.3, "distance from start " + away);
            t.until(() -> countdownOf(t, solo) == 0, 20 * 30);
            double atGo = t.on(() -> {
                var pos = t.server.getPlayerList().getPlayerByName(one[0]).position();
                return Math.hypot(pos.x - start[0], pos.z - start[2]);
            });
            t.check("hold: the bot is still on its start spot at Go!", atGo < 0.3, "distance from start " + atGo);
        }
        endAll(t, ida);
        // (d) rounds with two players: puzzle rooms never get both
        Cleanup.unforce();
        java.util.Random random = new java.util.Random(7);
        int puzzlePairs = 0;
        int soloGroupRooms = 0;
        java.util.Map<String, RoomType> byId = new java.util.HashMap<>();
        Rooms.all().forEach(r -> byId.put(r.id(), r));
        for (int sample = 0; sample < 600; sample++) {
            int tier = 1 + sample % 12;
            RoomType shared = Rooms.pick(2, tier, null, random, false, r -> r.kind() != com.silver.arcade.realm.Room.Kind.SOLO);
            RoomType any = Rooms.pick(2, tier, null, random, false);
            RoomType alone = Rooms.pick(1, tier, null, random, false, r -> r.kind() != com.silver.arcade.realm.Room.Kind.GROUP);
            puzzlePairs += (shared != null && PUZZLES.contains(shared.id()) ? 1 : 0) + (any != null && PUZZLES.contains(any.id()) ? 1 : 0);
            soloGroupRooms += alone != null && alone.kind() == com.silver.arcade.realm.Room.Kind.GROUP ? 1 : 0;
        }
        t.check("rounds: 1200 room picks for two players never choose a puzzle room", puzzlePairs == 0, puzzlePairs + " puzzle rooms picked");
        t.check("rounds: a solo round never picks a group-only room", soloGroupRooms == 0, soloGroupRooms + " group rooms picked");
        t.check("rounds: every puzzle room is solo-only (1..1 players)", PUZZLES.stream().allMatch(id -> byId.get(id) != null
            && byId.get(id).kind() == com.silver.arcade.realm.Room.Kind.SOLO && byId.get(id).minPlayers() == 1 && byId.get(id).maxPlayers() == 1), "");
        // (b) a finished solo party sees who it waits for; observed rounds also never put two in a puzzle room
        String[] two = Pool.ensure(t, 2);
        UUID u1 = t.on(() -> t.server.getPlayerList().getPlayerByName(two[0]).getUUID());
        UUID u2 = t.on(() -> t.server.getPlayerList().getPlayerByName(two[1]).getUUID());
        Live l1 = null;
        Live l2 = null;
        int sharedPuzzles = 0;
        for (int attempt = 0; attempt < 12 && l1 == null; attempt++) {
            startRun(t, two, false);
            t.until(() -> Live.find(u1) != null && Live.find(u2) != null, 200);
            Live x = t.on(() -> Live.find(u1));
            Live y = t.on(() -> Live.find(u2));
            if (x != null && y != null && x.party == y.party) {
                sharedPuzzles += PUZZLES.contains(x.room.id()) ? 1 : 0;
            } else if (x != null && y != null) {
                l1 = x;
                l2 = y;
            }
            if (l1 == null) {
                endAll(t, u1, u2);
            }
        }
        t.check("rounds: in the observed rounds a shared room is never a puzzle", sharedPuzzles == 0, sharedPuzzles + " shared puzzle rooms");
        if (l1 == null) {
            t.fail("a solo round splits two bots into their own rooms", "12 rounds in a row kept them together");
        } else {
            Live first = l1;
            Live second = l2;
            t.until(() -> !first.arena.decorating() && !second.arena.decorating(), 20 * 60);
            t.until(() -> countdownOf(t, first) == 0 && countdownOf(t, second) == 0, 20 * 30);
            long mark = PacketLog.mark(two[0]);
            t.run(() -> first.expire(t.tick()));
            boolean waiting = t.until(() -> PacketLog.hasText(PacketLog.since(two[0], mark), "ClientboundSystemChatPacket", "Waiting for " + two[1]), 120);
            t.check("waiting: a finished party sees \"Waiting for <name> to finish their room...\" while the other party plays", waiting,
                PacketLog.since(two[0], mark).stream().filter(r -> r.info().contains("[bar]")).map(PacketLog.Rec::info).distinct().toList().toString());
            endAll(t, u1, u2);
        }
        // (c) /giveup in a shared room tells the partner; the giver is carried only when the partner clears
        Cleanup.force("simon_says 1");
        giveUpCase(t, two, u1, u2, true);
        giveUpCase(t, two, u1, u2, false);
        Cleanup.unforce();
    }

    private static void giveUpCase(T t, String[] two, UUID u1, UUID u2, boolean partnerClears) {
        String tag = partnerClears ? "giveup (partner clears)" : "giveup (partner fails)";
        Live pair = null;
        for (int attempt = 0; attempt < 12 && pair == null; attempt++) {
            startRun(t, two, true);
            t.until(() -> Live.find(u1) != null && Live.find(u2) != null, 200);
            Live x = t.on(() -> Live.find(u1));
            Live y = t.on(() -> Live.find(u2));
            if (x != null && y != null && x.party == y.party) {
                pair = x;
            } else {
                endAll(t, u1, u2);
            }
        }
        if (pair == null) {
            t.fail(tag + ": two bots can share a room", "the room never held both");
            return;
        }
        Live shared = pair;
        t.until(() -> !shared.arena.decorating(), 20 * 60);
        t.until(() -> countdownOf(t, shared) == 0, 20 * 30);
        long m1 = PacketLog.mark(two[0]);
        long m2 = PacketLog.mark(two[1]);
        t.run(() -> Reflect.call(shared.run, "giveUp", t.server.getPlayerList().getPlayerByName(two[0])));
        t.ticks(5);
        t.check(tag + ": the other bot is told \"<name> gave up this room.\"", PacketLog.hasText(PacketLog.since(two[1], m2), "ClientboundSystemChatPacket", two[0] + " gave up this room"), "");
        t.check(tag + ": the giver gets a chat line saying they stay if the others clear", PacketLog.hasText(PacketLog.since(two[0], m1), "ClientboundSystemChatPacket", "You gave up this room. If everyone else clears it, you stay in the climb."), "");
        if (partnerClears) {
            t.run(() -> ((java.util.Set<UUID>) Reflect.get(shared.room, "cleared")).add(u2));
        } else {
            t.run(() -> ((java.util.Set<UUID>) Reflect.get(shared.room, "failed")).add(u2));
        }
        boolean over = t.until(shared::over, 200);
        t.check(tag + ": the room ends", over, "");
        java.util.Set<UUID> cleared = t.on(() -> Reflect.get(shared.party, "cleared"));
        boolean carried = PacketLog.hasText(PacketLog.since(two[0], m1), "ClientboundSystemChatPacket", "Your team cleared the room");
        if (partnerClears) {
            t.check(tag + ": the giver is carried to the next round", cleared.contains(u1) && cleared.contains(u2) && carried, "cleared " + cleared.size() + ", told " + carried);
        } else {
            t.check(tag + ": the giver is out", !cleared.contains(u1) && !carried, "cleared " + cleared.size() + ", told " + carried);
        }
        endAll(t, u1, u2);
    }
}
