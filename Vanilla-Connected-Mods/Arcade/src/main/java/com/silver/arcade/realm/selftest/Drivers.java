package com.silver.arcade.realm.selftest;

import com.silver.arcade.selftest.Reflect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * One driver per room: plays it with real inputs (use, hit, chat, movement, attacks). {@code play(ctx, loser)} makes every
 * bot win, except bot {@code loser} (when >= 0) which plays to lose: a wrong answer, a wrong pad, falling, or doing
 * nothing (the caller then runs the room's clock out). Where a room is versus, "win" means bot 0 wins.
 */
final class Drivers {
    @FunctionalInterface
    interface Driver {
        void play(Ctx c, int loser) throws Exception;
    }

    static final Map<String, Driver> ALL = new LinkedHashMap<>();

    static {
        ALL.put("typing_race", Drivers::typing);
        ALL.put("mental_math", Drivers::math);
        ALL.put("maze", Drivers::maze);
        ALL.put("connect_four", Drivers::connect4);
        ALL.put("simon_says", Drivers::memory);
        ALL.put("parkour", Drivers::parkour);
        ALL.put("lights_out", Drivers::lightsOut);
        ALL.put("copy_the_picture", Drivers::pattern);
        ALL.put("archery", Drivers::archery);
        ALL.put("drop", Drivers::dropper);
        ALL.put("floor_is_falling", Drivers::fallingFloor);
        ALL.put("red_light_green_light", Drivers::redLight);
        ALL.put("hold_the_ring", Drivers::waves);
        ALL.put("spleef", Drivers::spleef);
        ALL.put("sumo", Drivers::sumo);
        ALL.put("two_booths", Drivers::splitSimon);
        ALL.put("block_party", Drivers::blockParty);
        ALL.put("crack_the_code", Drivers::mastermind);
        ALL.put("sliding_picture", Drivers::slide);
        ALL.put("pairs", Drivers::pairs);
        ALL.put("musical_chairs", Drivers::chairs);
        ALL.put("hot_potato", Drivers::hotPotato);
        ALL.put("king_of_the_hill", Drivers::kingHill);
        ALL.put("hide_and_seek", Drivers::hideSeek);
        ALL.put("whack_a_mole", Drivers::whack);
        ALL.put("tower", Drivers::hanoi);
        ALL.put("spot_the_difference", Drivers::spot);
        ALL.put("buried_treasure", Drivers::treasure);
        ALL.put("rising_floor", Drivers::lavaRising);
        ALL.put("hole_in_the_wall", Drivers::holeWall);
    }

    private Drivers() {
    }

    private static final int LONG = 20 * 400;

    /** The index of the bot that wins a versus room: the first one that is not the loser. */
    static int winner(Ctx c, int loser) {
        for (int i = 0; i < c.n(); i++) {
            if (i != loser) {
                return i;
            }
        }
        return 0;
    }

    private static ServerLevel level(Ctx c) {
        return c.arena().level();
    }

    // ---------------------------------------------------------------- chat rooms

    private static void typing(Ctx c, int loser) {
        Map<Integer, String> last = new HashMap<>();
        boolean[] said = new boolean[c.n()];
        c.driveUntilEnded(() -> {
            Map<UUID, String> lines = c.f("line");
            for (int i = 0; i < c.n(); i++) {
                if (c.decided(i)) {
                    continue;
                }
                String line = lines.get(c.id(i));
                if (i == loser) {
                    if (!said[i]) {
                        said[i] = true;
                        c.say(i, "completely wrong text");
                    }
                } else if (line != null && last.get(i) != line) {
                    last.put(i, line);
                    c.say(i, line);
                }
            }
        }, LONG);
    }

    private static void math(Ctx c, int loser) {
        Map<Integer, String> last = new HashMap<>();
        boolean[] said = new boolean[c.n()];
        c.driveUntilEnded(() -> {
            Map<UUID, Long> answer = c.f("answer");
            Map<UUID, Integer> done = c.f("done");
            for (int i = 0; i < c.n(); i++) {
                if (c.decided(i)) {
                    continue;
                }
                UUID id = c.id(i);
                Long a = answer.get(id);
                String key = a + "/" + done.get(id);
                if (i == loser) {
                    if (!said[i]) {
                        said[i] = true;
                        c.say(i, Long.toString(a + 1));
                    }
                } else if (!key.equals(last.get(i))) {
                    last.put(i, key);
                    c.say(i, Long.toString(a));
                }
            }
        }, LONG);
    }

    // ---------------------------------------------------------------- walking rooms

    private static void maze(Ctx c, int loser) {
        if (c.tier >= 5) {
            boolean dark = c.t.on(() -> c.p(0).hasEffect(MobEffects.DARKNESS));
            c.check("darkness effect from tier 5", dark, "");
        }
        if (loser >= 0 && c.n() == 1) {
            return;
        }
        c.driveUntilEnded(() -> {
            BlockPos exit = c.f("exit");
            for (int i = 0; i < c.n(); i++) {
                if (i != loser && !c.decided(i)) {
                    c.putAbs(i, exit.getX() + 0.5, exit.getY() + 1, exit.getZ() + 0.5);
                }
            }
        }, 400);
        if (c.tier >= 5 && loser < 0) {
            boolean gone = c.t.on(() -> !c.p(0).hasEffect(MobEffects.DARKNESS));
            c.check("darkness effect removed once cleared", gone, "");
        }
    }

    private static void parkour(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        int[] k = {0};
        int[] hold = {0};
        int[] fall = {0}; // 0 not yet, 1 settling on the first checkpoint, 2 fell, 3 done
        double[] expect = new double[3];
        boolean ok = c.driveUntilEnded(() -> {
            int[][] jumps = c.f("jumps");
            BlockPos finish = c.f("finish");
            BlockPos o = c.o();
            if (hold[0] > 0) {
                if (--hold[0] > 0) {
                    return;
                }
                if (fall[0] == 1) {
                    fall[0] = 2;
                    c.putAbs(0, expect[0], o.getY() - 30, expect[2]);
                    hold[0] = 6;
                } else if (fall[0] == 2) {
                    fall[0] = 3;
                    ServerPlayer p = c.p(0);
                    double d = p.position().distanceTo(new Vec3(expect[0], expect[1], expect[2]));
                    c.check("falling from the first checkpoint returns the bot to it", d < 1.5,
                        "stood " + p.position() + " wanted " + new Vec3(expect[0], expect[1], expect[2]));
                }
                return;
            }
            if (k[0] < jumps.length) {
                int[] j = jumps[k[0]];
                boolean check = k[0] > 0 && k[0] % 8 == 0;
                for (int i = 0; i < c.n(); i++) {
                    c.putAbs(i, o.getX() + j[0] + 0.5, o.getY() + j[1] + 1.0, o.getZ() + j[2] + 0.5);
                }
                k[0]++;
                if (check && fall[0] == 0) {
                    fall[0] = 1;
                    expect[0] = o.getX() + j[0] + 0.5;
                    expect[1] = o.getY() + j[1] + 1.0;
                    expect[2] = o.getZ() + j[2] + 0.5;
                    hold[0] = 3;
                }
            } else {
                for (int i = 0; i < c.n(); i++) {
                    c.putAbs(i, finish.getX() + 0.5, finish.getY() + 1, finish.getZ() + 0.5);
                }
            }
        }, 20 * 120);
        c.check("the whole course was walked", ok, ok ? "" : "room did not finish");
    }

    // ---------------------------------------------------------------- board puzzles

    private static void connect4(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        c.t.run(() -> c.use(0, 6, 1, 6));
        boolean answered = c.until(() -> {
            for (int x = 0; x < 7; x++) {
                for (int y = 2; y <= 7; y++) {
                    if (level(c).getBlockState(c.at(x, y, 6)).is(Blocks.CONCRETE.red())) {
                        return true;
                    }
                }
            }
            return false;
        }, 80);
        c.check("the Gamemaster answers a move with a red piece", answered, "");
        c.t.run(() -> {
            Object board = c.f("board");
            for (int k = 0; k < 3; k++) {
                Reflect.call(board, "drop", 0, 1); // three of ours stacked (the position is set up, the winning move is played for real)
            }
            c.use(0, 0, 1, 6);
        });
    }

    private static void memory(Ctx c, int loser) {
        boolean shown = c.until(() -> !(Boolean) c.f("showing"), 20 * 60);
        c.check("the sequence finishes showing", shown, "");
        if (loser >= 0) {
            c.t.run(() -> {
                List<ServerPlayer> order = c.f("order");
                List<Integer> seq = c.f("sequence");
                int pad = (seq.get(0) + 1) % 4;
                c.use(c.indexOf(order.get(0)), -2 + 2 * pad, 1, 4);
            });
            return;
        }
        int[] step = {0};
        c.driveUntilEnded(() -> {
            List<Integer> seq = c.f("sequence");
            List<ServerPlayer> order = c.f("order");
            int input = c.f("input");
            if (input >= seq.size()) {
                return;
            }
            int idx = c.indexOf(order.get(input % order.size()));
            int pad = seq.get(input);
            if (step[0]++ % 2 == 0) {
                c.use(idx, -2 + 2 * pad, 1, 4);
            } else {
                c.hit(idx, -2 + 2 * pad, 1, 4);
            }
        }, LONG);
    }

    private static List<int[]> lightsSolution(boolean[][] lit) {
        int n = lit.length;
        for (int mask = 0; mask < (1 << n); mask++) {
            boolean[][] g = new boolean[n][];
            for (int x = 0; x < n; x++) {
                g[x] = lit[x].clone();
            }
            List<int[]> presses = new ArrayList<>();
            for (int x = 0; x < n; x++) {
                if ((mask >> x & 1) != 0) {
                    flip(g, x, 0);
                    presses.add(new int[] {x, 0});
                }
            }
            for (int y = 0; y < n - 1; y++) {
                for (int x = 0; x < n; x++) {
                    if (g[x][y]) {
                        flip(g, x, y + 1);
                        presses.add(new int[] {x, y + 1});
                    }
                }
            }
            boolean clear = true;
            for (int x = 0; x < n; x++) {
                clear &= !g[x][n - 1];
            }
            if (clear) {
                return presses;
            }
        }
        throw new IllegalStateException("lights out board has no solution");
    }

    private static void flip(boolean[][] g, int x, int y) {
        int n = g.length;
        int[][] d = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int a = x + o[0];
            int b = y + o[1];
            if (a >= 0 && b >= 0 && a < n && b < n) {
                g[a][b] = !g[a][b];
            }
        }
    }

    private static void lightsOut(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        List<int[]> presses = c.t.on(() -> {
            Object board = c.f("board");
            int n = Reflect.get(board, "size");
            boolean[][] lit = new boolean[n][n];
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    lit[x][y] = Reflect.<Boolean>call(board, "lit", x, y);
                }
            }
            return lightsSolution(lit);
        });
        int n = c.t.on(() -> Reflect.<Integer>get(c.f("board"), "size"));
        int[] k = {0};
        boolean solved = c.drive(() -> c.ended() || (k[0] >= presses.size() && c.tickIs(1) && false), () -> {
            if (k[0] < presses.size()) {
                int[] pr = presses.get(k[0]);
                if (k[0] % 2 == 0) {
                    c.use(k[0] % c.n(), pr[0] - n / 2, 2 + pr[1], 5);
                } else {
                    c.hit(k[0] % c.n(), pr[0] - n / 2, 2 + pr[1], 5);
                }
                k[0]++;
            }
        }, 20 * 20);
        if (!solved) {
            boolean boardSolved = c.t.on(() -> Reflect.<Boolean>call(c.f("board"), "solved"));
            c.check("lights out: the solution (" + presses.size() + " presses, " + k[0] + " sent) puts every lamp out", boardSolved, "board solved: " + boardSolved);
        }
    }

    private static void pattern(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        int size = c.t.on(() -> Reflect.<Integer>get(c.room(), "size"));
        // painting is ignored while the picture still shows
        int[] before = c.t.on(() -> Reflect.<int[][]>get(c.room(), "canvas")[0].clone());
        c.t.run(() -> c.use(0, -size / 2, 2, 5));
        int[] after = c.t.on(() -> Reflect.<int[][]>get(c.room(), "canvas")[0].clone());
        c.check("no painting while the picture still shows", java.util.Arrays.equals(before, after), "");
        boolean hidden = c.until(() -> c.f("hidden"), 20 * 60);
        c.check("the picture hides after its time", hidden, "");
        int[] k = {0};
        c.driveUntilEnded(() -> {
            int[][] pat = c.f("pattern");
            int[][] canvas = c.f("canvas");
            int colours = c.f("colours");
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (pat[x][y] != canvas[x][y]) {
                        if (k[0]++ % 2 == 0) {
                            c.use(0, x - size / 2, 2 + y, 5);
                        } else {
                            c.hit(0, x - size / 2, 2 + y, 5);
                        }
                        return;
                    }
                }
            }
        }, 20 * 100);
    }

    private static void mastermind(Ctx c, int loser) {
        int[] secret = c.t.on(() -> c.<int[]>f("secret").clone());
        int pegs = secret.length;
        int colours = c.t.on(() -> Reflect.<Integer>call(c.room(), "colours"));
        int rows = c.t.on(() -> Reflect.<Integer>call(c.room(), "rows"));
        if (loser >= 0) {
            // rows of wrong guesses: press the check button until the rows run out
            int[] guessNow = c.t.on(() -> c.<int[]>f("guess").clone());
            if (java.util.Arrays.equals(guessNow, secret)) {
                c.t.run(() -> c.use(0, 0, 2, 6));
            }
            int[] n = {0};
            c.driveUntilEnded(() -> {
                if (n[0]++ < rows + 2) {
                    c.use(0, -2, 2, 6);
                }
            }, 20 * 60);
            return;
        }
        int[] presses = {0};
        int[] rowsUsed = {0};
        c.driveUntilEnded(() -> {
            int row = c.f("row");
            int[] guess = c.f("guess");
            for (int i = 0; i < pegs; i++) {
                if (guess[i] != secret[i]) {
                    c.use(0, i, 2 + row, 6);
                    return;
                }
            }
            c.use(0, -2, 2, 6);
        }, 20 * 60);
    }

    private static void slide(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        int n = c.t.on(() -> Reflect.<Integer>get(c.f("board"), "size"));
        int[][] start = c.t.on(() -> {
            int[][] tiles = Reflect.get(c.f("board"), "tiles");
            int[][] copy = new int[n][];
            for (int x = 0; x < n; x++) {
                copy[x] = tiles[x].clone();
            }
            return copy;
        });
        List<int[]> moves = SlideSolver.solve(start, n);
        c.check("slide puzzle solved offline (" + (moves == null ? -1 : moves.size()) + " moves)", moves != null, "");
        if (moves == null) {
            return;
        }
        int[] k = {0};
        c.driveUntilEnded(() -> {
            if (k[0] < moves.size()) {
                int[] m = moves.get(k[0]);
                if (k[0]++ % 2 == 0) {
                    c.use(0, m[0], 2 + (n - 1 - m[1]), 5);
                } else {
                    c.hit(0, m[0], 2 + (n - 1 - m[1]), 5);
                }
            }
        }, 20 * 200);
    }

    private static void pairs(Ctx c, int loser) {
        int columns = c.t.on(() -> Reflect.<Integer>get(c.room(), "columns"));
        int rows = c.t.on(() -> Reflect.<Integer>get(c.room(), "rows"));
        int[] faces = c.t.on(() -> c.<int[]>f("faces").clone());
        int cells = faces.length;
        if (loser >= 0) {
            // two cells that do not match: they flip over, then turn back after a moment
            int a = 0;
            int b = -1;
            for (int i = 1; i < cells; i++) {
                if (faces[i] != faces[0]) {
                    b = i;
                    break;
                }
            }
            int fa = a;
            int fb = b;
            c.t.run(() -> {
                c.use(0, fa % columns, 2 + rows - 1 - fa / columns, 5);
                c.use(0, fb % columns, 2 + rows - 1 - fb / columns, 5);
            });
            boolean up = c.t.on(() -> !level(c).getBlockState(c.at(fb % columns, 2 + rows - 1 - fb / columns, 5)).is(Blocks.GLAZED_TERRACOTTA.black()));
            c.check("a turned tile shows its face", up, "");
            boolean down = c.until(() -> level(c).getBlockState(c.at(fb % columns, 2 + rows - 1 - fb / columns, 5)).is(Blocks.GLAZED_TERRACOTTA.black()), 80);
            c.check("a mismatched tile turns back face down", down, down ? "" : c.t.on(() -> "first " + c.<Integer>f("first") + ", second " + c.<Integer>f("second")
                + ", hideAt " + c.<Long>f("hideAt") + ", room clock " + c.<Long>f("now") + ", arena decorating " + c.live.arena.decorating()));
            return;
        }
        List<int[]> pairs = new ArrayList<>();
        Map<Integer, Integer> first = new HashMap<>();
        for (int i = 0; i < cells; i++) {
            Integer f = first.remove(faces[i]);
            if (f == null) {
                first.put(faces[i], i);
            } else {
                pairs.add(new int[] {f, i});
            }
        }
        int[] k = {0};
        int[] half = {0};
        c.driveUntilEnded(() -> {
            if (k[0] >= pairs.size()) {
                return;
            }
            int[] pr = pairs.get(k[0]);
            int cell = pr[half[0]];
            if (half[0] == 0) {
                c.use(0, cell % columns, 2 + rows - 1 - cell / columns, 5);
                half[0] = 1;
            } else {
                c.hit(0, cell % columns, 2 + rows - 1 - cell / columns, 5);
                half[0] = 0;
                k[0]++;
            }
        }, 20 * 200);
    }

    private static void whack(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        int size = c.t.on(() -> Reflect.<Integer>get(c.room(), "size"));
        c.driveUntilEnded(() -> {
            long[] down = c.f("downAt");
            for (int i = 0; i < down.length; i++) {
                if (down[i] > 0) {
                    c.hit(0, (i % size) * 2, 1, (i / size) * 2 + 1);
                    return;
                }
            }
        }, 20 * 300);
    }

    private static void hanoi(Ctx c, int loser) {
        int rings = c.t.on(() -> Reflect.<Integer>get(c.room(), "rings"));
        int[] postX = new int[3];
        for (int p = 0; p < 3; p++) {
            postX[p] = (1 - p) * (2 * rings + 4);
        }
        if (loser >= 0) {
            // an illegal move first: lift the small ring to the middle post, then try the next ring onto it
            c.t.run(() -> {
                c.use(0, postX[0], 0, 2);
                c.use(0, postX[1], 0, 2);
                c.use(0, postX[0], 0, 2);
                c.use(0, postX[1], 0, 2);
            });
            @SuppressWarnings("unchecked")
            int middle = c.t.on(() -> ((java.util.Deque<Integer>) c.<Object[]>f("posts")[1]).size());
            c.check("a big ring cannot go on a small one", middle == 1, "middle post holds " + middle + " ring(s)");
            return;
        }
        List<int[]> moves = new ArrayList<>();
        hanoiMoves(rings, 0, 2, 1, moves);
        int[] k = {0};
        int[] half = {0};
        c.driveUntilEnded(() -> {
            if (k[0] >= moves.size()) {
                return;
            }
            int[] m = moves.get(k[0]);
            if (half[0] == 0) {
                c.use(0, postX[m[0]], 0, 2);
                half[0] = 1;
            } else {
                c.hit(0, postX[m[1]], 0, 2);
                half[0] = 0;
                k[0]++;
            }
        }, 20 * 300);
    }

    private static void hanoiMoves(int n, int from, int to, int via, List<int[]> out) {
        if (n == 0) {
            return;
        }
        hanoiMoves(n - 1, from, via, to, out);
        out.add(new int[] {from, to});
        hanoiMoves(n - 1, via, to, from, out);
    }

    private static void spot(Ctx c, int loser) {
        int size = c.t.on(() -> Reflect.<Integer>get(c.room(), "size"));
        Set<Integer> diffs = c.t.on(() -> new HashSet<>(c.<Set<Integer>>f("differences")));
        if (loser >= 0) {
            int wrong = 0;
            List<int[]> picks = new ArrayList<>();
            for (int x = 0; x < size && picks.size() < 3; x++) {
                for (int y = 0; y < size && picks.size() < 3; y++) {
                    if (!diffs.contains(x * 100 + y)) {
                        picks.add(new int[] {x, y});
                    }
                }
            }
            for (int[] pk : picks) {
                c.t.run(() -> c.use(0, -size - 1 + pk[0], 2 + pk[1], 6));
                c.ticks(2);
            }
            return;
        }
        List<Integer> todo = new ArrayList<>(diffs);
        int[] k = {0};
        c.driveUntilEnded(() -> {
            if (k[0] < todo.size()) {
                int d = todo.get(k[0]++);
                c.use(0, -size - 1 + d / 100, 2 + d % 100, 6);
            }
        }, 20 * 60);
    }

    private static void treasure(Ctx c, int loser) {
        List<int[]> chests = c.t.on(() -> new ArrayList<>(c.<List<int[]>>f("treasures")));
        if (loser >= 0) {
            List<int[]> cells = new ArrayList<>();
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    boolean chest = false;
                    for (int[] t : chests) {
                        chest |= Math.abs(t[0] - x) + Math.abs(t[1] - z) <= 1;
                    }
                    if (!chest) {
                        cells.add(new int[] {x, z});
                    }
                }
            }
            Collections.shuffle(cells, new java.util.Random(7));
            int[] k = {0};
            boolean[] looked = {false};
            c.driveUntilEnded(() -> {
                if (k[0] < cells.size()) {
                    int[] cell = cells.get(k[0]++);
                    c.hit(0, cell[0], 0, cell[1]);
                    if (!looked[0]) {
                        looked[0] = true;
                        boolean wool = level(c).getBlockState(c.at(cell[0], -1, cell[1])).getBlock().getDescriptionId().contains("wool");
                        c.check("a dug hole shows a heat colour", wool, level(c).getBlockState(c.at(cell[0], -1, cell[1])).toString());
                    }
                }
            }, 20 * 60);
            return;
        }
        int[] k = {0};
        c.driveUntilEnded(() -> {
            if (k[0] < chests.size()) {
                int[] t = chests.get(k[0]++);
                c.hit(0, t[0], 0, t[1]);
            }
        }, 20 * 60);
    }

    // ---------------------------------------------------------------- skill / timing rooms

    private static void archery(Ctx c, int loser) {
        if (loser >= 0) {
            return;
        }
        boolean armed = c.t.on(() -> c.p(0).getInventory().contains(s -> s.is(Items.BOW)) && c.p(0).getInventory().contains(s -> s.is(Items.ARROW)));
        c.check("the bots are given a bow and arrows", armed, "");
        int[] shot = {-1};
        c.driveUntilEnded(() -> {
            List<BlockPos> targets = c.f("targets");
            int current = c.f("current");
            if (current < targets.size() && shot[0] != current) {
                shot[0] = current;
                BlockPos t = c.at(targets.get(current).getX(), targets.get(current).getY(), targets.get(current).getZ());
                Arrow arrow = new Arrow(level(c), t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, new ItemStack(Items.ARROW), null);
                arrow.setOwner(c.p(0));
                level(c).addFreshEntity(arrow);
            }
        }, 20 * 120);
    }

    private static void dropper(Ctx c, int loser) {
        // a miss: standing on the floor outside the pool sends the bot back to the top
        BlockPos[] dry = {null};
        BlockPos[] wet = {null};
        c.t.run(() -> {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    boolean water = level(c).getFluidState(c.at(x, 0, z)).isSource();
                    if (water && wet[0] == null) {
                        wet[0] = new BlockPos(x, 0, z);
                    }
                    if (!water && dry[0] == null) {
                        dry[0] = new BlockPos(x, 0, z);
                    }
                }
            }
        });
        c.t.run(() -> c.put(0, dry[0].getX() + 0.5, 1, dry[0].getZ() + 0.5));
        boolean back = c.until(() -> c.p(0).getY() > c.o().getY() + 50, 40);
        c.check("a miss sends the bot back to the top of the shaft", back, "");
        if (loser >= 0) {
            return;
        }
        c.driveUntilEnded(() -> {
            for (int i = 0; i < c.n(); i++) {
                if (!c.decided(i)) {
                    c.put(i, wet[0].getX() + 0.5, 0.2, wet[0].getZ() + 0.5);
                }
            }
        }, 200);
    }

    private static void fallingFloor(Ctx c, int loser) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                cells.add(new int[] {x, z});
            }
        }
        Collections.shuffle(cells, new java.util.Random(11));
        int[] next = {0};
        if (loser >= 0) {
            c.t.run(() -> c.put(loser, 20, -25, 20)); // off the edge
        }
        Runnable step = () -> {
            if (!c.tickIs(6)) {
                return;
            }
            Map<Long, Long> dropping = c.f("dropping");
            for (int i = 0; i < c.n(); i++) {
                if (i == loser || c.decided(i)) {
                    continue;
                }
                for (int tries = 0; tries < cells.size(); tries++) {
                    int[] cell = cells.get(next[0]++ % cells.size());
                    long key = ((long) (cell[0] + 512) << 32) | ((long) (0 + 512) << 16) | (cell[1] + 512);
                    if (!dropping.containsKey(key)) {
                        c.put(i, cell[0] + 0.5, 1, cell[1] + 0.5);
                        break;
                    }
                }
            }
        };
        if (loser >= 0 && c.n() > 2) {
            c.drive(() -> c.decided(loser) || c.ended(), step, 20 * 30);
            if (!c.ended()) {
                spectatorClamp(c, loser);
            }
        }
        c.driveUntilEnded(step, 20 * 100);
    }

    /** A bot that is out watches as a spectator: it may not leave the room's box. */
    static void spectatorClamp(Ctx c, int i) {
        int[] b = c.room().bounds();
        c.t.run(() -> {
            if (c.p(i).isSpectator()) {
                c.putAbs(i, c.o().getX() + b[3] + 80, c.o().getY() + 10, c.o().getZ());
            }
        });
        c.ticks(4);
        double x = c.t.on(() -> c.p(i).getX());
        boolean spectator = c.t.on(() -> c.p(i).isSpectator());
        if (spectator) {
            c.check("a spectator is held inside the room's box", x <= c.o().getX() + b[3] + 0.5, "x " + x + " limit " + (c.o().getX() + b[3]));
        }
    }

    private static void redLight(Ctx c, int loser) {
        if (loser >= 0) {
            // caught moving while the light is red: sent back to the start
            boolean red = c.until(() -> {
                boolean green = c.f("green");
                long redSince = c.f("redSince");
                long now = c.t.tick() - c.live.startedAt();
                return !green && now - redSince >= 14 && now > 70;
            }, 20 * 30);
            c.check("a red light comes on", red, "");
            if (red) {
                c.t.run(() -> {
                    ServerPlayer p = c.p(0);
                    c.putAbs(0, p.getX(), p.getY(), p.getZ() + 2.0);
                });
                c.ticks(4);
                double z = c.t.on(() -> c.p(0).getZ() - c.o().getZ());
                c.check("moving on red sends the bot back to the start", z < 1.5, "z offset " + z);
            }
            return;
        }
        c.driveUntilEnded(() -> {
            boolean green = c.f("green");
            if (green) {
                for (int i = 0; i < c.n(); i++) {
                    if (!c.decided(i)) {
                        c.put(i, -5 + (i % 6) * 2 + 0.5, 1, 48.5);
                    }
                }
            }
        }, 20 * 80);
    }

    private static void waves(Ctx c, int loser) {
        boolean[] killed = {false};
        if (loser >= 0) {
            c.t.run(() -> {
                ServerPlayer p = c.p(loser);
                p.hurtServer(level(c), level(c).damageSources().generic(), 1000F);
            });
            boolean spectator = c.until(() -> c.p(loser).isSpectator() || c.decided(loser), 20);
            c.check("a deadly hit in a room is a lost room, not a death", spectator && c.t.on(() -> c.p(loser).getHealth() > 0), "");
            if (c.n() == 1) {
                return;
            }
        }
        boolean[] mobsSeen = {false};
        c.driveUntilEnded(() -> {
            List<Mob> alive = c.f("alive");
            for (Mob m : new ArrayList<>(alive)) {
                mobsSeen[0] = true;
                if (m.isAlive() && m.tickCount > 15) { // seen for three quarters of a second, then dispatched
                    m.kill(level(c));
                }
            }
        }, 20 * 120);
        c.check("monsters came in waves", mobsSeen[0], "");
    }

    private static void blockParty(Ctx c, int loser) {
        c.driveUntilEnded(() -> {
            int called = c.f("called");
            boolean dropped = c.f("dropped");
            if (called < 0 || dropped) {
                return;
            }
            int[][] floor = c.f("floor");
            int hx = -1;
            int hz = -1;
            int bx = -1;
            int bz = -1;
            for (int x = 0; x < floor.length && (hx < 0 || bx < 0); x++) {
                for (int z = 0; z < floor.length; z++) {
                    if (floor[x][z] == called && hx < 0) {
                        hx = x;
                        hz = z;
                    }
                    if (floor[x][z] != called && bx < 0) {
                        bx = x;
                        bz = z;
                    }
                }
            }
            for (int i = 0; i < c.n(); i++) {
                if (c.decided(i)) {
                    continue;
                }
                if (i == loser && bx >= 0) {
                    c.put(i, bx - 8 + 0.5, 1, bz - 8 + 0.5);
                } else if (hx >= 0) {
                    c.put(i, hx - 8 + 0.5, 1, hz - 8 + 0.5);
                }
            }
        }, 20 * 120);
    }

    private static void lavaRising(Ctx c, int loser) {
        List<int[]> ledges = c.t.on(() -> new ArrayList<>(c.<List<int[]>>f("ledges")));
        int top = c.t.on(() -> c.<Integer>f("top"));
        if (loser >= 0) {
            c.driveUntilEnded(() -> {
                for (int i = 0; i < c.n(); i++) {
                    if (i != loser && !c.decided(i)) {
                        int[] last = ledges.get(ledges.size() - 1);
                        c.putAbs(i, c.o().getX() + last[0] + 0.5, c.o().getY() + top + 1, c.o().getZ() + last[2] + 0.5);
                    }
                }
            }, 20 * 120);
            int lava = c.t.on(() -> c.<Integer>f("lava"));
            c.check("the magma rose while the bot waited", lava >= 1, "lava level " + lava);
            return;
        }
        // The stage is now built before the players arrive (no more decoration going up round them), so the magma is the only
        // thing that animates here: stand on a ledge up the shaft until it has risen once, then climb.
        int safeIndex = Math.min(6, ledges.size() - 1);
        int[] safe = ledges.get(safeIndex);
        c.t.run(() -> c.putAbs(0, c.o().getX() + safe[0] + 0.5, c.o().getY() + safe[1] + 1, c.o().getZ() + safe[2] + 0.5));
        c.t.until(() -> c.t.on(() -> ((Number) c.<Object>f("lava")).intValue() >= 1), 20 * 90);
        int[] k = {safeIndex};
        double[] seen = new double[5];
        String[] below = {"?"};
        boolean[] landed = {false};
        c.driveUntilEnded(() -> {
            ServerPlayer p0 = c.p(0);
            if (p0 != null) {
                seen[0] = p0.getX() - c.o().getX();
                seen[1] = p0.getY() - c.o().getY();
                seen[2] = p0.getZ() - c.o().getZ();
                seen[3] = ((Number) c.<Object>f("lava")).doubleValue();
                seen[4] = p0.onGround() ? 1 : 0;
                below[0] = String.valueOf(level(c).getBlockState(p0.blockPosition().below()).getBlock().getDescriptionId());
            }
            if (k[0] < ledges.size()) {
                int[] l = ledges.get(k[0]++);
                for (int i = 0; i < c.n(); i++) {
                    c.putAbs(i, c.o().getX() + l[0] + 0.5, c.o().getY() + l[1] + 1.0, c.o().getZ() + l[2] + 0.5);
                }
            } else if (!landed[0]) {
                landed[0] = true; // one landing, then the bot stays put and settles on the gold
                // the gold landing is a 3x3 one gap along the spiral from the last ledge: find its centre at the top
                int[] gold = null;
                for (int gx = -10; gx <= 10 && gold == null; gx++) {
                    for (int gz = -10; gz <= 10; gz++) {
                        if (level(c).getBlockState(c.at(gx, top, gz)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                                && level(c).getBlockState(c.at(gx + 1, top, gz + 1)).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)) {
                            gold = new int[] {gx + 1, gz + 1};
                            break;
                        }
                    }
                }
                int[] spot = gold != null ? gold : new int[] {ledges.get(ledges.size() - 1)[0], ledges.get(ledges.size() - 1)[2]};
                for (int i = 0; i < c.n(); i++) {
                    if (!c.decided(i)) {
                        c.putAbs(i, c.o().getX() + spot[0] + 0.5, c.o().getY() + top + 1.0, c.o().getZ() + spot[1] + 0.5);
                    }
                }
            }
        }, 20 * 120);
        if (!c.t.on(() -> c.live.cleared().contains(c.id(0)))) {
            c.check("lava: the climb reached the landing", false, String.format("last seen at (%.1f, %.1f, %.1f) relative, lava level %.0f, on ground %.0f, top %d, %d ledges, block below %s",
                seen[0], seen[1], seen[2], seen[3], seen[4], top, ledges.size(), below[0]));
        }
    }

    private static void holeWall(Ctx c, int loser) {
        c.driveUntilEnded(() -> {
            Set<Integer> holes = c.f("holes");
            if (holes.isEmpty()) {
                return;
            }
            int hole = Collections.min(holes);
            int safe = hole;
            int bad = holes.contains(-4) ? 4 : -4;
            for (int i = 0; i < c.n(); i++) {
                if (c.decided(i)) {
                    continue;
                }
                c.put(i, (i == loser ? bad : safe) + 0.5, 1, 0.5);
            }
        }, 20 * 200);
    }

    // ---------------------------------------------------------------- versus rooms (2-4 bots)

    private static void spleef(Ctx c, int loser) {
        int w = winner(c, loser);
        boolean[] clamped = {false};
        for (int j = 0; j < c.n(); j++) {
            if (j == w) {
                continue;
            }
            int victim = j;
            int from = c.t.tick();
            boolean out = c.drive(() -> c.decided(victim) || c.ended(), () -> {
                if (!c.tickIs(10)) {
                    return;
                }
                ServerPlayer pv = c.p(victim);
                if (pv.getY() < c.o().getY() + 0.5) {
                    return; // already falling: moving the winner down to the victim's height would drop it below the out line too
                }
                c.putAbs(w, pv.getX() + 2.0, pv.getY(), pv.getZ());
                // the blocks the victim's feet touch (a bot standing on a block edge rests on two or four of them)
                var box = pv.getBoundingBox();
                Set<BlockPos> under = new HashSet<>();
                int y = (int) Math.floor(pv.getY()) - 1;
                for (double x : new double[] {box.minX + 0.01, box.maxX - 0.01}) {
                    for (double z : new double[] {box.minZ + 0.01, box.maxZ - 0.01}) {
                        under.add(new BlockPos((int) Math.floor(x), y, (int) Math.floor(z)));
                    }
                }
                for (BlockPos pos : under) {
                    In.hit(c.p(w), pos);
                }
            }, 20 * 30);
            c.check("bot " + victim + " is knocked out by breaking the snow under it (" + (c.t.tick() - from) + " ticks)", out,
                c.t.on(() -> "victim at " + c.p(victim).position() + " mode " + c.p(victim).gameMode() + "; block below " + level(c).getBlockState(c.p(victim).blockPosition().below())));
            if (!clamped[0] && c.n() > 2 && !c.ended()) {
                clamped[0] = true;
                spectatorClamp(c, victim);
            }
        }
        c.driveUntilEnded(() -> { }, 40);
    }

    private static void sumo(Ctx c, int loser) {
        int w = winner(c, loser);
        int v = w == 0 ? 1 : 0;
        int radius = c.t.on(() -> c.<Integer>f("radius"));
        int[] attacks = {0};
        boolean ended = c.drive(c::ended, () -> {
            if (!c.tickIs(12)) {
                return;
            }
            if (attacks[0] % 6 == 0) {
                c.putAbs(v, c.o().getX() + radius - 1.2, c.o().getY() + 1, c.o().getZ() + 0.5);
                c.p(v).setYRot(0);
                c.putAbs(w, c.o().getX() + radius - 2.8, c.o().getY() + 1, c.o().getZ() + 0.5);
                c.p(w).setYRot(-90F);
            }
            c.p(w).attack(c.p(v));
            attacks[0]++;
        }, 20 * 30);
        c.check("the stick's knockback pushes the other bot off the platform", ended, ended ? attacks[0] + " attacks" : "still on after " + attacks[0] + " attacks");
        if (!ended) {
            c.t.run(() -> c.putAbs(v, c.o().getX() + 40, c.o().getY() - 10, c.o().getZ()));
            c.driveUntilEnded(() -> { }, 60);
        }
    }

    private static void splitSimon(Ctx c, int loser) {
        int watcher = c.t.on(() -> c.indexOf((ServerPlayer) c.f("watcher")));
        int presser = c.t.on(() -> c.indexOf((ServerPlayer) c.f("presser")));
        boolean shown = c.until(() -> {
            int s = c.f("shown");
            List<Integer> seq = c.f("sequence");
            return s >= seq.size();
        }, 20 * 60);
        c.check("the watcher's lamps run through the whole sequence", shown, "");
        if (loser >= 0) {
            c.t.run(() -> {
                List<Integer> seq = c.f("sequence");
                int pad = (seq.get(0) + 1) % 4;
                c.use(presser, 4 + pad + (pad >= 2 ? 1 : 0), 1, 3);
            });
            return;
        }
        int[] step = {0};
        c.driveUntilEnded(() -> {
            List<Integer> seq = c.f("sequence");
            int input = c.f("input");
            if (input < seq.size()) {
                int pad = seq.get(input);
                int x = 4 + pad + (pad >= 2 ? 1 : 0);
                if (step[0]++ % 2 == 0) {
                    c.use(presser, x, 1, 3);
                } else {
                    c.hit(presser, x, 1, 3);
                }
            }
        }, LONG);
    }

    private static void kingHill(Ctx c, int loser) {
        int w = winner(c, loser);
        c.driveUntilEnded(() -> {
            if (c.tickIs(10)) {
                c.put(w, 0.5, 8, 0.5);
            }
        }, 20 * 120);
    }

    private static void chairs(Ctx c, int loser) {
        int w = winner(c, loser);
        Runnable step = () -> {
            List<int[]> seats = c.f("seats");
            boolean music = c.f("music");
            if (music || seats.isEmpty()) {
                return;
            }
            // SIT: the winner (or everyone but the loser) takes a seat; the loser stands clear of every seat
            int s = 0;
            for (int i = 0; i < c.n(); i++) {
                if (c.decided(i)) {
                    continue;
                }
                boolean sits = loser < 0 ? i == w : i != loser;
                if (sits && s < seats.size()) {
                    c.put(i, seats.get(s)[0] + 0.5, 1, seats.get(s)[1] + 0.5);
                    s++;
                } else if (i == loser) {
                    int[] spot = clearSpot(seats);
                    c.put(i, spot[0] + 0.5, 1, spot[1] + 0.5);
                }
            }
        };
        if (c.n() > 2) {
            c.drive(() -> c.ended() || anySpectator(c), step, 20 * 100);
            if (!c.ended()) {
                int out = c.t.on(() -> {
                    for (int i = 0; i < c.n(); i++) {
                        if (c.p(i) != null && c.p(i).isSpectator()) {
                            return i;
                        }
                    }
                    return -1;
                });
                if (out >= 0) {
                    spectatorClamp(c, out);
                }
            }
        }
        c.driveUntilEnded(step, 20 * 150);
    }

    private static boolean anySpectator(Ctx c) {
        for (int i = 0; i < c.n(); i++) {
            if (c.p(i) != null && c.p(i).isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private static int[] clearSpot(List<int[]> seats) {
        for (int x = -6; x <= 6; x++) {
            boolean free = true;
            for (int[] s : seats) {
                free &= Math.abs(s[0] - x) + Math.abs(s[1] - 6) >= 2;
            }
            if (free) {
                return new int[] {x, 6};
            }
        }
        return new int[] {0, 7};
    }

    private static void hotPotato(Ctx c, int loser) {
        int w = winner(c, loser);
        int protectedBot = loser < 0 ? 0 : w;
        boolean[] passed = {false};
        c.driveUntilEnded(() -> {
            ServerPlayer holder = c.f("holder");
            if (holder == null || !c.tickIs(5)) {
                return;
            }
            int h = c.indexOf(holder);
            if (h < 0 || c.decided(h)) {
                return;
            }
            long now = c.f("now");
            long passedAt = c.f("passedAt");
            if (now - passedAt <= 12) {
                return;
            }
            int target = -1;
            if (h == protectedBot) {
                // never keep the potato: hit the doomed bot, or anyone else still playing
                if (loser >= 0 && !c.decided(loser)) {
                    target = loser;
                } else {
                    for (int i = 0; i < c.n() && target < 0; i++) {
                        if (i != h && !c.decided(i)) {
                            target = i;
                        }
                    }
                }
            } else if (loser >= 0 && h != loser && !c.decided(loser)) {
                target = loser; // the loser is handed the potato and keeps it
            }
            if (target >= 0) {
                ServerPlayer victim = c.p(target);
                c.putAbs(h, victim.getX() - 1.5, victim.getY(), victim.getZ());
                c.p(h).attack(victim);
                passed[0] = true;
            }
        }, 20 * 170);
    }

    private static void hideSeek(Ctx c, int loser) {
        int seeker = c.t.on(() -> c.indexOf((ServerPlayer) c.f("seeker")));
        long hideEnds = c.t.on(() -> c.<Long>f("hideEnds"));
        // bot 0 seeks: it tags everyone (win) or does nothing (lose). Bot 0 hides: it waits out the clock (win) or is tagged (lose).
        if (seeker == 0 && loser < 0) {
            c.until(() -> c.t.tick() - c.live.startedAt() > hideEnds + 2, 20 * 60);
            c.driveUntilEnded(() -> {
                if (!c.tickIs(6)) {
                    return;
                }
                for (int i = 0; i < c.n(); i++) {
                    if (i != seeker && !c.decided(i)) {
                        ServerPlayer v = c.p(i);
                        c.putAbs(seeker, v.getX() + 1.0, v.getY(), v.getZ());
                        c.p(seeker).attack(v);
                        return;
                    }
                }
            }, 20 * 60);
        } else if (seeker != 0 && loser == 0) {
            c.until(() -> c.t.tick() - c.live.startedAt() > hideEnds + 2, 20 * 60);
            c.driveUntilEnded(() -> {
                if (c.tickIs(6) && !c.decided(0)) {
                    ServerPlayer v = c.p(0);
                    c.putAbs(seeker, v.getX() + 1.0, v.getY(), v.getZ());
                    c.p(seeker).attack(v);
                }
            }, 20 * 60);
        } else {
            // the bell: hiders still free clear, a seeker who found nobody fails
            c.until(() -> c.t.tick() - c.live.startedAt() > hideEnds + 5, 20 * 60);
            c.expire();
        }
    }

    // ---------------------------------------------------------------- slide solver

    /** Weighted A* for the sliding puzzle; returns the tiles to click (x, y) in order. */
    static final class SlideSolver {
        static List<int[]> solve(int[][] tiles, int n) {
            // state as a flat array row-major in (y, x): tiles[x][y]
            int[] start = new int[n * n];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    start[y * n + x] = tiles[x][y];
                }
            }
            int[] goal = new int[n * n];
            for (int i = 0; i < n * n - 1; i++) {
                goal[i] = i + 1;
            }
            record Node(int[] s, int g, int h, Node parent, int moved) {
            }
            java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>((a, b) -> Integer.compare(a.g + 3 * a.h, b.g + 3 * b.h));
            Map<String, Integer> seen = new HashMap<>();
            open.add(new Node(start, 0, manhattan(start, n), null, -1));
            seen.put(java.util.Arrays.toString(start), 0);
            long limit = System.nanoTime() + 20_000_000_000L;
            while (!open.isEmpty() && System.nanoTime() < limit) {
                Node cur = open.poll();
                if (cur.h == 0) {
                    List<int[]> moves = new ArrayList<>();
                    for (Node k = cur; k.parent != null; k = k.parent) {
                        moves.add(0, new int[] {k.moved % n, k.moved / n});
                    }
                    return moves;
                }
                int gap = 0;
                for (int i = 0; i < cur.s.length; i++) {
                    if (cur.s[i] == 0) {
                        gap = i;
                    }
                }
                int gx = gap % n;
                int gy = gap / n;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : dirs) {
                    int nx = gx + d[0];
                    int ny = gy + d[1];
                    if (nx < 0 || ny < 0 || nx >= n || ny >= n) {
                        continue;
                    }
                    int[] next = cur.s.clone();
                    next[gap] = next[ny * n + nx];
                    next[ny * n + nx] = 0;
                    String key = java.util.Arrays.toString(next);
                    Integer best = seen.get(key);
                    if (best != null && best <= cur.g + 1) {
                        continue;
                    }
                    seen.put(key, cur.g + 1);
                    open.add(new Node(next, cur.g + 1, manhattan(next, n), cur, ny * n + nx));
                }
            }
            return null;
        }

        private static int manhattan(int[] s, int n) {
            int total = 0;
            for (int i = 0; i < s.length; i++) {
                if (s[i] != 0) {
                    int want = s[i] - 1;
                    total += Math.abs(want % n - i % n) + Math.abs(want / n - i / n);
                }
            }
            return total;
        }
    }
}
