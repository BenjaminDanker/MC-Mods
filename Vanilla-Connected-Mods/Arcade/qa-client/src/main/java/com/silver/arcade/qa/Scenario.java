package com.silver.arcade.qa;

import com.silver.qaclient.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** The playthrough: intro cutscene, hall, a door, then each requested room, the choice platform and the exit gate. */
public final class Scenario {
    private final Bot b;
    private final Solvers solve;
    private final Path force = Path.of(System.getProperty("arcadeqa.force", "force.txt"));
    private final int tier = Integer.getInteger("arcadeqa.tier", 1);
    private final int chunkRadius = Integer.getInteger("arcadeqa.chunkRadius", 6);
    private final int settle = Integer.getInteger("arcadeqa.settle", 60);
    private final String choice = System.getProperty("arcadeqa.choice", "gold");
    private static final String[] ALL = {"lights_out", "crack_the_code", "copy_the_picture", "pairs", "sliding_picture", "spot_the_difference", "tower", "whack_a_mole", "buried_treasure",
        "typing_race", "mental_math", "maze", "connect_four", "simon_says", "parkour", "archery", "drop", "floor_is_falling", "red_light_green_light", "hold_the_ring",
        "block_party", "rising_floor", "hole_in_the_wall"};
    // Rooms that need other players (min party of 2 or 3): see companions().
    private static final String[] GROUP = {"spleef", "sumo", "two_booths", "musical_chairs", "hot_potato", "king_of_the_hill", "hide_and_seek"};

    public Scenario(Bot b) {
        this.b = b;
        this.solve = new Solvers(b);
    }

    private void step(String s) {
        Rec.step = s;
        Rec.event("step", "name", s);
    }

    public void run() {
        Rec.dir.toFile().mkdirs();
        step("intro");
        intro();
        step("hall");
        hallTour();
        String spec = System.getProperty("arcadeqa.rooms", "all");
        List<String> rooms = new ArrayList<>();
        if (spec.equals("all")) {
            rooms.addAll(List.of(ALL));
        } else if (spec.equals("none")) {
            // chunk-only run
        } else if (spec.equals("group")) {
            rooms.addAll(List.of(GROUP));
        } else {
            rooms.addAll(List.of(spec.split(",")));
        }
        boolean first = true;
        for (String room : rooms) {
            try {
                playRoom(room, first);
            } catch (Throwable t) {
                Rec.event("room_crash", "error", t.toString());
                t.printStackTrace();
            }
            first = false;
        }
        if (Boolean.parseBoolean(System.getProperty("arcadeqa.exit", "true"))) {
            step("exit-gate");
            exitGate();
        }
        Rec.room = "-";
        step("end");
    }

    // -------------------------------------------------------------- intro and hall

    private void intro() {
        Rec.event("joined", "pos", b.pos().toString(), "dimension", b.call(() -> b.mc.level.dimension().identifier().toString()),
            "gameMode", b.call(() -> String.valueOf(b.mc.gameMode.getPlayerMode())));
        long start = Rec.tick;
        int n = 0;
        boolean skippable = true;
        while (Rec.tick - start < 360) {
            if (n % 1 == 0) {
                b.shot("cutscene-" + n + "-t" + (Rec.tick - start));
            }
            n++;
            b.ticks(60);
            if (!Hud.curAction.toLowerCase().contains("sneak")) {
                skippable = false;
                break;
            }
        }
        Rec.event("cutscene_watched", "ticks", Rec.tick - start, "stillShowingSkipHint", skippable, "pos", b.pos().toString());
        if (skippable) {
            b.run(() -> b.mc.options.keyShift.setDown(true));
            b.ticks(12);
            b.run(() -> b.mc.options.keyShift.setDown(false));
            b.ticks(40);
            Rec.event("cutscene_skipped", "ticks", Rec.tick - start, "pos", b.pos().toString());
        }
        b.ticks(40);
        b.shot("after-cutscene");
    }

    private void hallTour() {
        Chunks.report(b, "hall-arrival", chunkRadius);
        b.ticks(settle);
        Chunks.report(b, "hall-settled", chunkRadius);
        if (Boolean.getBoolean("arcadeqa.spin")) {
            for (int i = 0; i < 4; i++) {
                final float yaw = i * 90f;
                b.run(() -> { b.mc.player.setYRot(yaw); b.mc.player.setXRot(0f); });
                b.ticks(40);
                b.shot("hall-spin-" + i);
            }
            Chunks.report(b, "hall-spun", chunkRadius);
        }
        b.shot("hall-arrival");
        Rec.event("hud_hall", "title", Hud.curTitle, "sub", Hud.curSub, "action", Hud.curAction, "bars", Hud.curBars);
    }

    private boolean inHall() {
        Vec3 p = b.pos();
        return Math.abs(p.x) < 120 && Math.abs(p.z) < 160;
    }

    private void goToDoor(String room) {
        Rec.event("walk_to_door", "from", b.pos().toString());
        boolean ok = b.walkTo(20.5, 22.5, 1.5, 700);
        Rec.event("walked", "stage", "to x=20", "ok", ok, "pos", b.pos().toString());
        if (!ok) {
            b.shot("stuck-in-hall");
            Chunks.report(b, "hall-stuck", 3);
        }
        b.shot("at-door");
        b.walkTo(28.5, 22.5, 0.6, 200);
        Rec.event("walked", "stage", "through door", "pos", b.pos().toString());
    }

    // -------------------------------------------------------------- rooms

    private void playRoom(String room, boolean first) {
        Rec.room = room;
        step("room:" + room);
        solve.reset();
        Hud.resetCounters();
        try {
            Files.writeString(force, room + " " + tier + "\n");
        } catch (Exception e) {
            Rec.event("force_failed", "error", e.toString());
        }
        // Back in the hall? (After the first room we arrive there again.)
        if (!b.until(this::inHall, 400)) {
            Rec.event("not_in_hall", "pos", b.pos().toString());
        }
        b.ticks(20);
        int since = Hud.log.size();
        long t0 = Rec.tick;
        goToDoor(room);
        // Wait for the room to start: a boss bar appears, or the player is moved far away.
        boolean started = b.until(() -> !inHall() || !Hud.curBars.isEmpty() && Hud.log.size() > since + 1 && !Hud.curBars.contains("The game begins"), 800);
        Rec.event("door_wait", "startedMarker", started, "ticks", Rec.tick - t0, "pos", b.pos().toString(), "bars", Hud.curBars);
        b.shot("door-wait");
        if (!b.until(() -> !inHall(), 400)) {
            Rec.event("room_not_reached", "pos", b.pos().toString(), "bars", Hud.curBars);
            b.shot("room-not-reached");
            return;
        }
        long roomStart = Rec.tick;
        int hudStart = Hud.log.size();
        b.ticks(60);
        Rec.event("room_start", "pos", b.pos().toString(), "bars", Hud.curBars, "held", Hud.held(b.mc), "reach", b.reachLimit(),
            "dimension", b.call(() -> b.mc.level.dimension().identifier().toString()), "gameMode", b.call(() -> String.valueOf(b.mc.gameMode.getPlayerMode())),
            "food", b.call(() -> b.mc.player.getFoodData().getFoodLevel()), "health", b.call(() -> (double) b.mc.player.getHealth()));
        b.shot("start");
        Chunks.report(b, room + "-start", chunkRadius);
        b.ticks(settle);
        Chunks.report(b, room + "-settled", chunkRadius);
        // !explain
        int c0 = Hud.log.size();
        b.chat("!explain");
        b.ticks(30);
        Rec.event("explain_reply", "lines", String.join(" | ", Hud.log.subList(c0, Hud.log.size())));
        b.shot("after-explain");
        // Play.
        step("play:" + room);
        try {
            switch (room) {
                case "lights_out" -> solve.lightsOut();
                case "crack_the_code" -> solve.mastermind();
                case "copy_the_picture" -> solve.pattern();
                case "pairs" -> solve.pairs();
                case "sliding_picture" -> solve.slide();
                case "spot_the_difference" -> solve.spot();
                case "tower" -> solve.hanoi();
                case "whack_a_mole" -> solve.whack();
                case "buried_treasure" -> solve.treasure();
                case "typing_race" -> solve.typing();
                case "mental_math" -> solve.math();
                default -> observe(room);
            }
        } catch (Throwable t) {
            Rec.event("solver_crash", "error", t.toString());
            t.printStackTrace();
        }
        b.stopMoving();
        b.shot("after-play");
        Chunks.report(b, room + "-end", 6);
        // Outcome.
        boolean won = b.until(() -> Hud.seen(hudStart, "cleared") || Hud.seen(hudStart, "tier ") && Hud.seen(hudStart, "gold"), 200);
        boolean lost = !won && (Hud.seen(hudStart, "failed") || Hud.seen(hudStart, "lost") || Hud.seen(hudStart, "out of"));
        Rec.event("outcome", "won", won, "lost", lost, "ticksInRoom", Rec.tick - roomStart, "titles", Hud.titles, "actionbars", Hud.actionbars,
            "bossNames", Hud.bossNames, "chats", Hud.chats, "clicks", solve.clicks, "noEffect", solve.noEffect, "unaimable", solve.unreachable,
            "maxReach", Solvers.round(solve.maxReach));
        if (won) {
            choose(hudStart);
        } else {
            if (Boolean.parseBoolean(System.getProperty("arcadeqa.giveup", "true"))) {
                giveUp();
            }
            waitHall();
        }
    }

    /** Rooms the driver does not play to a win: look around, move a little, record. */
    private void observe(String room) {
        Rec.event("observe_only", "room", room);
        for (int i = 0; i < 4; i++) {
            b.ticks(50);
            b.shot("observe-" + i);
            Rec.event("pos", "pos", b.pos().toString(), "bars", Hud.curBars, "action", Hud.curAction);
        }
        // walk forward a bit like someone exploring
        b.run(() -> {
            b.mc.options.keyUp.setDown(true);
        });
        b.ticks(60);
        b.stopMoving();
        b.shot("observe-walked");
        Chunks.report(b, room + "-observed", 6);
    }

    private void giveUp() {
        int c0 = Hud.log.size();
        b.chat("/giveup");
        b.ticks(40);
        Rec.event("giveup_reply", "lines", String.join(" | ", Hud.log.subList(c0, Hud.log.size())), "pos", b.pos().toString());
        b.shot("after-giveup");
    }

    private void waitHall() {
        boolean ok = b.until(this::inHall, 1200);
        b.ticks(30);
        Rec.event("back_in_hall", "ok", ok, "pos", b.pos().toString());
        b.shot("back-in-hall");
    }

    /** The choice platform: gold block on one side, emerald on the other. */
    private void choose(int hudStart) {
        step("choice");
        b.ticks(60);
        b.shot("choice-platform");
        Chunks.report(b, "choice", 5);
        Rec.event("choice_hud", "title", Hud.curTitle, "sub", Hud.curSub, "bars", Hud.curBars, "action", Hud.curAction);
        List<BlockPos> gold = W.scanExact(b, 10, 3, 10, "gold_block");
        List<BlockPos> emerald = W.scanExact(b, 10, 3, 10, "emerald_block");
        Rec.event("choice_blocks", "gold", gold.size(), "emerald", emerald.size());
        List<BlockPos> side = choice.equals("emerald") ? emerald : gold;
        if (side.isEmpty()) {
            Solvers.problem("choice platform: no " + choice + " blocks within 10 blocks of where the player stands");
            return;
        }
        BlockPos target = side.get(side.size() / 2);
        boolean ok = b.walkTo(target.getX() + 0.5, target.getZ() + 0.5, 0.8, 300);
        Rec.event("walked_to_choice", "side", choice, "ok", ok, "pos", b.pos().toString());
        b.ticks(40);
        b.shot("choice-standing");
        if (choice.equals("emerald")) {
            // the next tier starts in a new room
            b.until(() -> Hud.seen(hudStart + 1, "tier 2") || Hud.curBars.contains("Tier 2"), 400);
            b.ticks(60);
            b.shot("next-tier");
            Rec.event("next_tier_hud", "bars", Hud.curBars, "title", Hud.curTitle, "sub", Hud.curSub);
            giveUp();
        }
        waitHall();
    }

    // -------------------------------------------------------------- exit

    private void exitGate() {
        if (!inHall()) {
            waitHall();
        }
        Rec.event("walk_to_exit", "from", b.pos().toString());
        boolean ok = b.walkTo(0.5, -6.0, 1.5, 700);
        b.shot("exit-approach");
        b.walkTo(0.5, -8.5, 0.8, 200);
        b.ticks(60);
        Rec.event("exit_walked", "ok", ok, "pos", b.pos().toString());
        b.shot("exit-gate");
        b.ticks(60);
    }
}
