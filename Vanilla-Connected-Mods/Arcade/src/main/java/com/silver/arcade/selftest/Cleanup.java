package com.silver.arcade.selftest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Puts everything the test created back: bots, their files, their database rows, the forced-room file. */
public final class Cleanup {
    private static final Path FORCE = FabricLoader.getInstance().getConfigDir().resolve("arcade-force-room.txt");
    private static String forceBackup;
    private static boolean forceTouched;

    private Cleanup() {
    }

    /** Leftovers from a run that was cut short: a ledger of bots, or a stale forced-room backup. */
    static void leftovers(T t) {
        Set<String> old = new LinkedHashSet<>(Bots.ledgerUuids().keySet());
        if (!old.isEmpty()) {
            t.suite = "setup";
            t.ok("leftover bots from an earlier run cleaned", old.toString());
        }
        all(t);
    }

    /** Sets the forced room (remembering what was there). */
    public static void force(String text) throws IOException {
        if (!forceTouched) {
            forceTouched = true;
            forceBackup = Files.exists(FORCE) ? Files.readString(FORCE) : null;
        }
        Files.writeString(FORCE, text);
    }

    public static void unforce() throws IOException {
        if (!forceTouched) {
            return;
        }
        forceTouched = false;
        if (forceBackup != null) {
            Files.writeString(FORCE, forceBackup);
        } else {
            Files.deleteIfExists(FORCE);
        }
        forceBackup = null;
    }

    static void all(T t) {
        T.cleaning = true;
        try {
            unforce();
        } catch (IOException failure) {
            t.fail("forced-room file restored", failure.toString());
        }
        Set<UUID> ids = new LinkedHashSet<>(Bots.ledgerUuids().values());
        ids.addAll(Bots.LIVE.values());
        List<String> names = new ArrayList<>(Bots.LIVE.keySet());
        names.addAll(Bots.ledgerUuids().keySet());
        for (String name : new LinkedHashSet<>(names)) {
            try {
                Bots.kill(t, name);
            } catch (RuntimeException failure) {
                t.fail("bot " + name + " removed", failure.toString());
            }
        }
        if (!names.isEmpty()) {
            t.ticks(20);
        }
        // the product writes some rows asynchronously after a disconnect: remove, wait, remove again
        for (int round = 0; round < 2; round++) {
            try (TestDb db = new TestDb()) {
                db.deleteFor(ids);
                var foreign = db.foreign(ids, true);
                if (!foreign.isEmpty()) {
                    t.report.notes.add("rows other mods wrote about the test bots were removed: " + foreign);
                }
            } catch (Exception failure) {
                t.fail("database rows removed", failure.toString());
                break;
            }
            T.sleep(1200);
        }
        List<String> removed = Bots.clean(t.server);
        if (!removed.isEmpty()) {
            t.report.notes.add("bot files removed: " + removed);
        }
        for (String name : names) {
            PacketLog.unwatch(name);
        }
        Bots.forgetAllNames(t.server);
        Bots.LIVE.clear();
        Bots.WANT_INTRO.clear();
    }
}
