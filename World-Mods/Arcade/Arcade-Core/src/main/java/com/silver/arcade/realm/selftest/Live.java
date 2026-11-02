package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.realm.Room;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A view of one running party (room + arena) inside the product's Run, read by reflection. */
final class Live {
    final Object run;
    final Object party;
    final Room room;
    final Arena arena;

    private Live(Object run, Object party) {
        this.run = run;
        this.party = party;
        this.room = Reflect.get(party, "room");
        this.arena = Reflect.get(party, "arena");
    }

    static List<?> runs() {
        return Reflect.get(SelfTest.ENV.roleObject, "runs");
    }

    static Object runOf(UUID id) {
        for (Object run : runs()) {
            if (Reflect.<Boolean>call(run, "has", id)) {
                return run;
            }
        }
        return null;
    }

    /** The running party the player is in, or null (server thread). */
    static Live find(UUID id) {
        Object run = runOf(id);
        if (run == null) {
            return null;
        }
        List<?> parties = Reflect.get(run, "parties");
        for (Object party : parties) {
            List<UUID> members = Reflect.get(party, "members");
            if (members.contains(id) && !Reflect.<Boolean>get(party, "over")) {
                return new Live(run, party);
            }
        }
        return null;
    }

    boolean over() {
        return Reflect.get(party, "over");
    }

    boolean ended() {
        return over() || room.finished();
    }

    Set<UUID> cleared() {
        return over() ? Reflect.get(party, "cleared") : room.cleared();
    }

    long limit() {
        return Reflect.get(party, "limit");
    }

    long startedAt() {
        return Reflect.get(party, "startedAt");
    }

    /** Server ticks the room has been running. */
    long elapsed(int now) {
        return now - startedAt();
    }

    /** Moves the room's clock to its limit, as if the time had run out. */
    void expire(int now) {
        Reflect.set(party, "startedAt", now - limit() - 1);
    }

    String phase() {
        Object phase = Reflect.get(run, "phase");
        return String.valueOf(phase);
    }
}
