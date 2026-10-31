package com.silver.arcade.qa;

import com.silver.qaclient.*;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One strategy per room: each reads the board from the client world (and chat) and acts only through the Bot's
 * look / click / walk / chat. Nothing here touches the server.
 */
public final class Solvers {
    private final Bot b;
    int clicks, noEffect, unreachable;
    long deadline = Long.MAX_VALUE;
    int roomHud;
    int mark;
    double maxReach;

    public Solvers(Bot b) {
        this.b = b;
    }

    void reset() {
        clicks = 0;
        noEffect = 0;
        unreachable = 0;
        maxReach = 0;
    }

    static void problem(String what) {
        Rec.event("problem", "text", what);
    }

    /** Aims at the block and right-clicks it. Returns false when the crosshair could not be put on it. */
    boolean use(BlockPos p, String what) {
        if (Rec.tick > deadline) {
            throw new IllegalStateException("driver time cap for this room reached");
        }
        if (Math.abs(b.pos().x) < 120) {
            throw new IllegalStateException("the room is over (back in the hall)");
        }
        double d = b.reachTo(p), lim = b.reachLimit();
        maxReach = Math.max(maxReach, d);
        if (!b.aimAtBlock(p)) {
            Bot.Aim a = b.crosshair();
            unreachable++;
            Rec.event("unaimable", "target", what + " " + p.toShortString(), "reach", round(d), "limit", round(lim), "crosshair", a.hit() + " " + a.pos());
            if (d > lim) {
                // A person would step closer.
                Vec3 e = b.pos();
                if (b.walkTo(p.getX() + 0.5, p.getZ() + 0.5 - Math.signum(p.getZ() + 0.5 - e.z) * 1.5, 1.0, 100)) {
                    if (b.aimAtBlock(p)) {
                        problem("needed to walk closer to reach " + what + " (was " + round(d) + " blocks away, reach " + round(lim) + ")");
                        b.rightClick();
                        clicks++;
                        return true;
                    }
                }
            }
            return false;
        }
        b.rightClick();
        clicks++;
        return true;
    }

    static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Waits for the block at p to stop being {@code old}. */
    boolean changed(BlockPos p, String old, int ticks) {
        boolean ok = b.until(() -> !W.id(b.mc.level.getBlockState(p)).equals(old), ticks);
        if (!ok) {
            noEffect++;
            Rec.event("no_effect", "pos", p.toShortString(), "still", old);
        }
        return ok;
    }

    boolean won(int since) {
        return Hud.seen(since, "cleared") || Hud.seen(since, "perfect") || Hud.seen(since, "well done") || Hud.seen(since, "cracked")
            || Hud.seen(since, "picture perfect") || Hud.seen(since, "found") && Hud.seen(since, "buried_treasure");
    }

    // ---------------------------------------------------------------- Lights Out

    void lightsOut() {
        List<BlockPos> lamps = W.scan(b, 14, 10, 14, s -> s.equals("shroomlight") || s.equals("black_concrete"));
        if (lamps.isEmpty()) {
            problem("lights out: no lamps visible");
            return;
        }
        int x0 = lamps.stream().mapToInt(BlockPos::getX).min().getAsInt(), x1 = lamps.stream().mapToInt(BlockPos::getX).max().getAsInt();
        int y0 = lamps.stream().mapToInt(BlockPos::getY).min().getAsInt(), y1 = lamps.stream().mapToInt(BlockPos::getY).max().getAsInt();
        int z = lamps.get(0).getZ();
        int n = x1 - x0 + 1;
        Rec.event("board", "kind", "lights_out", "size", n, "x0", x0, "y0", y0, "z", z);
        boolean[][] lit = new boolean[n][n];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                lit[x][y] = W.idAt(b, new BlockPos(x0 + x, y0 + y, z)).equals("shroomlight");
            }
        }
        // Gaussian elimination over GF(2): which lamps to press so that every lamp ends dark.
        int N = n * n;
        int[][] a = new int[N][N + 1];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                int row = x * n + y;
                int[][] d = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] dd : d) {
                    int nx = x + dd[0], ny = y + dd[1];
                    if (nx >= 0 && ny >= 0 && nx < n && ny < n) {
                        a[row][nx * n + ny] ^= 1;
                    }
                }
                a[row][N] = lit[x][y] ? 1 : 0;
            }
        }
        int[] pivotCol = new int[N];
        Arrays.fill(pivotCol, -1);
        int r = 0;
        for (int c = 0; c < N && r < N; c++) {
            int p = -1;
            for (int i = r; i < N; i++) {
                if (a[i][c] == 1) {
                    p = i;
                    break;
                }
            }
            if (p < 0) {
                continue;
            }
            int[] t = a[p];
            a[p] = a[r];
            a[r] = t;
            for (int i = 0; i < N; i++) {
                if (i != r && a[i][c] == 1) {
                    for (int k = 0; k <= N; k++) {
                        a[i][k] ^= a[r][k];
                    }
                }
            }
            pivotCol[r] = c;
            r++;
        }
        int[] press = new int[N];
        for (int i = 0; i < r; i++) {
            press[pivotCol[i]] = a[i][N];
        }
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (press[x * n + y] == 1) {
                    BlockPos p = new BlockPos(x0 + x, y0 + y, z);
                    String before = W.idAt(b, p);
                    if (use(p, "lamp " + x + "," + y)) {
                        changed(p, before, 20);
                        StringBuilder sb = new StringBuilder();
                        for (int yy = n - 1; yy >= 0; yy--) {
                            for (int xx = 0; xx < n; xx++) {
                                sb.append(W.idAt(b, new BlockPos(x0 + xx, y0 + yy, z)).equals("shroomlight") ? '#' : '.');
                            }
                            sb.append('/');
                        }
                        Rec.event("lights_after_press", "x", x, "y", y, "board", sb.toString(), "hit", b.crosshair().hit() + " " + b.crosshair().pos());
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Mastermind

    void mastermind() {
        List<BlockPos> bulbs = W.scan(b, 16, 10, 16, id -> id.equals("waxed_copper_bulb"));
        if (bulbs.isEmpty()) {
            problem("crack the code: no check button (waxed copper bulb) visible");
            return;
        }
        BlockPos button = bulbs.get(0);
        int py = button.getY(), pz = button.getZ();
        // The peg row starts two columns right of the button (bottom row): empty pegs are tinted glass, set pegs are wool.
        List<BlockPos> row = new ArrayList<>();
        for (int x = button.getX() + 2; ; x++) {
            String id = W.idAt(b, new BlockPos(x, py, pz));
            if (id.equals("tinted_glass") || id.endsWith("_wool")) {
                row.add(new BlockPos(x, py, pz));
            } else {
                break;
            }
        }
        int np = row.size();
        if (np == 0) {
            problem("crack the code: no pegs found next to the button");
            return;
        }
        int px0 = row.get(0).getX();
        // Palette strip: wool blocks one plane behind the board, in the button's column, bottom to top = colour 0..
        List<String> palette = new ArrayList<>();
        if (button != null) {
            for (int y = py + 1; y < py + 12; y++) {
                String id = W.idAt(b, new BlockPos(button.getX(), y, pz + 1));
                if (id.endsWith("_wool")) {
                    palette.add(id);
                } else if (!palette.isEmpty()) {
                    break;
                }
            }
        }
        int colours = palette.size();
        Rec.event("board", "kind", "crack_the_code", "pegs", np, "colours", colours, "button", button == null ? "none" : button.toShortString(), "palette", String.join(",", palette));
        if (colours == 0) {
            problem("crack the code: no colour strip visible beside the button");
            return;
        }
        // All codes, consistent-guess strategy.
        List<int[]> codes = new ArrayList<>();
        int total = (int) Math.pow(colours, np);
        for (int i = 0; i < total; i++) {
            int[] c = new int[np];
            int v = i;
            for (int k = 0; k < np; k++) {
                c[k] = v % colours;
                v /= colours;
            }
            codes.add(c);
        }
        Random rnd = new Random(7);
        int guessNo = 0;
        while (!codes.isEmpty() && guessNo < 14) {
            int[] g = codes.get(guessNo == 0 ? Math.min(codes.size() - 1, 5) : rnd.nextInt(codes.size()));
            // Set the pegs: click until the peg's block shows the wanted colour.
            for (int k = 0; k < np; k++) {
                BlockPos peg = row.get(k);
                for (int tries = 0; tries < colours + 2; tries++) {
                    String cur = W.idAt(b, peg);
                    if (cur.equals(palette.get(g[k]))) {
                        break;
                    }
                    if (!use(peg, "peg " + k)) {
                        break;
                    }
                    changed(peg, cur, 15);
                }
            }
            String got = guessString(row);
            Rec.event("guess", "n", guessNo + 1, "wanted", Arrays.toString(g), "pegs", got);
            String before = W.idAt(b, row.get(0));
            use(button, "check button");
            b.ticks(12);
            // Feedback row: one above the guess row, newest guess is the lowest.
            int gold = 0, iron = 0;
            for (int i = 0; i < np; i++) {
                String id = W.idAt(b, new BlockPos(px0 + np + 1 + i, py + 1, pz));
                if (id.equals("gold_block")) {
                    gold++;
                } else if (id.equals("iron_block")) {
                    iron++;
                }
            }
            Rec.event("feedback", "n", guessNo + 1, "gold", gold, "iron", iron);
            if (Hud.seen(roomHud, "Cracked")) {
                return;
            }
            final int fg = gold, fi = iron;
            final int[] gg = g;
            codes.removeIf(c -> {
                int[] s = score(c, gg, colours);
                return s[0] != fg || s[1] != fi;
            });
            guessNo++;
            b.ticks(6);
        }
    }

    String guessString(List<BlockPos> row) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : row) {
            sb.append(W.idAt(b, p).replace("_wool", "")).append(' ');
        }
        return sb.toString().trim();
    }

    static int[] score(int[] secret, int[] guess, int colours) {
        int exact = 0;
        int[] a = new int[colours], c = new int[colours];
        for (int i = 0; i < secret.length; i++) {
            if (secret[i] == guess[i]) {
                exact++;
            } else {
                a[secret[i]]++;
                c[guess[i]]++;
            }
        }
        int near = 0;
        for (int i = 0; i < colours; i++) {
            near += Math.min(a[i], c[i]);
        }
        return new int[] {exact, near};
    }

    // ---------------------------------------------------------------- Copy the picture

    void pattern() {
        // The wall behind is red wool (z+1 of the canvas plane); canvas and picture are the wool blocks in the plane in front of it.
        List<BlockPos> red = W.scanExact(b, 14, 14, 14, "red_wool");
        if (red.isEmpty()) {
            problem("copy the picture: no wall visible");
            return;
        }
        Map<Integer, Integer> perZ = new HashMap<>();
        red.forEach(p -> perZ.merge(p.getZ(), 1, Integer::sum));
        int pz = perZ.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey() - 1;
        List<BlockPos> wool = W.scan(b, 14, 14, 14, s -> s.endsWith("_wool")).stream().filter(p -> p.getZ() == pz).toList();
        int size = (int) Math.round(Math.sqrt(wool.size() / 2.0));
        int x0 = wool.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int y0 = wool.stream().mapToInt(BlockPos::getY).min().getAsInt();
        int y1 = y0 + 2 * size;
        Rec.event("board", "kind", "copy_the_picture", "size", size, "x0", x0, "y0", y0, "y1", y1, "z", pz);
        // Read the picture while it shows.
        String[][] pic = new String[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                pic[x][y] = W.idAt(b, new BlockPos(x0 + x, y0 + size + 1 + y, pz));
            }
        }
        Rec.event("picture", "cells", Arrays.deepToString(pic));
        b.shot("pattern-picture-shown");
        // A person might try to paint at once: the room says to remember first.
        int since = Hud.log.size();
        BlockPos probe = new BlockPos(x0, y0, pz);
        use(probe, "canvas (early try)");
        b.ticks(10);
        Rec.event("early_click_reply", "hud", String.join(" | ", Hud.log.subList(since, Hud.log.size())), "blockNow", W.idAt(b, probe));
        // Wait for the picture to hide (the top-left cell stops being wool).
        boolean hidden = b.until(() -> !W.id(b.mc.level.getBlockState(new BlockPos(x0, y0 + size + 1, pz))).endsWith("_wool"), 600);
        Rec.event("picture_hidden", "ok", hidden);
        b.shot("pattern-picture-hidden");
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                BlockPos p = new BlockPos(x0 + x, y0 + y, pz);
                for (int tries = 0; tries < 6; tries++) {
                    String cur = W.idAt(b, p);
                    if (cur.equals(pic[x][y])) {
                        break;
                    }
                    if (!use(p, "canvas " + x + "," + y)) {
                        break;
                    }
                    changed(p, cur, 15);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Pairs

    void pairs() {
        List<BlockPos> tiles = W.scanExact(b, 16, 12, 16, "black_glazed_terracotta");
        if (tiles.isEmpty()) {
            problem("pairs: no face-down tiles visible");
            return;
        }
        Map<Integer, Integer> perZ = new HashMap<>();
        tiles.forEach(p -> perZ.merge(p.getZ(), 1, Integer::sum));
        int pz = perZ.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        List<BlockPos> cells = new ArrayList<>(tiles.stream().filter(p -> p.getZ() == pz).toList());
        cells.sort(Comparator.<BlockPos>comparingInt(q -> -q.getY()).thenComparingInt(BlockPos::getX));
        Rec.event("board", "kind", "pairs", "tiles", cells.size());
        Map<BlockPos, String> known = new HashMap<>();
        Set<BlockPos> matched = new HashSet<>();
        int guard = 0;
        while (matched.size() < cells.size() && guard++ < 80) {
            // Is a known pair waiting?
            BlockPos a = null, c = null;
            outer:
            for (BlockPos p : known.keySet()) {
                for (BlockPos q : known.keySet()) {
                    if (!p.equals(q) && !matched.contains(p) && !matched.contains(q) && known.get(p).equals(known.get(q))) {
                        a = p;
                        c = q;
                        break outer;
                    }
                }
            }
            if (a == null) {
                BlockPos first = cells.stream().filter(p -> !known.containsKey(p) && !matched.contains(p)).findFirst().orElse(null);
                if (first == null) {
                    break;
                }
                String face = flip(first);
                if (face == null) {
                    break;
                }
                known.put(first, face);
                // Does it match a known tile?
                BlockPos twin = known.entrySet().stream().filter(e -> !e.getKey().equals(first) && !matched.contains(e.getKey()) && e.getValue().equals(face))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
                if (twin != null) {
                    flip(twin);
                    matched.add(first);
                    matched.add(twin);
                    b.ticks(6);
                    continue;
                }
                BlockPos second = cells.stream().filter(p -> !known.containsKey(p) && !matched.contains(p)).findFirst().orElse(null);
                if (second == null) {
                    break;
                }
                String face2 = flip(second);
                if (face2 == null) {
                    break;
                }
                known.put(second, face2);
                if (face2.equals(face)) {
                    matched.add(first);
                    matched.add(second);
                } else {
                    // wait for both to turn back
                    b.until(() -> W.id(b.mc.level.getBlockState(first)).equals("black_glazed_terracotta")
                        && W.id(b.mc.level.getBlockState(second)).equals("black_glazed_terracotta"), 60);
                }
                b.ticks(4);
            } else {
                flip(a);
                flip(c);
                matched.add(a);
                matched.add(c);
                b.ticks(6);
            }
        }
    }

    /** Right-clicks a face-down tile; returns what it turned into. */
    String flip(BlockPos p) {
        // a mismatched pair stays up for a moment and clicks in that time do nothing: wait and try again, as a person would
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!use(p, "tile")) {
                return null;
            }
            boolean ok = b.until(() -> !W.id(b.mc.level.getBlockState(p)).equals("black_glazed_terracotta"), 12);
            if (ok) {
                return W.idAt(b, p);
            }
            noEffect++;
            Rec.event("no_effect", "pos", p.toShortString(), "still", "black_glazed_terracotta", "attempt", attempt);
            b.ticks(20);
        }
        return null;
    }

    // ---------------------------------------------------------------- Sliding picture

    void slide() {
        List<BlockPos> g = W.scanExact(b, 20, 10, 14, "black_stained_glass");
        if (g.isEmpty()) {
            problem("slide: cannot see the finished picture");
            return;
        }
        BlockPos G = g.get(0);
        int n = 0;
        for (int x = G.getX(); ; x--) {
            String id = W.idAt(b, new BlockPos(x, G.getY(), G.getZ()));
            if (id.endsWith("_concrete") || id.equals("black_stained_glass")) {
                n++;
            } else {
                break;
            }
        }
        int fx0 = G.getX() - (2 * n + 2), fy0 = G.getY(), z = G.getZ();
        Rec.event("board", "kind", "sliding_picture", "n", n, "frameX0", fx0, "y0", fy0);
        String[][] want = new String[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                want[x][y] = W.idAt(b, new BlockPos(G.getX() - (n - 1) + x, fy0 + (n - 1 - y), z));
            }
        }
        // 0 = hole; other cells numbered by their wanted position.
        Map<String, Integer> num = new HashMap<>();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!(x == n - 1 && y == n - 1)) {
                    num.put(want[x][y], y * n + x + 1);
                }
            }
        }
        int[] start = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                String id = W.idAt(b, new BlockPos(fx0 + x, fy0 + (n - 1 - y), z));
                start[y * n + x] = num.getOrDefault(id, 0);
            }
        }
        Rec.event("slide_state", "tiles", Arrays.toString(start));
        List<Integer> path = solveSlide(start, n);
        if (path == null) {
            problem("slide: no solution found from the state read off the screen");
            return;
        }
        Rec.event("slide_plan", "moves", path.size());
        for (int cell : path) {
            int x = cell % n, y = cell / n;
            BlockPos p = new BlockPos(fx0 + x, fy0 + (n - 1 - y), z);
            String before = W.idAt(b, p);
            use(p, "tile " + x + "," + y);
            // the tile slides away: the block turns into air
            changed(p, before, 15);
            b.ticks(2);
        }
    }

    /** BFS over states (A* for 4x4). Returns the list of tile cells to click in order. */
    static List<Integer> solveSlide(int[] start, int n) {
        int N = n * n;
        int[] goal = new int[N];
        for (int i = 0; i < N - 1; i++) {
            goal[i] = i + 1;
        }
        record Node(int[] s, int hole, int g, int f, Node parent, int moved) {
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f));
        Map<String, Integer> best = new HashMap<>();
        int hole0 = 0;
        for (int i = 0; i < N; i++) {
            if (start[i] == 0) {
                hole0 = i;
            }
        }
        open.add(new Node(start, hole0, 0, h(start, n), null, -1));
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < 2_000_000) {
            Node cur = open.poll();
            if (Arrays.equals(cur.s, goal)) {
                LinkedList<Integer> out = new LinkedList<>();
                for (Node k = cur; k.parent != null; k = k.parent) {
                    out.addFirst(k.moved);
                }
                return out;
            }
            String key = Arrays.toString(cur.s);
            Integer seen = best.get(key);
            if (seen != null && seen <= cur.g) {
                continue;
            }
            best.put(key, cur.g);
            int hx = cur.hole % n, hy = cur.hole / n;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dd : d) {
                int nx = hx + dd[0], ny = hy + dd[1];
                if (nx < 0 || ny < 0 || nx >= n || ny >= n) {
                    continue;
                }
                int[] t = cur.s.clone();
                int cell = ny * n + nx;
                t[cur.hole] = t[cell];
                t[cell] = 0;
                open.add(new Node(t, cell, cur.g + 1, cur.g + 1 + h(t, n), cur, cell));
            }
        }
        return null;
    }

    static int h(int[] s, int n) {
        int sum = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i] != 0) {
                int gi = s[i] - 1;
                sum += Math.abs(gi % n - i % n) + Math.abs(gi / n - i / n);
            }
        }
        return sum;
    }

    // ---------------------------------------------------------------- Spot the difference

    void spot() {
        List<BlockPos> tc0 = W.scan(b, 18, 14, 14, s -> s.endsWith("_terracotta") && !s.contains("glazed"));
        // the pictures hang on the dark oak panel wall: keep the terracotta cells that have it right behind them
        List<BlockPos> tc = b.call(() -> tc0.stream().filter(q -> W.id(b.mc.level.getBlockState(q.south())).equals("dark_oak_planks")).toList());
        if (tc.isEmpty()) {
            problem("spot: no pictures visible");
            return;
        }
        Map<Integer, Integer> perZ = new HashMap<>();
        tc.forEach(p -> perZ.merge(p.getZ(), 1, Integer::sum));
        int pz = perZ.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        double px = b.pos().x;
        List<BlockPos> plane = tc.stream().filter(p -> p.getZ() == pz).toList();
        List<BlockPos> leftPic = plane.stream().filter(p -> p.getX() > px).toList(); // +x is on the player's left when facing +z
        List<BlockPos> rightPic = plane.stream().filter(p -> p.getX() < px).toList();
        int size = (int) leftPic.stream().mapToInt(BlockPos::getX).distinct().count();
        int lx0 = leftPic.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int rx0 = rightPic.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int yMin = leftPic.stream().mapToInt(BlockPos::getY).min().getAsInt();
        Rec.event("board", "kind", "spot_the_difference", "size", size, "leftX0", lx0, "rightX0", rx0, "z", pz);
        List<BlockPos> diffs = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                String l = W.idAt(b, new BlockPos(lx0 + x, yMin + y, pz));
                String r = W.idAt(b, new BlockPos(rx0 + x, yMin + y, pz));
                if (!l.equals(r)) {
                    diffs.add(new BlockPos(rx0 + x, yMin + y, pz));
                }
            }
        }
        Rec.event("spot_diffs", "count", diffs.size());
        b.shot("spot-pictures");
        for (BlockPos p : diffs) {
            use(p, "difference");
            b.ticks(4);
        }
    }

    // ---------------------------------------------------------------- Hanoi

    void hanoi() {
        List<BlockPos> golds = W.scanExact(b, 24, 6, 14, "gold_block");
        List<BlockPos> bases = new ArrayList<>();
        if (!golds.isEmpty()) {
            Map<String, List<BlockPos>> byRow = new HashMap<>();
            golds.forEach(p -> byRow.computeIfAbsent(p.getY() + "/" + p.getZ(), k -> new ArrayList<>()).add(p));
            bases = byRow.values().stream().filter(l -> l.size() == 3).findFirst().orElse(new ArrayList<>());
        }
        if (bases.size() != 3) {
            problem("hanoi: could not find the three gold bases (found " + golds.size() + " gold blocks)");
            return;
        }
        bases.sort(Comparator.<BlockPos>comparingInt(q -> -q.getX())); // post 0 (start) has the largest x
        int rings = 0;
        BlockPos b0 = bases.get(0);
        for (int y = b0.getY() + 1; y < b0.getY() + 9; y++) {
            if (W.idAt(b, new BlockPos(b0.getX(), y, b0.getZ() + 2)).endsWith("_wool")) {
                rings++;
            } else {
                break;
            }
        }
        Rec.event("board", "kind", "tower", "rings", rings, "bases", bases.toString());
        List<int[]> moves = new ArrayList<>();
        hanoiMoves(rings, 0, 2, 1, moves);
        final int rr = rings;
        final List<BlockPos> bs = bases;
        for (int[] m : moves) {
            towerClick(bs, m[0], rr);
            b.ticks(3);
            towerClick(bs, m[1], rr);
            b.ticks(3);
        }
    }

    /** Clicks the post: the nearest ring-wall / post block the crosshair can be put on (reach is 14, the outer posts are further than that from the start). */
    void towerClick(List<BlockPos> bases, int post, int rings) {
        BlockPos base = bases.get(post);
        List<BlockPos> cand = new ArrayList<>();
        for (int dy = 1; dy <= rings; dy++) {
            cand.add(new BlockPos(base.getX(), base.getY() + dy, base.getZ() + 1)); // the birch post
            for (int dx = -rings; dx <= rings; dx++) {
                cand.add(new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + 2)); // ring wall behind it
            }
        }
        cand.add(base);
        Map<BlockPos, Double> dist = b.call(() -> {
            Map<BlockPos, Double> m = new HashMap<>();
            Vec3 e = b.mc.player.getEyePosition();
            for (BlockPos q : cand) {
                double x = Math.max(q.getX(), Math.min(e.x, q.getX() + 1)), y = Math.max(q.getY(), Math.min(e.y, q.getY() + 1)), z = Math.max(q.getZ(), Math.min(e.z, q.getZ() + 1));
                m.put(q, e.distanceTo(new Vec3(x, y, z)));
            }
            return m;
        });
        cand.sort(Comparator.comparingDouble(dist::get));
        double lim = b.reachLimit();
        int tries = 0;
        for (BlockPos q : cand) {
            if (dist.get(q) > lim - 0.2 || tries++ > 12) {
                break;
            }
            String id = W.idAt(b, q);
            if (id.equals("air")) {
                continue;
            }
            if (b.aimAtBlock(q)) {
                maxReach = Math.max(maxReach, b.reachTo(q));
                b.rightClick();
                clicks++;
                return;
            }
            Bot.Aim a = b.crosshair();
            Rec.event("tower_aim_fail", "cand", q.toShortString() + " " + id, "reach", round(b.reachTo(q)), "crosshair", a.hit() + " " + a.pos() + (a.pos() == null ? "" : " " + W.idAt(b, a.pos())));
        }
        unreachable++;
        Rec.event("unaimable", "target", "tower post " + post, "reach", round(b.reachTo(base)), "limit", round(lim), "candidates", cand.size());
        use(base, "base " + post);
    }

    static void hanoiMoves(int n, int from, int to, int via, List<int[]> out) {
        if (n == 0) {
            return;
        }
        hanoiMoves(n - 1, from, via, to, out);
        out.add(new int[] {from, to});
        hanoiMoves(n - 1, via, to, from, out);
    }

    // ---------------------------------------------------------------- Whack-a-mole

    void whack() {
        List<BlockPos> dirt = W.scanExact(b, 14, 4, 14, "coarse_dirt");
        Rec.event("board", "kind", "whack_a_mole", "holes", dirt.size());
        long end = Rec.tick + 20 * 70;
        int hits = 0;
        while (Rec.tick < end && !Hud.seen(roomHud, "cleared") && !won(Hud.log.size())) {
            BlockPos up = b.call(() -> {
                for (BlockPos d : dirt) {
                    if (W.id(b.mc.level.getBlockState(d.above())).equals("jack_o_lantern")) {
                        return d.above();
                    }
                }
                return null;
            });
            if (up == null) {
                b.ticks(1);
                continue;
            }
            // quick aim: no waiting between look and click
            b.quickUse(Vec3.atCenterOf(up));
            clicks++;
            hits++;
            maxReach = Math.max(maxReach, b.reachTo(up));
            b.ticks(3);
            if (hits > 400) {
                break;
            }
        }
        Rec.event("whack_done", "clicks", hits);
    }

    // ---------------------------------------------------------------- Buried treasure

    void treasure() {
        // The field starts right in front of the players' strip (orange and yellow terracotta carpet): its columns give the
        // field's width, the row after the strip is the near edge, and the sand column runs to the far rim.
        Vec3 pp = b.pos();
        int fy = b.call(() -> b.mc.player.blockPosition().below().getY());
        List<BlockPos> strip = W.scan(b, 14, 1, 6, id -> id.equals("orange_terracotta") || id.equals("yellow_terracotta")).stream()
            .filter(q -> q.getY() == fy).toList();
        int fx0, fz0, side, depth;
        if (strip.isEmpty()) {
            problem("treasure: the players' strip is not in view; falling back to the biggest sand block");
            return;
        }
        fx0 = strip.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int fx1 = strip.stream().mapToInt(BlockPos::getX).max().getAsInt();
        fz0 = strip.stream().mapToInt(BlockPos::getZ).max().getAsInt() + 1;
        side = fx1 - fx0 + 1;
        int zEnd = fz0;
        int midX = (fx0 + fx1) / 2;
        while (W.idAt(b, new BlockPos(midX, fy, zEnd + 1)).equals("sand")) {
            zEnd++;
        }
        depth = zEnd - fz0 + 1;
        final int fyy = fy;
        Rec.event("board", "kind", "buried_treasure", "field", fx0 + ".." + fx1 + " x " + fz0 + ".." + zEnd, "y", fyy, "cells", side * depth);
        Map<String, int[]> heatOf = Map.of("red_wool", new int[] {0, 1}, "orange_wool", new int[] {2, 2}, "yellow_wool", new int[] {3, 4},
            "lime_wool", new int[] {5, 6}, "light_blue_wool", new int[] {7, 9}, "blue_wool", new int[] {10, 99});
        Set<Long> dug = new HashSet<>();
        List<int[]> readings = new ArrayList<>(); // {x, z, lo, hi}
        int digs = 0, chests = 0, failedDigs = 0;
        while (digs < 70 && !won(mark) && !Hud.seen(mark, "out of digs") && Rec.tick < deadline) {
            int[] bestC = null;
            double bestS = -1e9;
            for (int x = fx0; x < fx0 + side; x++) {
                for (int z = fz0; z < fz0 + depth; z++) {
                    if (dug.contains(key(x, z))) {
                        continue;
                    }
                    boolean ok = true;
                    double sc = 0, minD = 99;
                    for (int[] r : readings) {
                        int d = Math.abs(r[0] - x) + Math.abs(r[1] - z);
                        minD = Math.min(minD, d);
                        if (d < r[2]) {
                            ok = false;
                            break;
                        }
                        if (d <= r[3]) {
                            sc += 10;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    sc += Math.min(minD, 4) - 0.05 * (Math.abs(x - (fx0 + side / 2)) + Math.abs(z - (fz0 + depth / 2)));
                    if (sc > bestS) {
                        bestS = sc;
                        bestC = new int[] {x, z};
                    }
                }
            }
            if (bestC == null) {
                readings.clear(); // inconsistent readings (several chests): start the inference over
                if (dug.size() >= side * depth) {
                    break;
                }
                continue;
            }
            BlockPos pos = new BlockPos(bestC[0], fyy, bestC[1]);
            dug.add(key(bestC[0], bestC[1]));
            digs++;
            if (!digCell(pos)) {
                if (++failedDigs >= 4 && digs == failedDigs) {
                    problem("treasure: " + failedDigs + " digs in a row did nothing; giving up on digging");
                    break;
                }
                continue;
            }
            b.ticks(8);
            String floor = W.idAt(b, pos.below());
            Rec.event("dug", "cell", pos.toShortString(), "floor", floor, "n", digs);
            if (floor.equals("chest")) {
                chests++;
                readings.clear();
                continue;
            }
            int[] r = heatOf.get(floor);
            if (r != null) {
                readings.add(new int[] {bestC[0], bestC[1], r[0], r[1]});
            }
        }
        Rec.event("treasure_done", "digs", digs, "chestsFound", chests);
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Aim at the sand block and dig it with the held tool. */
    boolean digCell(BlockPos pos) {
        double d = b.reachTo(pos), lim = b.reachLimit();
        maxReach = Math.max(maxReach, d);
        if (d > lim - 0.3) {
            // walk onto the field towards it, as a person would
            Vec3 e = b.pos();
            double tx = pos.getX() + 0.5, tz = pos.getZ() + 0.5;
            double dz = tz - e.z, dx = tx - e.x, len = Math.max(0.1, Math.hypot(dx, dz));
            b.walkTo(tx - dx / len * 3.0, tz - dz / len * 3.0, 1.0, 160);
        }
        if (!b.aimAtBlock(pos)) {
            Rec.event("unaimable", "target", "sand " + pos.toShortString(), "reach", round(b.reachTo(pos)), "limit", round(lim), "crosshair", String.valueOf(b.crosshair().pos()));
            unreachable++;
            return false;
        }
        boolean started = b.startDig();
        int held = b.hold(() -> W.id(b.mc.level.getBlockState(pos)).equals("air") || !W.id(b.mc.level.getBlockState(pos)).equals("sand"), 100);
        boolean ok = !W.idAt(b, pos).equals("sand");
        clicks++;
        Rec.event("dig", "pos", pos.toShortString(), "startAttack", started, "heldTicks", held, "removed", ok);
        if (!ok) {
            noEffect++;
        }
        return ok;
    }

    // ---------------------------------------------------------------- chat rooms

    void typing() {
        int seen = roomHud;
        for (int line = 0; line < 3; line++) {
            String text = waitLine(seen, 200);
            if (text == null) {
                problem("typing: no line to type appeared in chat");
                return;
            }
            seen = Hud.log.size();
            int words = text.split(" ").length;
            b.ticks(Math.max(30, words * 14)); // about 85 wpm: reading and typing
            waitGo();
            b.chat(text);
            b.ticks(30);
            if (Hud.seen(seen, "not enough") ) {
                return;
            }
        }
    }

    /** Chat before "Go!" is not an answer: a person waits for the word. */
    void waitGo() {
        if (!b.until(() -> Hud.seen(roomHud, "go!"), 400)) {
            Rec.event("problem", "text", "no 'Go!' seen before typing");
        }
    }

    String waitLine(int since, int ticks) {
        long end = Rec.tick + ticks;
        while (Rec.tick < end) {
            for (int i = since; i < Hud.log.size(); i++) {
                String s = Hud.log.get(i);
                if (s.startsWith("C:>> ")) {
                    return s.substring(5).trim();
                }
            }
            b.ticks(2);
        }
        return null;
    }

    void math() {
        int seen = roomHud;
        for (int i = 0; i < 30; i++) {
            String text = waitLine(seen, 240);
            if (text == null) {
                return;
            }
            seen = Hud.log.size();
            String expr = text.replace("= ?", "").replace('x', '*').trim();
            long v;
            try {
                v = new Object() {
                    int pos = -1, ch;
                    void next() {
                        ch = ++pos < expr.length() ? expr.charAt(pos) : -1;
                    }
                    boolean eat(int c) {
                        while (ch == ' ') {
                            next();
                        }
                        if (ch == c) {
                            next();
                            return true;
                        }
                        return false;
                    }
                    long parse() {
                        next();
                        return expression();
                    }
                    long expression() {
                        long x = term();
                        for (;;) {
                            if (eat('+')) {
                                x += term();
                            } else if (eat('-')) {
                                x -= term();
                            } else {
                                return x;
                            }
                        }
                    }
                    long term() {
                        long x = factor();
                        while (eat('*')) {
                            x *= factor();
                        }
                        return x;
                    }
                    long factor() {
                        if (eat('(')) {
                            long x = expression();
                            eat(')');
                            return x;
                        }
                        int start = pos;
                        while (ch >= '0' && ch <= '9') {
                            next();
                        }
                        return Long.parseLong(expr.substring(start, pos));
                    }
                }.parse();
            } catch (Exception e) {
                problem("math: could not read the sum '" + text + "'");
                return;
            }
            b.ticks(50); // a few seconds of thinking
            waitGo();
            b.chat(Long.toString(v));
            b.ticks(20);
            if (Hud.seen(seen - 1, "cleared") || Hud.seen(seen - 1, "failed")) {
                return;
            }
        }
    }

    static String describe(Vec3 v) {
        return String.format("%.1f,%.1f,%.1f", v.x, v.y, v.z);
    }
}
