package com.silver.arcade.selftest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.silver.arcade.ArcadeConfig;
import com.silver.arcade.ArcadeDb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The end-to-end self-test. Inert unless {@code config/arcade-selftest.json} exists: then (and only then) the suites it names
 * run on a worker thread, driven by Carpet fake players, and the result goes to a JSON report. See
 * ~/mc-servers/worldwork/arcade/SELFTEST-SPEC.md and bin/arcade-selftest.
 */
public final class SelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade.selftest");
    private static final Gson GSON = new Gson();

    /** A group of checks. {@code server} is the server role it runs on ("realm" or "door"). */
    public interface Suite {
        String name();

        String role();

        void run(T t) throws Exception;
    }

    /** Everything a suite may need from the product. */
    public static final class Env {
        public MinecraftServer server;
        public String role;
        public Object roleObject;
        public ArcadeConfig config;
        public ArcadeDb db;
        public Path reportDir;
        public Path runDir;
    }

    public static final Env ENV = new Env();
    private static final Map<String, Suite> SUITES = new LinkedHashMap<>();
    private static volatile boolean running;
    /** Set when a real player joins during a run that was not forced: the suites stop and everything is cleaned up. */
    static volatile String aborted;
    static volatile boolean forced;
    private static boolean registered;
    private static Path trigger;
    private static Report current;
    private static Path progressFile;
    private static int checksDone;

    private SelfTest() {
    }

    public static void register(Suite suite) {
        SUITES.put(suite.name(), suite);
    }

    /** Called once from the role's register(); does nothing but watch for the trigger file. */
    public static void install(MinecraftServer server, String role, Object roleObject, ArcadeConfig config, ArcadeDb db) {
        ENV.server = server;
        ENV.role = role;
        ENV.roleObject = roleObject;
        ENV.config = config;
        ENV.db = db;
        trigger = FabricLoader.getInstance().getConfigDir().resolve("arcade-selftest.json");
        Bots.mode = role.equals("realm") ? "adventure" : "survival";
        if (role.equals("realm")) {
            com.silver.arcade.realm.selftest.RealmSuites.register();
        } else {
            com.silver.arcade.door.selftest.DoorSuites.register();
        }
        Hygiene.register();
        // Waits for the trigger without any per-tick work (and without the server's ticking: an empty server pauses itself).
        Thread watcher = new Thread(SelfTest::watch, "arcade-selftest-trigger");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void watch() {
        try (java.nio.file.WatchService service = trigger.getParent().getFileSystem().newWatchService()) {
            trigger.getParent().register(service, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                if (!running && Files.exists(trigger)) {
                    ENV.server.execute(SelfTest::begin);
                    T.sleep(1000);
                }
                var key = service.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (key != null) {
                    key.pollEvents();
                    key.reset();
                }
            }
        } catch (IOException | InterruptedException failure) {
            LOGGER.warn("self-test trigger watcher stopped: {}", failure.toString());
        }
    }

    /** Hooks that only matter while a test runs; added on the first run. */
    private static void armOnce() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> {
            ServerPlayer player = handler.getPlayer();
            String name = player.getGameProfile().name();
            if (running && Bots.LIVE.containsKey(name) && !Bots.WANT_INTRO.contains(name)) {
                player.addTag("arcade_met"); // no first-visit scene unless the test asks for it
            }
            if (running && !forced && !Bots.isBot(player) && !Bots.LIVE.containsKey(name)) {
                aborted = name + " joined while the self-test was running";
                LOGGER.warn("[selftest] {}: stopping the test", aborted);
            }
        });
        TickMonitor.install();
        // A client confirms every batch of chunks it receives; without that the server stops sending chunks (and with them the
        // block changes) to the player. The bots confirm too.
        ServerTickEvents.END_SERVER_TICK.register(ticking -> {
            if (running && ticking.getTickCount() % 3 == 0) {
                for (ServerPlayer player : ticking.getPlayerList().getPlayers()) {
                    if (Bots.LIVE.containsKey(player.getGameProfile().name())) {
                        player.connection.handleChunkBatchReceived(new net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket(20F));
                    }
                }
            }
        });
    }

    private static void begin() {
        if (running || !Files.exists(trigger)) {
            return;
        }
        JsonObject request;
        try {
            request = GSON.fromJson(Files.readString(trigger), JsonObject.class);
            Files.deleteIfExists(trigger);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("could not read the self-test trigger", failure);
            try {
                Files.deleteIfExists(trigger);
            } catch (IOException ignored) {
                // nothing more to do
            }
            return;
        }
        running = true;
        LOGGER.info("[selftest] trigger found: {}", request);
        armOnce();
        Thread worker = new Thread(() -> {
            try {
                work(request);
            } catch (Throwable failure) {
                LOGGER.error("[selftest] the worker died before it could run", failure);
                running = false;
            }
        }, "arcade-selftest");
        worker.setDaemon(true);
        worker.start();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    private static void work(JsonObject request) {
        MinecraftServer server = ENV.server;
        Report report = new Report();
        current = report;
        report.id = request.has("id") ? request.get("id").getAsString() : Long.toString(System.currentTimeMillis());
        report.server = ENV.role.equals("realm") ? "arcade" : ENV.config.serverName;
        report.startedAt = Instant.now().toString();
        long t0 = System.nanoTime();
        Path reportPath = Path.of(request.has("report") ? request.get("report").getAsString()
            : server.getServerDirectory().resolve("selftest-report.json").toString());
        ENV.reportDir = reportPath.toAbsolutePath().getParent();
        progressFile = ENV.reportDir.resolve("progress-" + report.server + ".json");
        Path busy = server.getServerDirectory().resolve(".busy");
        Thread keepBusy = new Thread(() -> {
            while (running) {
                try {
                    Files.writeString(busy, "arcade self-test\n");
                } catch (IOException ignored) {
                    // best effort
                }
                T.sleep(20_000);
            }
        }, "arcade-selftest-busy");
        keepBusy.setDaemon(true);
        keepBusy.start();
        T.Options options = new T.Options();
        options.force = request.has("force") && request.get("force").getAsBoolean();
        forced = options.force;
        aborted = null;
        options.quick = request.has("quick") && request.get("quick").getAsBoolean();
        options.rooms = strings(request, "rooms");
        for (String tier : strings(request, "tiers")) {
            options.tiers = new ArrayList<>(options.tiers);
            options.tiers.add(tier.equals("max") ? -1 : Integer.parseInt(tier));
        }
        if (request.has("tickRate")) {
            options.tickRate = request.get("tickRate").getAsFloat();
        }
        T t = new T(server, report, options);
        float originalRate = server.tickRateManager().tickrate();
        try {
            List<String> real = server.submit(() -> {
                List<String> names = new ArrayList<>();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (!Bots.isBot(p)) {
                        names.add(p.getGameProfile().name());
                    }
                }
                return names;
            }).get();
            if (!real.isEmpty() && !options.force) {
                report.refused = "real player(s) online: " + real + " (use --force to run anyway)";
                report.notes.add(report.refused);
                return;
            }
            if (!real.isEmpty()) {
                report.notes.add("forced with real players online: " + real);
            }
            T.cleaning = false;
            ENV.runDir = ENV.reportDir.resolve("run-" + report.server);
            Files.createDirectories(ENV.runDir);
            Bots.setLedger(ENV.runDir.resolve("bots.json"));
            PacketLog.active = true;
            LogScan.begin(server);
            // leftovers of a crashed earlier run
            t.suite = "setup";
            Cleanup.leftovers(t);
            Hygiene.takeBefore(t);
            if (options.tickRate != originalRate) {
                server.submit(() -> server.tickRateManager().setTickRate(options.tickRate)).get();
                report.notes.add("tick rate " + originalRate + " -> " + options.tickRate + " during the run");
            }
            List<String> wanted = strings(request, "suites");
            if (wanted.isEmpty()) {
                wanted = new ArrayList<>(SUITES.keySet());
            } else if (!wanted.contains("hygiene")) {
                wanted = new ArrayList<>(wanted);
                wanted.add("hygiene");
            }
            for (String name : wanted) {
                Suite suite = SUITES.get(name);
                if (suite == null) {
                    t.suite = name;
                    t.fail("suite exists", "no suite " + name + " on this server (have " + SUITES.keySet() + ")");
                    continue;
                }
                if (!suite.role().equals(ENV.role) && !suite.role().equals("any")) {
                    continue;
                }
                report.suitesRun.add(name);
                t.suite = name;
                LOGGER.info("[selftest] suite {} starts", name);
                try {
                    suite.run(t);
                } catch (Throwable failure) {
                    LOGGER.error("[selftest] suite {} threw", name, failure);
                    t.fail("suite ran to the end", failure + " @ " + (failure.getStackTrace().length > 0 ? failure.getStackTrace()[0] : ""));
                }
                LOGGER.info("[selftest] suite {} done", name);
            }
        } catch (Throwable failure) {
            LOGGER.error("[selftest] aborted", failure);
            t.suite = "harness";
            t.fail("harness ran", failure.toString());
        } finally {
            try {
                t.suite = "cleanup";
                if (aborted != null) {
                    report.notes.add("ABORTED: " + aborted);
                    t.fail("no real player joined during the run", aborted);
                }
                Cleanup.all(t);
            } catch (Throwable failure) {
                LOGGER.error("[selftest] cleanup failed", failure);
                t.fail("cleanup finished", failure.toString());
            }
            try {
                server.submit(() -> server.tickRateManager().setTickRate(originalRate)).get();
            } catch (Exception ignored) {
                // the server is going down
            }
            PacketLog.stopAll();
            report.finishedAt = Instant.now().toString();
            report.durationMs = (System.nanoTime() - t0) / 1_000_000;
            report.finish();
            try {
                Files.createDirectories(reportPath.getParent());
                Path tmp = reportPath.resolveSibling(reportPath.getFileName() + ".tmp");
                Files.writeString(tmp, report.json());
                Files.move(tmp, reportPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException failure) {
                LOGGER.error("[selftest] could not write the report", failure);
            }
            LOGGER.info("[selftest] {}: {}", report.passed ? "PASSED" : "FAILED", report.totals.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue()[0] + "/" + (e.getValue()[0] + e.getValue()[1])).toList());
            try {
                Files.deleteIfExists(busy);
                Files.deleteIfExists(progressFile);
            } catch (IOException ignored) {
                // best effort
            }
            running = false;
        }
    }

    /** Called after every check: a small progress file the script shows. */
    static void progress(T t, String check, boolean ok) {
        checksDone++;
        if (progressFile == null || (checksDone % 3 != 0 && ok)) {
            return;
        }
        try {
            JsonObject o = new JsonObject();
            o.addProperty("suite", t.suite);
            o.addProperty("last", check);
            o.addProperty("lastOk", ok);
            o.addProperty("checks", checksDone);
            JsonArray fails = new JsonArray();
            if (current != null) {
                synchronized (current) {
                    current.checks.stream().filter(c -> !c.ok).limit(50).forEach(c -> fails.add(c.suite + ": " + c.check));
                }
            }
            o.add("failures", fails);
            Files.writeString(progressFile, o.toString());
        } catch (IOException ignored) {
            // progress is a nicety
        }
    }

    public static boolean running() {
        return running;
    }
}
