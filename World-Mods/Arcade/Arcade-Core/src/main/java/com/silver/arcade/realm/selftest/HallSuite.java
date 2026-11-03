package com.silver.arcade.realm.selftest;

import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.LogScan;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hall: ten doors, each leading to a waiting room and then a climb; bots through one door within the 10 s wait share a
 * climb; the exit pad sends a bot home; the countdown reads 10, 9, ... 1.
 */
final class HallSuite {
    private HallSuite() {
    }

    private static double[] centre(ArcadeConfig.HallDoor d) {
        boolean alongX = "x".equals(d.axis);
        return new double[] {d.x + (alongX ? d.width / 2.0 : 0.5), d.y, d.z + (alongX ? 0.5 : d.width / 2.0)};
    }

    static void run(T t) throws Exception {
        List<ArcadeConfig.HallDoor> doors = SelfTest.ENV.config.hall.doors;
        t.check("the hall has 10 doors configured", doors.size() == 10, doors.size() + " doors");
        Cleanup.force("typing_race 1"); // a cheap room behind every door
        int signs = t.on(() -> t.server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(-100, 90, -100, 100, 140, 160),
            e -> e.entityTags().contains("arcade_hall_sign")).size());
        t.ok("hall signs standing before the first bot arrives: " + signs + " (they are put up when someone is in the hall)");
        String[] bots = Pool.ensure(t, 2);
        String a = bots[0];
        UUID ida = t.on(() -> t.server.getPlayerList().getPlayerByName(a).getUUID());
        t.ticks(120);
        int signNow = t.on(() -> t.server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(-100, 90, -100, 100, 140, 160),
            e -> e.entityTags().contains("arcade_hall_sign")).size());
        String where = t.on(() -> t.server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(-100, 90, -100, 100, 140, 160),
            e -> e.entityTags().contains("arcade_hall_sign")).stream().map(e -> String.format("(%.0f,%.0f)", e.getX(), e.getZ())).sorted().toList().toString());
        t.check("the hall has 10 PLAY signs and one HOME sign (no duplicates)", signNow == 11, signNow + " signs, at " + where);
        for (int d = 0; d < doors.size(); d++) {
            ArcadeConfig.HallDoor door = doors.get(d);
            String label = "door " + (d + 1);
            double[] c = centre(door);
            long mark = PacketLog.mark(a);
            int entered = t.tick();
            t.run(() -> {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(a);
                p.teleportTo(t.server.overworld(), c[0], c[1], c[2], Set.of(), 0F, 0F, false);
            });
            t.ticks(8); // the hall looks at who stands in a door every fifth tick
            double dist = t.on(() -> t.server.getPlayerList().getPlayerByName(a).position().distanceTo(new net.minecraft.world.phys.Vec3(door.waitX, door.waitY, door.waitZ)));
            t.check(label + ": walking in lands the bot in its waiting room", dist < 1.5, "distance " + dist);
            boolean started = t.until(() -> Live.find(ida) != null, 300);
            int waited = t.tick() - entered;
            t.check(label + ": a climb starts after about 10 s (" + waited + " ticks)", started && waited >= 195 && waited <= 225, "waited " + waited + " ticks");
            List<PacketLog.Rec> got = PacketLog.since(a, mark);
            Set<Integer> counts = new TreeSet<>();
            for (PacketLog.Rec r : got) {
                if (r.type().equals("ClientboundSystemChatPacket") && r.info().startsWith("[bar] The game begins in ")) {
                    String digits = r.info().replaceAll("[^0-9 ]", "").trim().split(" ")[0];
                    counts.add(Integer.parseInt(digits));
                }
            }
            t.check(label + ": the countdown shows 10, 9, ... 1", counts.equals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), "saw " + counts);
            Live live = t.on(() -> Live.find(ida));
            if (live != null) {
                t.until(() -> !live.arena.decorating(), 20 * 60);
                t.run(() -> live.expire(t.tick()));
                t.until(live::over, 200);
            }
            t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(a)) == null && Live.runOf(ida) == null, 20 * 20);
        }
        // two bots through the same door within the 10 s wait share one climb
        String b = bots[1];
        UUID idb = t.on(() -> t.server.getPlayerList().getPlayerByName(b).getUUID());
        double[] c0 = centre(doors.get(0));
        double[] c5 = centre(doors.get(5));
        t.run(() -> t.server.getPlayerList().getPlayerByName(a).teleportTo(t.server.overworld(), c0[0], c0[1], c0[2], Set.of(), 0F, 0F, false));
        t.ticks(40);
        t.run(() -> t.server.getPlayerList().getPlayerByName(b).teleportTo(t.server.overworld(), c0[0], c0[1], c0[2], Set.of(), 0F, 0F, false));
        boolean both = t.until(() -> Live.runOf(ida) != null && Live.runOf(idb) != null, 300);
        Object runA = t.on(() -> Live.runOf(ida));
        Object runB = t.on(() -> Live.runOf(idb));
        t.check("two bots through the same door within 10 s share one climb", both && runA != null && runA == runB, "");
        finish(t, ida, idb);
        // different doors: separate climbs
        t.run(() -> t.server.getPlayerList().getPlayerByName(a).teleportTo(t.server.overworld(), c0[0], c0[1], c0[2], Set.of(), 0F, 0F, false));
        t.run(() -> t.server.getPlayerList().getPlayerByName(b).teleportTo(t.server.overworld(), c5[0], c5[1], c5[2], Set.of(), 0F, 0F, false));
        both = t.until(() -> Live.runOf(ida) != null && Live.runOf(idb) != null, 300);
        runA = t.on(() -> Live.runOf(ida));
        runB = t.on(() -> Live.runOf(idb));
        t.check("bots through different doors get separate climbs", both && runA != null && runA != runB, "");
        finish(t, ida, idb);
        // the exit pad sends a bot home
        long mark = PacketLog.mark(a);
        t.run(() -> t.server.getPlayerList().getPlayerByName(a).teleportTo(t.server.overworld(), 0.5, 101, -7.5, Set.of(), 0F, 0F, false));
        boolean queued = t.until(() -> !LogScan.grep("arcade exit: asked the proxy to move " + a).isEmpty(), 200);
        List<PacketLog.Rec> got = PacketLog.since(a, mark);
        t.check("the exit pad queues a transfer home (Portals request)", queued, queued ? LogScan.grep("arcade exit: asked the proxy to move " + a).get(0) : "no log line");
        t.check("...the request reached the player as a packet", PacketLog.hasText(got, "ClientboundCustomPayloadPacket", "portal"), PacketLog.snapshot(a).stream()
            .filter(r -> r.type().equals("ClientboundCustomPayloadPacket")).map(PacketLog.Rec::info).distinct().toList().toString());
        t.check("...with the Gamemaster's farewell", PacketLog.hasText(got, "ClientboundSystemChatPacket", "Leaving so soon"), "");
        t.run(() -> t.server.getPlayerList().getPlayerByName(a).teleportTo(t.server.overworld(), 0.5, 101, 0.5, Set.of(), 0F, 0F, false));
        t.ticks(5);
    }

    private static void finish(T t, UUID... ids) {
        for (UUID id : ids) {
            Live live = t.on(() -> Live.find(id));
            if (live != null) {
                t.until(() -> !live.arena.decorating(), 20 * 60);
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
        }, 20 * 30);
        t.until(() -> {
            for (UUID id : ids) {
                ServerPlayer p = t.server.getPlayerList().getPlayer(id);
                if (Pool.hallProblem(p) != null) {
                    return false;
                }
            }
            return true;
        }, 20 * 20);
    }
}
