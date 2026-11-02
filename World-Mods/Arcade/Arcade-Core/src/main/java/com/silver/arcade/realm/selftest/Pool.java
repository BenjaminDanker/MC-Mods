package com.silver.arcade.realm.selftest;

import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.T;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** A small set of bots kept alive across the cases of a suite, always parked in the hall between cases. */
final class Pool {
    private Pool() {
    }

    static String name(int i) {
        return Bots.PREFIX + "b" + (i + 1);
    }

    /** Makes sure {@code n} bots exist and are free in the hall; returns their names. */
    static String[] ensure(T t, int n) {
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            names[i] = name(i);
            if (Bots.get(t, names[i]) == null) {
                Bots.spawn(t, names[i], 0.5, 101, 0.5 + 0.0 * i, false);
            }
        }
        // free: not in a run
        boolean free = t.until(() -> {
            for (String name : names) {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
                if (p == null || Live.runOf(p.getUUID()) != null) {
                    return false;
                }
            }
            return true;
        }, 20 * 120);
        if (!free) {
            throw new IllegalStateException("bots are still in a run");
        }
        t.run(() -> {
            for (String name : names) {
                park(t, t.server.getPlayerList().getPlayerByName(name), 0);
            }
        });
        t.ticks(2);
        return names;
    }

    /** Back to the hall as the product leaves players there (adventure, empty, full health). */
    static void park(T t, ServerPlayer p, int slot) {
        p.setGameMode(GameType.ADVENTURE);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.teleportTo(t.server.overworld(), 0.5, 101, 0.5, java.util.Set.of(), 0F, 0F, false);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.setDeltaMovement(0, 0, 0);
    }

    static List<ServerPlayer> players(T t, String[] names) {
        List<ServerPlayer> out = new ArrayList<>();
        for (String name : names) {
            out.add(t.server.getPlayerList().getPlayerByName(name));
        }
        return out;
    }

    /** Whether a bot is where the product leaves a player who comes back from a room. */
    static String hallProblem(ServerPlayer p) {
        if (p == null) {
            return "offline";
        }
        List<String> bad = new ArrayList<>();
        if (p.getX() > 100 || p.getX() < -100 || p.getY() < 99 || p.getY() > 130) {
            bad.add("position " + p.position());
        }
        if (p.gameMode() != GameType.ADVENTURE) {
            bad.add("game mode " + p.gameMode());
        }
        if (!p.getInventory().isEmpty()) {
            bad.add("inventory not empty");
        }
        if (p.getHealth() < p.getMaxHealth()) {
            bad.add("health " + p.getHealth());
        }
        return bad.isEmpty() ? null : String.join(", ", bad);
    }
}
