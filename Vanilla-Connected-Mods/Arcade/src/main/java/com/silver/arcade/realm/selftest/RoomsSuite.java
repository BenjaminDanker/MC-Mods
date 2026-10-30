package com.silver.arcade.realm.selftest;

import com.silver.arcade.realm.Arena;
import com.silver.arcade.realm.Room;
import com.silver.arcade.realm.RoomType;
import com.silver.arcade.realm.Rooms;
import com.silver.arcade.selftest.Bots;
import com.silver.arcade.selftest.Cleanup;
import com.silver.arcade.selftest.PacketLog;
import com.silver.arcade.selftest.Reflect;
import com.silver.arcade.selftest.SelfTest;
import com.silver.arcade.selftest.T;
import com.silver.arcade.selftest.TestDb;
import com.silver.arcade.selftest.TickMonitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/**
 * Every room, at tier 1, tier 5 and the highest tier its caps reach: built, decorated, won through real inputs, lost,
 * and torn down again (arena released, ledger gone, nothing left behind).
 */
final class RoomsSuite {
    /** The tier at which every cap of the room has been reached. */
    static final Map<String, Integer> MAX_TIER = new HashMap<>();
    /** Rooms that need company, and how many bots to give them. */
    static final Map<String, Integer> BOTS = new HashMap<>();
    /** Rooms also run with two or three bots together at their first tier. */
    static final Map<String, Integer> EXTRA = new HashMap<>();
    /** Packets (alternatives separated by |) bot 0 must have been sent while the room ran: the room's animations. */
    static final Map<String, List<String>> ANIM = new HashMap<>();

    private static final String BLOCKS = "ClientboundBlockUpdatePacket|ClientboundSectionBlocksUpdatePacket";
    private static final String SOUND = "ClientboundSoundPacket|ClientboundSoundEntityPacket";

    static {
        Object[][] max = {{"typing", 24}, {"math", 22}, {"maze", 26}, {"connect4", 12}, {"memory", 17}, {"parkour", 25}, {"lights_out", 23},
            {"pattern", 8}, {"archery", 9}, {"dropper", 11}, {"falling_floor", 10}, {"red_light", 13}, {"waves", 8}, {"spleef", 12},
            {"sumo", 9}, {"split_simon", 12}, {"block_party", 14}, {"mastermind", 12}, {"slide", 16}, {"pairs", 12}, {"chairs", 10},
            {"hot_potato", 12}, {"king_hill", 13}, {"hide_seek", 15}, {"whack", 14}, {"hanoi", 9}, {"spot", 12}, {"treasure", 10},
            {"lava_rising", 13}, {"hole_wall", 12}};
        for (Object[] m : max) {
            MAX_TIER.put((String) m[0], (Integer) m[1]);
        }
        BOTS.put("spleef", 3);
        BOTS.put("sumo", 2);
        BOTS.put("split_simon", 2);
        BOTS.put("chairs", 3);
        BOTS.put("hot_potato", 3);
        BOTS.put("king_hill", 2);
        BOTS.put("hide_seek", 3);
        EXTRA.put("typing", 3);
        EXTRA.put("falling_floor", 3);
        EXTRA.put("block_party", 3);
        EXTRA.put("lights_out", 2);
        EXTRA.put("memory", 2);
        EXTRA.put("maze", 2);
        EXTRA.put("pattern", 2);
        EXTRA.put("pairs", 2);
        EXTRA.put("slide", 2);
        EXTRA.put("king_hill", 4);
        EXTRA.put("sumo", 2);
        ANIM.put("typing", List.of());
        ANIM.put("memory", List.of(BLOCKS, SOUND));
        ANIM.put("lights_out", List.of(BLOCKS, SOUND));
        ANIM.put("pattern", List.of(BLOCKS));
        ANIM.put("mastermind", List.of(BLOCKS, SOUND));
        ANIM.put("slide", List.of(BLOCKS, SOUND));
        ANIM.put("pairs", List.of(BLOCKS, SOUND));
        ANIM.put("whack", List.of(BLOCKS, SOUND));
        ANIM.put("hanoi", List.of(BLOCKS, SOUND));
        ANIM.put("spot", List.of(BLOCKS, SOUND));
        ANIM.put("treasure", List.of(BLOCKS));
        ANIM.put("connect4", List.of(BLOCKS));
        ANIM.put("split_simon", List.of(BLOCKS, SOUND));
        ANIM.put("waves", List.of("ClientboundAddEntityPacket"));
        ANIM.put("hot_potato", List.of("ClientboundLevelParticlesPacket", SOUND));
        ANIM.put("falling_floor", List.of(BLOCKS));
        ANIM.put("block_party", List.of(BLOCKS));
        ANIM.put("lava_rising", List.of(BLOCKS));
        ANIM.put("spleef", List.of(BLOCKS));
        ANIM.put("archery", List.of(BLOCKS));
        ANIM.put("hole_wall", List.of(BLOCKS));
        ANIM.put("chairs", List.of(SOUND));
        ANIM.put("red_light", List.of("ClientboundSetTitleTextPacket~Green"));
        ANIM.put("parkour", List.of());
    }

    /** Prizes the cases expect to find in the database: bot uuid -> "server:tier" in order. */
    private static final Map<UUID, List<String>> EXPECTED_PRIZES = new HashMap<>();
    private static final Set<String> SEEN_CASES = new LinkedHashSet<>();

    private RoomsSuite() {
    }

    static void run(T t) throws Exception {
        EXPECTED_PRIZES.clear();
        // The recorder works: the first bot's join packets were captured
        String first = Pool.ensure(t, 1)[0];
        t.ticks(10);
        int joined = PacketLog.snapshot(first).size();
        t.check("packet recorder sees a bot's packets", joined > 0, joined + " packets so far");
        // the bots obey gravity (the falling rooms depend on it)
        String[] pool = Pool.ensure(t, 4);
        for (String name : pool) {
            t.run(() -> {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
                p.teleportTo(t.server.overworld(), 40.5, 140, 40.5, java.util.Set.of(), 0F, 0F, false);
                p.setDeltaMovement(0, 0, 0);
            });
            t.ticks(20);
            String state = t.on(() -> {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(name);
                return String.format("y=%.2f flying=%s mayfly=%s noGravity=%s mode=%s", p.getY(), p.getAbilities().flying, p.getAbilities().mayfly, p.isNoGravity(), p.gameMode());
            });
            double y = t.on(() -> t.server.getPlayerList().getPlayerByName(name).getY());
            t.check("bot " + name + " falls when nothing is under it", y < 139, state);
        }
        t.run(() -> Pool.park(t, t.server.getPlayerList().getPlayerByName(pool[0]), 0));
        for (String name : pool) {
            t.run(() -> Pool.park(t, t.server.getPlayerList().getPlayerByName(name), 0));
        }
        // One throw-away room first: the first room after a server start pays for class loading and JIT, which is not what is being measured.
        int before = t.report.checks.size();
        Drivers.Driver warm = Drivers.ALL.get("typing");
        RoomType typing = Rooms.all().stream().filter(x -> x.id().equals("typing")).findFirst().orElse(null);
        if (typing != null && t.options.rooms.isEmpty()) {
            runCase(t, typing, warm, 1, 1, true);
            synchronized (t.report) {
                while (t.report.checks.size() > before) {
                    t.report.checks.remove(t.report.checks.size() - 1);
                }
            }
            t.report.notes.add("rooms: one unrecorded warm-up room (typing, tier 1) is played first");
        }
        List<RoomType> types = new ArrayList<>(Rooms.all());
        t.check("every room type is registered (30)", types.size() == 30, types.size() + " room types");
        for (RoomType type : types) {
            if (!t.options.rooms.isEmpty() && !t.options.rooms.contains(type.id())) {
                continue;
            }
            Drivers.Driver driver = Drivers.ALL.get(type.id());
            if (driver == null) {
                t.fail(type.id() + ": has a test driver", "no driver for " + type.id());
                continue;
            }
            for (int tier : tiersOf(t, type)) {
                int bots = BOTS.getOrDefault(type.id(), type.minPlayers());
                for (boolean win : new boolean[] {true, false}) {
                    runCase(t, type, driver, tier, bots, win);
                }
            }
            Integer extra = EXTRA.get(type.id());
            if (extra != null && extra != BOTS.getOrDefault(type.id(), type.minPlayers()) && !t.options.quick) {
                int tier = Math.max(1, type.minTier());
                for (boolean win : new boolean[] {true, false}) {
                    runCase(t, type, driver, tier, Math.min(extra, type.maxPlayers()), win);
                }
            }
        }
        verifyPrizes(t);
    }

    static List<Integer> tiersOf(T t, RoomType type) {
        int max = MAX_TIER.getOrDefault(type.id(), 10);
        List<Integer> want = new ArrayList<>();
        if (!t.options.tiers.isEmpty()) {
            for (int tier : t.options.tiers) {
                want.add(tier < 0 ? max : tier);
            }
        } else if (t.options.quick) {
            want.add(1);
        } else {
            want.add(1);
            want.add(5);
            want.add(max);
        }
        List<Integer> out = new ArrayList<>();
        for (int tier : want) {
            int use = Math.max(tier, type.minTier());
            if (!out.contains(use)) {
                out.add(use);
            }
        }
        return out;
    }

    private static void runCase(T t, RoomType type, Drivers.Driver driver, int tier, int nBots, boolean win) throws Exception {
        String label = type.id() + "@t" + tier + "/" + (win ? "win" : "lose") + "/" + nBots + "b";
        t.guard(label + ": case ran", () -> runCase0(t, type, driver, tier, nBots, win, label));
    }

    private static void runCase0(T t, RoomType type, Drivers.Driver driver, int tier, int nBots, boolean win, String label) throws Exception {
        String[] bots;
        try {
            bots = Pool.ensure(t, nBots);
        } catch (RuntimeException failure) {
            t.fail(label + ": bots ready in the hall", failure.toString());
            return;
        }
        Cleanup.force(type.id() + " " + tier);
        Map<String, Long> marks = new HashMap<>();
        for (String name : bots) {
            marks.put(name, PacketLog.mark(name));
        }
        TickMonitor.reset();
        long start = System.nanoTime();
        List<UUID> ids = t.on(() -> Pool.players(t, bots).stream().map(ServerPlayer::getUUID).toList());
        Live live;
        try {
            live = t.on(() -> {
                Reflect.call(SelfTest.ENV.roleObject, "startRun", new ArrayList<>(Pool.players(t, bots)), nBots > 1);
                return Live.find(ids.get(0));
            });
        } catch (RuntimeException failure) {
            Throwable root = failure;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            t.fail(label + ": room builds without an exception", root + " @ " + (root.getStackTrace().length > 0 ? root.getStackTrace()[0] : ""));
            return;
        }
        if (live == null) {
            t.fail(label + ": room started", "no room is running for the bots");
            return;
        }
        boolean forced = live.room.id().equals(type.id());
        t.check(label + ": the forced room is the one that runs", forced, "got " + live.room.id());
        if (!forced) {
            t.run(() -> live.expire(t.tick()));
            t.until(live::over, 200);
            return;
        }
        t.ok(label + ": room builds without an exception");
        long buildMs = (System.nanoTime() - start) / 1_000_000;
        boolean decorated = t.until(() -> !live.arena.decorating(), 20 * 60);
        long decoMs = (System.nanoTime() - start) / 1_000_000;
        t.check(label + ": decoration finishes within 10 s", decorated && decoMs <= 10_000, "build call " + buildMs + " ms, decoration done after " + decoMs + " ms");
        long worstBuild = TickMonitor.max();
        t.check(label + ": worst tick while building and decorating < 60 ms", worstBuild < 60, worstBuild + " ms (ticks over 60 ms: " + TickMonitor.over60() + ")");
        List<BlockPos> ledger = t.on(() -> new ArrayList<>(Reflect.<java.util.Deque<BlockPos>>get(live.arena, "placed")));
        int slot = Reflect.get(live.arena, "slot");
        int arenaX = live.arena.origin().getX();
        // --- play
        Ctx ctx = new Ctx(t, live, bots, tier, label);
        TickMonitor.reset();
        int loser = win ? -1 : 0;
        int playStart = t.tick();
        t.guard(label + ": driver ran", () -> driver.play(ctx, loser));
        if (!t.on(live::over)) {
            if (!win && !t.on(live::ended)) {
                t.run(() -> live.expire(t.tick())); // the clock runs out
            }
        }
        boolean over = t.until(live::over, 20 * 60);
        t.check(label + ": the room ends (" + (t.tick() - playStart) + " ticks of play)", over, over ? "" : "still running after the driver and 60 s");
        if (!over) {
            t.run(() -> live.expire(t.tick()));
            t.until(live::over, 200);
        }
        long worstPlay = TickMonitor.max();
        t.check(label + ": worst tick while playing < 60 ms", worstPlay < 60, worstPlay + " ms (ticks over 60 ms: " + TickMonitor.over60() + ")");
        Set<UUID> cleared = t.on(live::cleared);
        boolean versus = nBots > 1 && !ANIM_COOP.contains(type.id());
        if (win) {
            if (versus && !type.id().equals("hide_seek")) {
                t.check(label + ": bot 0 alone wins", cleared.equals(Set.of(ids.get(0))), "cleared " + cleared.size() + " of " + nBots + ", bot0 in: " + cleared.contains(ids.get(0)));
            } else if (type.id().equals("hide_seek")) {
                t.check(label + ": bot 0 wins", cleared.contains(ids.get(0)), "cleared " + cleared.size() + " of " + nBots);
            } else {
                t.check(label + ": every bot clears the room", cleared.containsAll(ids), "cleared " + cleared.size() + " of " + nBots + "; bot 0 at "
                    + t.on(() -> {
                        ServerPlayer p = t.server.getPlayerList().getPlayerByName(bots[0]);
                        return p == null ? "gone" : String.format("%.1f %.1f %.1f (arena origin %s) mode %s", p.getX(), p.getY(), p.getZ(), live.arena.origin(), p.gameMode());
                    }));
            }
        } else {
            t.check(label + ": bot 0 does not clear", !cleared.contains(ids.get(0)), "cleared " + cleared.size());
            if (versus) {
                t.check(label + ": someone else wins the versus room", !cleared.isEmpty(), "cleared " + cleared.size());
            }
        }
        // --- what the player was told and shown
        List<PacketLog.Rec> got = PacketLog.since(bots[0], marks.get(bots[0]));
        t.check(label + ": the title with the room's name was sent", PacketLog.hasText(got, "ClientboundSetTitleTextPacket", live.room.title()), "");
        t.check(label + ": the timer bar was sent", PacketLog.has(got, "ClientboundBossEventPacket"), "");
        if (win && cleared.contains(ids.get(0))) {
            t.check(label + ": 'Cleared!' bar shown", PacketLog.hasText(got, "ClientboundSystemChatPacket", "Cleared!"), "");
        }
        if (win && cleared.contains(ids.get(0))) {
            for (String need : ANIM.getOrDefault(type.id(), List.of())) {
                boolean seen = need.contains("~") ? PacketLog.hasText(got, need.split("~")[0], need.split("~")[1]) : PacketLog.hasAny(got, need);
                t.check(label + ": animation packets seen (" + need.replace("Clientbound", "").replace("Packet", "") + ")", seen, "");
            }
        }
        // --- the way out
        if (cleared.contains(ids.get(0))) {
            afterWin(t, live, bots, ids, cleared, tier, label, marks);
        } else {
            afterLoss(t, live, bots, ids, cleared, tier, label, marks);
        }
        // --- the arena is released and left clean
        int releasedAt = t.tick();
        boolean freed = t.until(() -> !Reflect.<BitSet>getStatic(Arena.class, "USED").get(slot), 20 * 150);
        t.check(label + ": arena slot released (" + (t.tick() - releasedAt) + " ticks of tear-down for " + ledger.size() + " blocks)", freed, "slot " + slot);
        Path ledgerFile = t.server.getWorldPath(LevelResource.ROOT).resolve("arcade-arenas").resolve("slot-" + slot + ".bin");
        t.check(label + ": ledger file gone", freed && !Files.exists(ledgerFile), ledgerFile.getFileName().toString());
        String[] sample = {""};
        int solid = t.on(() -> {
            int n = 0;
            StringBuilder out = new StringBuilder();
            for (BlockPos pos : ledger) {
                if (!t.server.overworld().getBlockState(pos).isAir()) {
                    if (n++ < 4) {
                        out.append(pos.toShortString()).append(' ').append(t.server.overworld().getBlockState(pos).getBlock().getDescriptionId())
                            .append(" chunkLoaded=").append(t.server.overworld().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)).append("; ");
                    }
                }
            }
            sample[0] = out.toString();
            return n;
        });
        t.check(label + ": every ledger position is air again (" + ledger.size() + " blocks)", freed && solid == 0, solid + " blocks left, e.g. " + sample[0]);
        int stray = t.on(() -> {
            AABB slotBox = new AABB(arenaX - 256, Arena.BASE_Y - 64, -256, arenaX + 256, Arena.BASE_Y + 64, 256);
            return t.server.overworld().getEntities((net.minecraft.world.entity.Entity) null, slotBox,
                e -> !(e instanceof ServerPlayer) && (e.entityTags().contains("arcade_room")
                    || e instanceof net.minecraft.world.entity.projectile.Projectile
                    || e instanceof net.minecraft.world.entity.item.ItemEntity)).size();
        });
        t.check(label + ": no room entities left in the slot", stray == 0, stray + " entities");
    }

    /** Rooms where every bot wins or loses together. */
    private static final Set<String> ANIM_COOP = Set.of("typing", "math", "maze", "connect4", "memory", "parkour", "lights_out", "pattern", "archery",
        "dropper", "falling_floor", "red_light", "waves", "mastermind", "slide", "pairs", "whack", "hanoi", "spot", "treasure", "lava_rising",
        "hole_wall", "block_party", "split_simon");

    private static void afterWin(T t, Live live, String[] bots, List<UUID> ids, Set<UUID> cleared, int tier, String label,
            Map<String, Long> marks) {
        // survivors stand on the choice platform; the others are back in the hall
        boolean choice = t.until(() -> "CHOICE".equals(live.phase()) || "DONE".equals(live.phase()), 100);
        Object choiceArena = t.on(() -> Reflect.get(live.run, "choiceArena"));
        t.check(label + ": the run reaches the choice platform", choice && choiceArena != null, "phase " + t.on(live::phase));
        List<PacketLog.Rec> got = PacketLog.since(bots[0], marks.get(bots[0]));
        t.check(label + ": 'Tier " + tier + " cleared' title sent", PacketLog.hasText(got, "ClientboundSetTitleTextPacket", "cleared"), "");
        if (choiceArena instanceof Arena choose) {
            boolean there = t.on(() -> {
                ServerPlayer p = t.server.getPlayerList().getPlayerByName(bots[0]);
                return choose.contains(p.blockPosition()) && p.getX() > 256;
            });
            t.check(label + ": the winner stands on the choice platform", there, "");
            // gold: cash out
            t.run(() -> {
                for (int i = 0; i < bots.length; i++) {
                    if (cleared.contains(ids.get(i))) {
                        choose.put(t.server.getPlayerList().getPlayerByName(bots[i]), -4.5, 1, 0.5, 180F);
                    }
                }
            });
        }
        boolean home = t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[0])) == null, 20 * 15);
        t.check(label + ": gold cashes out: bot is back in the hall (adventure, empty, full health)", home,
            home ? "" : String.valueOf(t.on(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[0])))));
        for (int i = 0; i < bots.length; i++) {
            if (cleared.contains(ids.get(i))) {
                EXPECTED_PRIZES.computeIfAbsent(ids.get(i), k -> new ArrayList<>()).add("vanilla:" + tier);
            }
        }
        // the others lost
        for (int i = 1; i < bots.length; i++) {
            if (!cleared.contains(ids.get(i))) {
                int idx = i;
                boolean back = t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[idx])) == null, 20 * 15);
                t.check(label + ": bot " + i + " (lost) is back in the hall", back,
                    back ? "" : String.valueOf(t.on(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[idx])))));
            }
        }
    }

    private static void afterLoss(T t, Live live, String[] bots, List<UUID> ids, Set<UUID> cleared, int tier, String label,
            Map<String, Long> marks) {
        boolean back = t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[0])) == null, 20 * 15);
        t.check(label + ": the lost bot is back in the hall (adventure, empty, full health)", back,
            back ? "" : String.valueOf(t.on(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[0])))));
        List<PacketLog.Rec> got = PacketLog.since(bots[0], marks.get(bots[0]));
        t.check(label + ": 'The house wins' told to the loser", PacketLog.hasText(got, "ClientboundSystemChatPacket", "The house wins"), "");
        // a versus winner (not bot 0) goes on to the choice platform: take the gold
        for (int i = 1; i < bots.length; i++) {
            if (cleared.contains(ids.get(i))) {
                int idx = i;
                Object choiceArena = null;
                t.until(() -> Reflect.get(live.run, "choiceArena") != null, 100);
                choiceArena = t.on(() -> Reflect.get(live.run, "choiceArena"));
                if (choiceArena instanceof Arena choose) {
                    t.run(() -> choose.put(t.server.getPlayerList().getPlayerByName(bots[idx]), -4.5, 1, 0.5, 180F));
                    EXPECTED_PRIZES.computeIfAbsent(ids.get(i), k -> new ArrayList<>()).add("vanilla:" + tier);
                }
            }
        }
        for (int i = 1; i < bots.length; i++) {
            int idx = i;
            t.until(() -> Pool.hallProblem(t.server.getPlayerList().getPlayerByName(bots[idx])) == null, 20 * 15);
        }
    }

    private static void verifyPrizes(T t) {
        t.ticks(20);
        T.sleep(1500); // the database writes are asynchronous
        try (TestDb db = new TestDb()) {
            int total = 0;
            int wrong = 0;
            StringBuilder detail = new StringBuilder();
            for (var entry : EXPECTED_PRIZES.entrySet()) {
                List<String> want = new ArrayList<>(entry.getValue());
                List<String> have = new ArrayList<>(db.prizes(entry.getKey()));
                total += want.size();
                List<String> a = new ArrayList<>(want);
                List<String> b = new ArrayList<>(have);
                java.util.Collections.sort(a);
                java.util.Collections.sort(b);
                if (!a.equals(b)) {
                    wrong++;
                    detail.append(entry.getKey()).append(" wanted ").append(a).append(" has ").append(b).append("; ");
                }
            }
            t.check("every cash-out left a prize row for the bot's origin server with the right tier (" + total + " prizes)", wrong == 0, detail.toString());
        } catch (Exception failure) {
            t.fail("prize rows readable", failure.toString());
        }
    }
}
