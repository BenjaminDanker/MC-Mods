package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
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
            t.check(label + ": the room (" + type + ") is at tier " + round, tier == round, "tier " + tier);
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
        Cleanup.force("typing 1");
        t.run(() -> Reflect.call(SelfTest.ENV.roleObject, "startRun", new ArrayList<>(List.of(t.server.getPlayerList().getPlayerByName(name))), false));
        t.until(() -> Live.find(id) != null, 200);
        Live live = t.on(() -> Live.find(id));
        t.until(() -> !live.arena.decorating(), 20 * 60);
        Ctx ctx = new Ctx(t, live, new String[] {name}, 1, "undecided");
        t.guard("undecided climb: driver ran", () -> Drivers.ALL.get("typing").play(ctx, -1));
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
    }
}
