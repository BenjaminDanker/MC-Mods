package com.silver.arcade.realm.selftest;

import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** The Grand Game: six free bots make the Gamemaster call it; those who come through in the window play one climb together. */
final class GrandSuite {
    private GrandSuite() {
    }

    static void run(T t) throws Exception {
        List<ArcadeConfig.HallDoor> doors = SelfTest.ENV.config.hall.doors;
        Cleanup.force("typing 1");
        String[] bots = Pool.ensure(t, 6);
        List<UUID> ids = t.on(() -> Pool.players(t, bots).stream().map(ServerPlayer::getUUID).toList());
        Object hall = Reflect.get(SelfTest.ENV.roleObject, "doors");
        int min = SelfTest.ENV.config.event.minPlayers;
        t.check("a Grand Game needs " + min + " players (configured)", min == 6, "minPlayers " + min);
        // --- six bots: the call opens, all six through, one together-run
        Reflect.set(hall, "lastGrandAt", -1L);
        long[] marks = new long[6];
        for (int i = 0; i < 6; i++) {
            marks[i] = PacketLog.mark(bots[i]);
        }
        boolean called = t.until(() -> (long) Reflect.get(hall, "grandCallEnds") > t.tick(), 400);
        t.check("six free bots in the hall make the Gamemaster call a Grand Game", called, "");
        int titled = 0;
        for (int i = 0; i < 6; i++) {
            if (PacketLog.hasText(PacketLog.since(bots[i], marks[i]), "ClientboundSetTitleTextPacket", "A Grand Game!")) {
                titled++;
            }
        }
        t.check("...every bot gets the 'A Grand Game!' title", titled == 6, titled + " of 6");
        t.ticks(25);
        t.check("...and the hall bar says every door leads to it", PacketLog.hasText(PacketLog.since(bots[0], marks[0]), "ClientboundSystemChatPacket", "Grand Game! Every door"), "");
        t.run(() -> {
            for (int i = 0; i < 6; i++) {
                ArcadeConfig.HallDoor d = doors.get(i);
                boolean alongX = "x".equals(d.axis);
                t.server.getPlayerList().getPlayerByName(bots[i]).teleportTo(t.server.overworld(),
                    d.x + (alongX ? d.width / 2.0 : 0.5), d.y, d.z + (alongX ? 0.5 : d.width / 2.0), Set.of(), 0F, 0F, false);
            }
        });
        int window = SelfTest.ENV.config.event.windowSeconds * 20;
        boolean started = t.until(() -> Live.runOf(ids.get(0)) != null, window + 200);
        Object run = t.on(() -> Live.runOf(ids.get(0)));
        boolean allIn = true;
        for (UUID id : ids) {
            allIn &= t.on(() -> Live.runOf(id)) == run && run != null;
        }
        t.check("all six bots end up in one climb when the window closes", started && allIn, "");
        t.check("...which is a together-run (Grand Game)", run != null && t.on(() -> Reflect.<Boolean>get(run, "together")), "");
        Live live = t.on(() -> Live.find(ids.get(0)));
        if (live != null) {
            List<UUID> members = t.on(() -> Reflect.<List<UUID>>get(live.party, "members"));
            t.check("...all six play the same room (" + live.room.id() + ")", members.size() == 6, members.size() + " in the party");
            t.until(() -> !live.arena.decorating(), 20 * 60);
        }
        finishAll(t, ids);
        // --- cooldown: six free again, no second call
        long[] marks2 = new long[6];
        for (int i = 0; i < 6; i++) {
            marks2[i] = PacketLog.mark(bots[i]);
        }
        t.ticks(350);
        boolean again = false;
        for (int i = 0; i < 6; i++) {
            again |= PacketLog.hasText(PacketLog.since(bots[i], marks2[i]), "ClientboundSetTitleTextPacket", "A Grand Game!");
        }
        t.check("no second call inside the cooldown (15 minutes)", !again, "");
        // --- only three come through: ordinary climbs
        Reflect.set(hall, "lastGrandAt", -1L);
        for (int i = 0; i < 6; i++) {
            marks2[i] = PacketLog.mark(bots[i]);
        }
        boolean called2 = t.until(() -> (long) Reflect.get(hall, "grandCallEnds") > t.tick(), 400);
        t.check("the call opens again once the cooldown is over", called2, "");
        t.run(() -> {
            for (int i = 0; i < 3; i++) {
                ArcadeConfig.HallDoor d = doors.get(i);
                boolean alongX = "x".equals(d.axis);
                t.server.getPlayerList().getPlayerByName(bots[i]).teleportTo(t.server.overworld(),
                    d.x + (alongX ? d.width / 2.0 : 0.5), d.y, d.z + (alongX ? 0.5 : d.width / 2.0), Set.of(), 0F, 0F, false);
            }
        });
        boolean started2 = t.until(() -> Live.runOf(ids.get(0)) != null, window + 200);
        Object run2 = t.on(() -> Live.runOf(ids.get(0)));
        boolean together = run2 != null && t.on(() -> Reflect.<Boolean>get(run2, "together"));
        t.check("with only 3 bots in the window the climb is ordinary, not a Grand Game", started2 && !together, "");
        t.check("...and they are told so", PacketLog.hasText(PacketLog.since(bots[0], marks2[0]), "ClientboundSystemChatPacket", "Not enough for a Grand Game"), "");
        t.check("the three bots that stayed out are not in any climb", t.on(() -> Live.runOf(ids.get(3)) == null && Live.runOf(ids.get(4)) == null && Live.runOf(ids.get(5)) == null), "");
        finishAll(t, ids);
    }

    static void finishAll(T t, List<UUID> ids) {
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
}
