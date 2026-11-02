package com.silver.arcade.selftest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;

/** The last suite: nothing is left behind, and nothing outside the test changed. */
public final class Hygiene {
    /** A named probe: returns a problem description, or null when fine. */
    private static final Map<String, Function<T, String>> PROBES = new LinkedHashMap<>();
    private static final Map<String, Function<T, String>> SNAPSHOTS = new LinkedHashMap<>();
    private static final Map<String, String> BEFORE = new LinkedHashMap<>();

    private Hygiene() {
    }

    /** A state captured before the run and compared after (returns a digest). */
    public static void snapshot(String name, Function<T, String> digest) {
        SNAPSHOTS.put(name, digest);
    }

    public static void probe(String name, Function<T, String> problem) {
        PROBES.put(name, problem);
    }

    static void takeBefore(T t) {
        BEFORE.clear();
        SNAPSHOTS.forEach((name, f) -> BEFORE.put(name, f.apply(t)));
    }

    static void register() {
        SelfTest.register(new SelfTest.Suite() {
            public String name() {
                return "hygiene";
            }

            public String role() {
                return "any";
            }

            public void run(T t) {
                Hygiene.run(t);
            }
        });
    }

    static void run(T t) {
        T.cleaning = true; // hygiene checks still run after an abort
        List<UUID> ids = new ArrayList<>(Bots.ledgerUuids().values());
        ids.addAll(Bots.LIVE.values());
        Cleanup.all(t);
        t.ticks(40); // arenas are taken down over a few ticks
        List<String> online = t.on(() -> t.server.getPlayerList().getPlayers().stream()
            .map(p -> p.getGameProfile().name()).filter(n -> n.startsWith(Bots.PREFIX)).toList());
        t.check("no test bots left online", online.isEmpty(), online.toString());
        List<String> files = Bots.leftoverFiles(t.server);
        t.check("no player data, stats or advancement files of the test bots are left", files.isEmpty(), files.toString());
        List<String> cached = Bots.cachedNames(t.server);
        t.check("no test bot name is left in the server's name cache", cached.isEmpty(), cached.toString());
        for (var probe : PROBES.entrySet()) {
            String problem = probe.getValue().apply(t);
            t.check(probe.getKey(), problem == null, problem == null ? "" : problem);
        }
        SNAPSHOTS.forEach((name, f) -> {
            String now = f.apply(t);
            String was = BEFORE.get(name);
            t.check(name + " unchanged by the test", now.equals(was), "before " + was + ", after " + now);
        });
        try (TestDb db = new TestDb()) {
            int left = db.countAll(ids);
            t.check("no test database rows left", left == 0, left + " rows");
            var foreign = db.foreign(ids, false);
            t.check("no rows about test bots in other tables", foreign.isEmpty(), foreign.toString());
        } catch (Exception failure) {
            t.fail("no test database rows left", failure.toString());
        }
        List<String> errors = LogScan.errors();
        t.check("no ERROR lines in the server log during the run", errors.isEmpty(),
            errors.isEmpty() ? "" : errors.size() + " error entries, first: " + errors.get(0));
        if (errors.size() > 1) {
            for (int i = 1; i < Math.min(errors.size(), 8); i++) {
                t.report.notes.add("log error " + (i + 1) + ": " + errors.get(i));
            }
        }
    }
}
