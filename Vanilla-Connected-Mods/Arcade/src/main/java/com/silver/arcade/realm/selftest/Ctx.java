package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.realm.Room;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.T;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** What a room driver works with: the bots in one running room, and ways to act as them and look at the room. */
final class Ctx {
    final T t;
    final Live live;
    final String[] bots;
    final int tier;
    final String label;

    Ctx(T t, Live live, String[] bots, int tier, String label) {
        this.t = t;
        this.live = live;
        this.bots = bots;
        this.tier = tier;
        this.label = label;
        // like a player, act only after the countdown's "Go!" (chat before it is not an answer)
        t.until(() -> t.on(() -> Reflect.<Integer>get(live.party, "countdown")) == 0, 20 * 15);
    }

    int n() {
        return bots.length;
    }

    Room room() {
        return live.room;
    }

    Arena arena() {
        return live.arena;
    }

    /** The bot (server thread). */
    ServerPlayer p(int i) {
        return t.server.getPlayerList().getPlayerByName(bots[i]);
    }

    UUID id(int i) {
        ServerPlayer p = p(i);
        return p == null ? null : p.getUUID();
    }

    int indexOf(UUID id) {
        for (int i = 0; i < bots.length; i++) {
            ServerPlayer p = p(i);
            if (p != null && p.getUUID().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    int indexOf(ServerPlayer player) {
        return player == null ? -1 : indexOf(player.getUUID());
    }

    BlockPos at(int dx, int dy, int dz) {
        return live.arena.at(dx, dy, dz);
    }

    /** A room field (server thread). */
    <V> V f(String name) {
        return Reflect.get(live.room, name);
    }

    boolean ended() {
        return live.ended();
    }

    boolean cleared(int i) {
        UUID id = id(i);
        return id != null && live.cleared().contains(id);
    }

    boolean decided(int i) {
        UUID id = id(i);
        Set<UUID> won = Reflect.get(live.room, "cleared");
        Set<UUID> lost = Reflect.get(live.room, "failed");
        return id == null || won.contains(id) || lost.contains(id);
    }

    /** Where the arena's origin is (absolute). */
    BlockPos o() {
        return live.arena.origin();
    }

    boolean tickIs(int every) {
        return t.tick() % every == 0;
    }

    // --- inputs, all on the server thread: call from inside drive()/on() ---

    void use(int i, int dx, int dy, int dz) {
        In.use(p(i), at(dx, dy, dz));
    }

    void hit(int i, int dx, int dy, int dz) {
        In.hit(p(i), at(dx, dy, dz));
    }

    void say(int i, String text) {
        In.say(p(i), text);
    }

    /** Place the bot relative to the arena (like the room does), keeping it still. */
    void put(int i, double dx, double dy, double dz) {
        live.arena.put(p(i), dx, dy, dz, 0F);
    }

    void putAbs(int i, double x, double y, double z) {
        ServerPlayer p = p(i);
        p.teleportTo(live.arena.level(), x, y, z, Set.of(), p.getYRot(), 0F, false);
        p.setDeltaMovement(0, 0, 0);
        p.resetFallDistance();
    }

    // --- driving ---

    /** Every tick on the server thread: stop when {@code done}, otherwise {@code step}. False when time ran out. */
    boolean drive(BooleanSupplier done, Runnable step, int maxTicks) {
        int end = t.tick() + maxTicks;
        int last = -1;
        while (t.tick() < end) {
            if (t.tick() != last) {
                last = t.tick();
                boolean finished = t.on(() -> {
                    if (done.getAsBoolean()) {
                        return true;
                    }
                    step.run();
                    return false;
                });
                if (finished) {
                    return true;
                }
            }
            T.sleep(2);
        }
        return false;
    }

    boolean driveUntilEnded(Runnable step, int maxTicks) {
        return drive(live::ended, step, maxTicks);
    }

    boolean until(BooleanSupplier cond, int maxTicks) {
        return t.until(cond, maxTicks);
    }

    void ticks(int n) {
        t.ticks(n);
    }

    /** The room's clock runs out now. */
    void expire() {
        t.run(() -> live.expire(t.tick()));
    }

    /** An extra check made by a driver (named under the case). */
    void check(String what, boolean ok, String detail) {
        t.check(label + ": " + what, ok, detail);
    }
}
