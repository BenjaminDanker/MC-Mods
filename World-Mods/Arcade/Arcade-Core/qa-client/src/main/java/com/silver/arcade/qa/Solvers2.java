package com.silver.arcade.qa;

import com.silver.qaclient.*;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Solvers for the solo rooms that had none: maze, connect four, simon, archery, drop, floor, red light, hold the ring,
 * block party, rising floor, hole in the wall, parkour. Each reads the room from the client world / HUD and acts only
 * through keys, head turns and clicks (Bot.drive runs a per-tick controller, like a person's hands).
 */
final class Solvers2 {
    private final Bot b;
    private final Solvers s;

    Solvers2(Bot b, Solvers s) {
        this.b = b;
        this.s = s;
    }

    static void note(String what, Object... kv) {
        Object[] a = new Object[kv.length + 2];
        a[0] = "text";
        a[1] = what;
        System.arraycopy(kv, 0, a, 2, kv.length);
        Rec.event("note", a);
    }

    // ------------------------------------------------------------------ small client-side helpers (client thread)

    static String idAt(Minecraft mc, int x, int y, int z) {
        return W.id(mc.level.getBlockState(new BlockPos(x, y, z)));
    }

    /** True when the point is inside some block's collision shape (what a foot can stand on). */
    static boolean solidPoint(Minecraft mc, double x, double y, double z) {
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos bp = BlockPos.containing(x, y, z).offset(0, dy, 0);
            var shape = mc.level.getBlockState(bp).getCollisionShape(mc.level, bp);
            if (shape.isEmpty()) {
                continue;
            }
            for (AABB a : shape.toAabbs()) {
                if (a.move(bp).inflate(1.0E-6).contains(x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A position trace every 10 ticks, for the log. */
    static void trace(Minecraft mc, int n, String what) {
        if (n % (what.equals("drop") ? 4 : 10) == 0) {
            Vec3 p = mc.player.position();
            Rec.event("trace", "what", what, "x", Math.round(p.x * 100) / 100.0, "y", Math.round(p.y * 100) / 100.0, "z", Math.round(p.z * 100) / 100.0,
                "ground", mc.player.onGround());
        }
    }

    static double speed(Minecraft mc) {
        Vec3 v = mc.player.getDeltaMovement();
        return Math.hypot(v.x, v.z);
    }

    // ------------------------------------------------------------------ Maze

    void maze() {
        Vec3 p0 = b.pos();
        int sx = (int) Math.floor(p0.x), sz = (int) Math.floor(p0.z);
        int fy = b.call(() -> b.mc.player.blockPosition().below().getY());
        List<BlockPos> golds = W.scanExact(b, 70, 2, 70, "gold_block").stream().filter(q -> q.getY() == fy).toList();
        Rec.event("board", "kind", "maze", "start", sx + "," + sz, "goldsOnFloor", golds.size());
        for (int attempt = 0; attempt < 3; attempt++) {
            Vec3 pp = b.pos();
            int cx = (int) Math.floor(pp.x), cz = (int) Math.floor(pp.z);
            // the exit: the gold block the walkable cells lead to (the rest of the gold is decoration outside the walls)
            List<int[]> path = b.call(() -> {
                Map<Long, long[]> prev = flood(cx, cz, fy, 75);
                BlockPos bestG = null;
                int bestLen = -1;
                for (BlockPos g : golds) {
                    if (!prev.containsKey(Solvers.key(g.getX(), g.getZ()))) {
                        continue;
                    }
                    int len = 0;
                    long[] cur = {g.getX(), g.getZ()};
                    while (cur != null) {
                        len++;
                        cur = prev.get(Solvers.key((int) cur[0], (int) cur[1]));
                    }
                    if (len > bestLen) {
                        bestLen = len;
                        bestG = g;
                    }
                }
                if (bestG == null) {
                    return null;
                }
                List<int[]> out = new ArrayList<>();
                long[] cur = {bestG.getX(), bestG.getZ()};
                while (cur != null) {
                    out.add(new int[] {(int) cur[0], (int) cur[1]});
                    cur = prev.get(Solvers.key((int) cur[0], (int) cur[1]));
                }
                Collections.reverse(out);
                return out;
            });
            if (path == null) {
                Solvers.problem("maze: no gold block reachable over the walkable cells in the client's view");
                return;
            }
            Rec.event("maze_path", "length", path.size());
            List<double[]> pts = new ArrayList<>();
            for (int[] c : path) {
                pts.add(new double[] {c[0] + 0.5, c[1] + 0.5});
            }
            final long deadline = s.deadline;
            boolean done = b.drive((mc, n) -> {
                if (Rec.tick > deadline) {
                    return true;
                }
                return follow(mc, pts, 0.3, false);
            }, 2400);
            Rec.event("maze_walk", "done", done, "pos", b.pos().toString());
            if (Hud.seen(s.mark, "cleared") || done) {
                break;
            }
        }
    }

    private int walkIdx;

    /** One step of following a list of points; true when the last one is reached. */
    private boolean follow(Minecraft mc, List<double[]> pts, double r, boolean sprint) {
        if (mc.player.tickCount < 0) {
            return true;
        }
        Vec3 p = mc.player.position();
        while (walkIdx < pts.size() && Math.hypot(pts.get(walkIdx)[0] - p.x, pts.get(walkIdx)[1] - p.z) < r) {
            walkIdx++;
        }
        if (walkIdx >= pts.size()) {
            walkIdx = 0;
            return true;
        }
        double[] t = pts.get(walkIdx);
        Bot.face(mc, Bot.yawTo(p.x, p.z, t[0], t[1]), 8f);
        Bot.keys(mc, true, false, false, false, false, sprint);
        return false;
    }

    private static Map<Long, long[]> flood(int sx, int sz, int fy, int rad) {
        Minecraft mc = Minecraft.getInstance();
        Map<Long, long[]> prev = new HashMap<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] {sx, sz});
        prev.put(Solvers.key(sx, sz), null);
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : d) {
                int nx = c[0] + dd[0], nz = c[1] + dd[1];
                if (Math.abs(nx - sx) > rad || Math.abs(nz - sz) > rad || prev.containsKey(Solvers.key(nx, nz))) {
                    continue;
                }
                BlockPos up = new BlockPos(nx, fy + 1, nz);
                boolean open = mc.level.getBlockState(up).getCollisionShape(mc.level, up).isEmpty()
                    && !mc.level.getBlockState(new BlockPos(nx, fy, nz)).isAir();
                if (open) {
                    prev.put(Solvers.key(nx, nz), new long[] {c[0], c[1]});
                    q.add(new int[] {nx, nz});
                }
            }
        }
        return prev;
    }

    // ------------------------------------------------------------------ Connect four

    void connectFour() {
        int fy = b.call(() -> b.mc.player.blockPosition().getY());
        List<BlockPos> golds = W.scanExact(b, 10, 2, 12, "gold_block").stream().filter(q -> q.getY() == fy).toList();
        List<BlockPos> pickers = new ArrayList<>();
        for (BlockPos g : golds) {
            String above = W.idAt(b, g.above());
            if (above.equals("white_stained_glass") || above.equals("yellow_concrete") || above.equals("red_concrete")) {
                pickers.add(g);
            }
        }
        pickers.sort(Comparator.comparingInt(BlockPos::getX));
        if (pickers.size() != 7) {
            Solvers.problem("connect four: expected 7 column pickers, saw " + pickers.size());
            return;
        }
        Rec.event("board", "kind", "connect_four", "pickers", pickers.toString());
        int[][] cells = new int[7][6];
        int moves = 0;
        for (int turn = 0; turn < 22; turn++) {
            if (Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "failed") || Rec.tick > s.deadline) {
                break;
            }
            readC4(pickers, cells);
            int col = C4.best(cells, 8);
            if (col < 0) {
                break;
            }
            int before = count(cells);
            s.use(pickers.get(col), "column " + col);
            moves++;
            Rec.event("c4_move", "col", col, "n", moves);
            // wait for our piece and the bot's reply (or the end of the game)
            b.until(() -> {
                int[][] t = new int[7][6];
                readC4(pickers, t);
                return count(t) >= before + 2 || Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "failed");
            }, 160);
        }
        readC4(pickers, cells);
        StringBuilder sb = new StringBuilder();
        for (int r = 5; r >= 0; r--) {
            for (int c = 0; c < 7; c++) {
                sb.append(cells[c][r] == 0 ? '.' : cells[c][r] == 1 ? 'Y' : 'R');
            }
            sb.append('/');
        }
        Rec.event("c4_final", "board", sb.toString(), "moves", moves);
    }

    private static int count(int[][] c) {
        int n = 0;
        for (int[] col : c) {
            for (int v : col) {
                n += v == 0 ? 0 : 1;
            }
        }
        return n;
    }

    private void readC4(List<BlockPos> pickers, int[][] cells) {
        b.run(() -> {
            for (int c = 0; c < 7; c++) {
                for (int r = 0; r < 6; r++) {
                    String id = W.id(b.mc.level.getBlockState(pickers.get(c).above(1 + r)));
                    cells[c][r] = id.equals("yellow_concrete") ? 1 : id.equals("red_concrete") ? 2 : 0;
                }
            }
        });
    }

    /** Negamax connect four for the player (1) against the room's bot (2). */
    static final class C4 {
        static boolean wins(int[][] c, int who) {
            int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
            for (int x = 0; x < 7; x++) {
                for (int y = 0; y < 6; y++) {
                    if (c[x][y] != who) {
                        continue;
                    }
                    for (int[] d : dirs) {
                        int k = 1;
                        while (k < 4) {
                            int xx = x + d[0] * k, yy = y + d[1] * k;
                            if (xx < 0 || xx >= 7 || yy < 0 || yy >= 6 || c[xx][yy] != who) {
                                break;
                            }
                            k++;
                        }
                        if (k == 4) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        static int drop(int[][] c, int col, int who) {
            for (int r = 0; r < 6; r++) {
                if (c[col][r] == 0) {
                    c[col][r] = who;
                    return r;
                }
            }
            return -1;
        }

        static int eval(int[][] c, int who) {
            int sc = 0;
            for (int r = 0; r < 6; r++) {
                sc += c[3][r] == who ? 3 : c[3][r] == 0 ? 0 : -3;
            }
            int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
            for (int x = 0; x < 7; x++) {
                for (int y = 0; y < 6; y++) {
                    for (int[] d : dirs) {
                        int mine = 0, theirs = 0;
                        boolean fits = true;
                        for (int k = 0; k < 4; k++) {
                            int xx = x + d[0] * k, yy = y + d[1] * k;
                            if (xx < 0 || xx >= 7 || yy < 0 || yy >= 6) {
                                fits = false;
                                break;
                            }
                            if (c[xx][yy] == who) {
                                mine++;
                            } else if (c[xx][yy] != 0) {
                                theirs++;
                            }
                        }
                        if (fits) {
                            if (theirs == 0) {
                                sc += mine == 3 ? 6 : mine == 2 ? 2 : 0;
                            } else if (mine == 0) {
                                sc -= theirs == 3 ? 6 : theirs == 2 ? 2 : 0;
                            }
                        }
                    }
                }
            }
            return sc;
        }

        static int search(int[][] c, int depth, int who, int alpha, int beta) {
            int other = 3 - who;
            if (wins(c, other)) {
                return -100000 - depth;
            }
            boolean any = false;
            for (int x = 0; x < 7; x++) {
                any |= c[x][5] == 0;
            }
            if (!any) {
                return 0;
            }
            if (depth == 0) {
                return eval(c, who);
            }
            int best = Integer.MIN_VALUE + 1;
            for (int col : new int[] {3, 2, 4, 1, 5, 0, 6}) {
                if (c[col][5] != 0) {
                    continue;
                }
                int r = drop(c, col, who);
                int v = -search(c, depth - 1, other, -beta, -alpha);
                c[col][r] = 0;
                best = Math.max(best, v);
                alpha = Math.max(alpha, v);
                if (alpha >= beta) {
                    break;
                }
            }
            return best;
        }

        static int best(int[][] c, int depth) {
            int bestCol = -1, bestV = Integer.MIN_VALUE;
            for (int col : new int[] {3, 2, 4, 1, 5, 0, 6}) {
                if (c[col][5] != 0) {
                    continue;
                }
                int r = drop(c, col, 1);
                int v = wins(c, 1) ? 1_000_000 : -search(c, depth - 1, 2, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
                c[col][r] = 0;
                if (v > bestV) {
                    bestV = v;
                    bestCol = col;
                }
            }
            return bestCol;
        }
    }

    // ------------------------------------------------------------------ Simon says

    void simon() {
        int fy = b.call(() -> b.mc.player.blockPosition().getY());
        Set<String> ids = Set.of("red_concrete", "blue_concrete", "lime_concrete", "yellow_concrete", "sea_lantern");
        List<BlockPos> cand = W.scan(b, 10, 2, 8, ids::contains);
        // the pad row: four blocks of these ids at the same y and z, two apart
        Map<String, List<BlockPos>> rows = new HashMap<>();
        for (BlockPos q : cand) {
            rows.computeIfAbsent(q.getY() + "/" + q.getZ(), k -> new ArrayList<>()).add(q);
        }
        List<BlockPos> pads = null;
        for (Map.Entry<String, List<BlockPos>> e : rows.entrySet()) {
            List<BlockPos> l = new ArrayList<>(e.getValue());
            l.sort(Comparator.comparingInt(BlockPos::getX));
            boolean ok = l.size() == 4;
            for (int i = 1; ok && i < 4; i++) {
                ok = l.get(i).getX() - l.get(i - 1).getX() == 2;
            }
            if (ok && l.get(0).getY() == fy && (pads == null || l.get(0).getZ() < pads.get(0).getZ())) {
                pads = l;
            }
        }
        if (pads == null) {
            Solvers.problem("simon: could not find the four pads (rows seen: " + rows.keySet() + ")");
            return;
        }
        final List<BlockPos> P = pads;
        Rec.event("board", "kind", "simon_says", "pads", P.toString());
        // Watch: log when each pad is lit (sea lantern) until the "Your turn" message.
        List<int[]> lit = new ArrayList<>(); // {startTick, pad}
        final int[] cur = {-1};
        final int since = s.mark;
        final long limit = Rec.tick + 20 * 60;
        b.drive((mc, n) -> {
            int l = -1;
            for (int i = 0; i < 4; i++) {
                if (idAt(mc, P.get(i).getX(), P.get(i).getY(), P.get(i).getZ()).equals("sea_lantern")) {
                    l = i;
                }
            }
            if (l != cur[0]) {
                if (l >= 0) {
                    lit.add(new int[] {(int) Rec.tick, l});
                }
                cur[0] = l;
            }
            return Hud.seen(since, "your turn") || Rec.tick > limit;
        }, 20 * 62);
        if (lit.isEmpty()) {
            Solvers.problem("simon: no pad ever lit");
            return;
        }
        int unit = Integer.MAX_VALUE;
        for (int i = 1; i < lit.size(); i++) {
            unit = Math.min(unit, lit.get(i)[0] - lit.get(i - 1)[0]);
        }
        List<Integer> seq = new ArrayList<>();
        for (int i = 0; i < lit.size(); i++) {
            int dur = i + 1 < lit.size() ? lit.get(i + 1)[0] - lit.get(i)[0] : unit; // the last run: unknown, one flash
            int reps = unit == Integer.MAX_VALUE ? 1 : Math.max(1, Math.round(dur / (float) unit));
            for (int k = 0; k < reps; k++) {
                seq.add(lit.get(i)[1]);
            }
        }
        StringBuilder sb = new StringBuilder();
        lit.forEach(x -> sb.append(x[0]).append(':').append(x[1]).append(' '));
        Rec.event("simon_seen", "flashes", sb.toString().trim(), "unitTicks", unit, "sequence", seq.toString());
        Solvers2.note("simon: a pad pressed twice in a row shows as one longer glow with no dark gap; only the bell tells a player it repeated");
        b.ticks(5);
        for (int pad : seq) {
            BlockPos t = P.get(pad);
            if (!s.use(t, "pad " + pad)) {
                break;
            }
            if (Hud.seen(since, "wrong") || Hud.seen(since, "cleared")) {
                break;
            }
        }
    }

    // ------------------------------------------------------------------ Archery

    void archery() {
        int slot = b.call(() -> {
            for (int i = 0; i < 9; i++) {
                if (b.mc.player.getInventory().getItem(i).getItem().toString().contains("bow")) {
                    return i;
                }
            }
            return -1;
        });
        if (slot < 0) {
            Solvers.problem("archery: no bow in the hotbar");
            return;
        }
        b.select(slot);
        int shots = 0, hits = 0;
        Set<Long> done = new HashSet<>();
        for (int t = 0; t < 20 && Rec.tick < s.deadline; t++) {
            if (Hud.seen(s.mark, "cleared")) {
                break;
            }
            List<BlockPos> tg = W.scanExact(b, 14, 14, 45, "target");
            BlockPos target = null;
            for (BlockPos q : tg) {
                String behind = W.idAt(b, q.south());
                if (behind.equals("green_wool") || behind.equals("hay_block") || behind.equals("gold_block")) {
                    target = q;
                    break;
                }
            }
            if (target == null) {
                b.ticks(10);
                if (tg.isEmpty() && Hud.seen(s.mark, "cleared")) {
                    break;
                }
                continue;
            }
            final BlockPos tp = target;
            boolean hit = false;
            for (int attempt = 0; attempt < 4 && !hit; attempt++) {
                shoot(tp);
                shots++;
                b.ticks(14);
                hit = !W.idAt(b, tp).equals("target");
            }
            Rec.event("archery_target", "pos", tp.toShortString(), "hit", hit, "shots", shots);
            if (hit) {
                hits++;
                b.ticks(3);
            } else {
                break;
            }
        }
        Rec.event("archery_done", "shots", shots, "targets", hits);
    }

    /** Draws the bow fully and looses an arrow so that, with gravity and drag, it reaches the front face of the target. */
    private void shoot(BlockPos t) {
        double[] aim = b.call(() -> {
            Vec3 e = b.mc.player.getEyePosition();
            double fx = t.getX() + 0.5, fy = t.getY() + 0.5, fz = t.getZ();
            double dx = fx - e.x, dz = fz - e.z;
            double hd = Math.hypot(dx, dz);
            double lo = -15, hi = 60;
            for (int i = 0; i < 40; i++) {
                double mid = (lo + hi) / 2;
                double y = arrowY(e.y - 0.1, mid, hd);
                if (y < fy) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return new double[] {Bot.yawTo(e.x, e.z, fx, fz), -(lo + hi) / 2};
        });
        b.drive((mc, n) -> {
            Bot.face(mc, (float) aim[0], (float) aim[1]);
            if (n == 1) {
                mc.options.keyUse.setDown(true);
                Bot.useItem(mc);
            }
            if (n == 26) {
                mc.options.keyUse.setDown(false);
            }
            return n >= 29;
        }, 80);
    }

    /** Height of an arrow after it has travelled the horizontal distance d, fired at pitch (degrees up) from height y0 at full draw. */
    static double arrowY(double y0, double pitchDeg, double d) {
        double vh = 3.0 * Math.cos(Math.toRadians(pitchDeg)), vy = 3.0 * Math.sin(Math.toRadians(pitchDeg));
        double x = 0, y = y0;
        for (int i = 0; i < 400; i++) {
            if (x + vh >= d) {
                return y + vy * ((d - x) / vh);
            }
            x += vh;
            y += vy;
            vh *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return -1000;
    }

    // ------------------------------------------------------------------ Red light, green light

    void redLight() {
        // The signal: a block of lime concrete (green) or red concrete (red), far ahead on the road.
        BlockPos sig = null;
        for (BlockPos q : W.scan(b, 8, 8, 70, id -> id.equals("lime_concrete") || id.equals("red_concrete"))) {
            if (sig == null || q.getZ() > sig.getZ()) {
                sig = q;
            }
        }
        if (sig == null) {
            Solvers.problem("red light: could not see the signal");
            return;
        }
        final BlockPos sg = sig;
        Rec.event("board", "kind", "red_light", "signal", sg.toShortString());
        final int[] stats = {0, 0}; // ticks walking, ticks frozen
        final int[] sw = {0, 0};
        final boolean[] was = {true};
        final long dl = s.deadline;
        boolean fin = b.drive((mc, n) -> {
            String id = idAt(mc, sg.getX(), sg.getY(), sg.getZ());
            boolean green = id.equals("lime_concrete");
            if (green != was[0]) {
                sw[green ? 0 : 1]++;
                was[0] = green;
            }
            Bot.face(mc, 0f, 6f);
            // freeze at once on red; a small lead: a person stops when the colour changes
            Bot.keys(mc, green, false, false, false, false, false);
            stats[green ? 0 : 1]++;
            return Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "made it") || Rec.tick > dl;
        }, 20 * 100);
        Rec.event("red_light_done", "finished", fin, "greenTicks", stats[0], "redTicks", stats[1], "greens", sw[0], "reds", sw[1], "pos", b.pos().toString());
    }

    // ------------------------------------------------------------------ Block party

    void blockParty() {
        final long dl = s.deadline;
        final int[] rounds = {0};
        final String[] lastCol = {""};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "wrong colour") || Rec.tick > dl) {
                return true;
            }
            var held = mc.player.getMainHandItem();
            if (held.isEmpty()) {
                Bot.keys(mc, false, false, false, false, false, false);
                return false;
            }
            String col = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            if (!col.equals(lastCol[0])) {
                lastCol[0] = col;
                rounds[0]++;
            }
            Vec3 p = mc.player.position();
            int fy = mc.player.blockPosition().below().getY();
            int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
            // cells of the called colour around, scored by distance and how many neighbours match (a margin for overshoot)
            double best = 1e9;
            int bx = 0, bz = 0;
            for (int x = px - 12; x <= px + 12; x++) {
                for (int z = pz - 12; z <= pz + 12; z++) {
                    if (!idAt(mc, x, fy, z).equals(col)) {
                        continue;
                    }
                    int same = 0;
                    int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : nb) {
                        same += idAt(mc, x + d[0], fy, z + d[1]).equals(col) ? 1 : 0;
                    }
                    double sc = Math.hypot(x + 0.5 - p.x, z + 0.5 - p.z) + (4 - same) * 0.9;
                    if (sc < best) {
                        best = sc;
                        bx = x;
                        bz = z;
                    }
                }
            }
            if (best > 1e8) {
                Bot.keys(mc, false, false, false, false, false, false);
                return false;
            }
            double d = Math.hypot(bx + 0.5 - p.x, bz + 0.5 - p.z);
            boolean on = idAt(mc, px, fy, pz).equals(col);
            if (d < 0.35 || on && d < 0.9 && speed(mc) < 0.02) {
                Bot.keys(mc, false, false, false, false, false, false);
            } else if (d < (speed(mc) > 0.15 ? 0.9 : 0.35)) {
                Bot.keys(mc, false, false, false, false, false, false);
            } else {
                Bot.face(mc, Bot.yawTo(p.x, p.z, bx + 0.5, bz + 0.5), 10f);
                Bot.keys(mc, true, false, false, false, false, d > 3.5);
            }
            return false;
        }, 20 * 150);
        Rec.event("block_party_done", "finished", fin, "colours", rounds[0], "pos", b.pos().toString());
    }

    // ------------------------------------------------------------------ Hole in the wall

    void holeInWall() {
        final long dl = s.deadline;
        final int[] walls = {0};
        final boolean[] seenWall = {false};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "swept") || Hud.seen(s.mark, "you fell") || Rec.tick > dl) {
                return true;
            }
            Vec3 p = mc.player.position();
            int fy = mc.player.blockPosition().getY();
            int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z);
            // find the nearest wall: purple concrete in the lanes ahead (z beyond the player), at the player's height
            int wz = Integer.MAX_VALUE;
            for (int z = pz + 1; z <= pz + 20; z++) {
                for (int x = px - 6; x <= px + 6; x++) {
                    if (idAt(mc, x, fy, z).equals("purple_concrete") || idAt(mc, x, fy + 2, z).equals("purple_concrete")) {
                        wz = z;
                        break;
                    }
                }
                if (wz != Integer.MAX_VALUE) {
                    break;
                }
            }
            double targetX = px + 0.5;
            if (wz != Integer.MAX_VALUE) {
                if (!seenWall[0]) {
                    walls[0]++;
                }
                seenWall[0] = true;
                // hole = the x range (within the stage, between the wall's two ends) where the wall has no block at the player's height
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                for (int x = px - 8; x <= px + 8; x++) {
                    if (idAt(mc, x, fy + 2, wz).equals("purple_concrete")) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                    }
                }
                List<Integer> gap = new ArrayList<>();
                for (int x = minX; x <= maxX; x++) {
                    if (!idAt(mc, x, fy, wz).equals("purple_concrete")) {
                        gap.add(x);
                    }
                }
                if (!gap.isEmpty()) {
                    targetX = (gap.get(0) + gap.get(gap.size() - 1)) / 2.0 + 0.5;
                }
            } else {
                seenWall[0] = false;
            }
            // strafe (facing +z: +x is left)
            double err = targetX - p.x;
            double v = mc.player.getDeltaMovement().x;
            double vd = Math.max(-0.22, Math.min(0.22, err * 0.35));
            boolean left = vd - v > 0.012 && err > 0.04;
            boolean right = vd - v < -0.012 && err < -0.04;
            Bot.face(mc, 0f, 10f);
            Bot.keys(mc, false, false, left, right, false, false);
            return false;
        }, 20 * 160);
        Rec.event("hole_done", "finished", fin, "walls", walls[0], "pos", b.pos().toString());
    }

    // ------------------------------------------------------------------ The floor is falling

    void fallingFloor() {
        Vec3 p0 = b.pos();
        int fy = b.call(() -> b.mc.player.blockPosition().below().getY());
        // floor bounds: concrete under the start
        int[] bnd = b.call(() -> {
            Minecraft mc = b.mc;
            int px = (int) Math.floor(p0.x), pz = (int) Math.floor(p0.z);
            int x0 = px, x1 = px, z0 = pz, z1 = pz;
            while (idAt(mc, x0 - 1, fy, pz).endsWith("_concrete")) {
                x0--;
            }
            while (idAt(mc, x1 + 1, fy, pz).endsWith("_concrete")) {
                x1++;
            }
            while (idAt(mc, px, fy, z0 - 1).endsWith("_concrete")) {
                z0--;
            }
            while (idAt(mc, px, fy, z1 + 1).endsWith("_concrete")) {
                z1++;
            }
            return new int[] {x0, x1, z0, z1};
        });
        Rec.event("board", "kind", "floor_is_falling", "x", bnd[0] + ".." + bnd[1], "z", bnd[2] + ".." + bnd[3]);
        // boustrophedon along z lanes, one lane per x, starting at the start's lane
        List<double[]> pts = new ArrayList<>();
        boolean up = true;
        int sx = (int) Math.floor(p0.x);
        for (int x = sx; x <= bnd[1]; x++) {
            double a = up ? bnd[3] : bnd[2], c = up ? bnd[2] : bnd[3];
            pts.add(new double[] {x + 0.5, a + 0.5});
            pts.add(new double[] {x + 0.5, c + 0.5});
            up = !up;
        }
        for (int x = sx - 1; x >= bnd[0]; x--) { // then the lanes the other way
            double a = up ? bnd[3] : bnd[2], c = up ? bnd[2] : bnd[3];
            pts.add(new double[] {x + 0.5, a + 0.5});
            pts.add(new double[] {x + 0.5, c + 0.5});
            up = !up;
        }
        final long dl = s.deadline;
        walkIdx = 0;
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "still standing") || Hud.seen(s.mark, "you fell") || Hud.seen(s.mark, "cleared") || Rec.tick > dl) {
                return true;
            }
            Vec3 p = mc.player.position();
            if (walkIdx >= pts.size()) {
                Bot.keys(mc, false, false, false, false, false, false);
                return false;
            }
            double[] t = pts.get(walkIdx);
            double d = Math.hypot(t[0] - p.x, t[1] - p.z);
            if (d < 0.35) {
                walkIdx++;
                return false;
            }
            Bot.face(mc, Bot.yawTo(p.x, p.z, t[0], t[1]), 8f);
            // slow near a lane end so the step to the next lane stays on the floor
            Bot.keys(mc, true, false, false, false, false, d > 3.0);
            return false;
        }, 20 * 100);
        walkIdx = 0;
        Rec.event("floor_done", "finished", fin, "pos", b.pos().toString());
    }

    // ------------------------------------------------------------------ Rising floor and parkour (hopping)

    private static double aboveSupport(Minecraft mc) {
        return mc.player.getY();
    }

    void risingFloor() {
        // the shaft: its glass walls give the inside; decoration copper and gold outside it is not part of the climb
        List<BlockPos> glass = W.scanExact(b, 10, 60, 10, "orange_stained_glass");
        if (glass.isEmpty()) {
            Solvers.problem("rising floor: the shaft walls are not in view");
            return;
        }
        int gx0 = glass.stream().mapToInt(BlockPos::getX).min().getAsInt() + 1, gx1 = glass.stream().mapToInt(BlockPos::getX).max().getAsInt() - 1;
        int gz0 = glass.stream().mapToInt(BlockPos::getZ).min().getAsInt() + 1, gz1 = glass.stream().mapToInt(BlockPos::getZ).max().getAsInt() - 1;
        List<BlockPos> led = W.scan(b, 10, 60, 10, id -> id.equals("waxed_cut_copper_slab") || id.equals("waxed_copper_block")).stream()
            .filter(q -> q.getX() >= gx0 && q.getX() <= gx1 && q.getZ() >= gz0 && q.getZ() <= gz1).collect(java.util.stream.Collectors.toList());
        List<BlockPos> gold = W.scanExact(b, 10, 60, 10, "gold_block").stream()
            .filter(q -> q.getX() >= gx0 && q.getX() <= gx1 && q.getZ() >= gz0 && q.getZ() <= gz1).toList();
        if (led.isEmpty()) {
            Solvers.problem("rising floor: no ledges seen");
            return;
        }
        led.sort(Comparator.comparingInt(BlockPos::getY));
        // the landing: the gold blocks at the top
        int topY = gold.stream().mapToInt(BlockPos::getY).max().orElse(led.get(led.size() - 1).getY() + 1);
        List<BlockPos> landing = gold.stream().filter(q -> q.getY() == topY).toList();
        BlockPos lastLedge = led.get(led.size() - 1);
        BlockPos nearGold = landing.stream().min(Comparator.comparingDouble(q -> q.distSqr(lastLedge))).orElse(lastLedge);
        double lx = nearGold.getX() + 0.5, lz = nearGold.getZ() + 0.5;
        List<double[]> path = new ArrayList<>(); // {x, topY, z, isLanding}
        for (BlockPos q : led) {
            path.add(new double[] {q.getX() + 0.5, q.getY() + 1.0, q.getZ() + 0.5});
        }
        path.add(new double[] {lx, topY + 1.0, lz});
        Rec.event("board", "kind", "rising_floor", "ledges", led.size(), "landing", lx + "," + lz + " y" + topY);
        final long dl = s.deadline;
        final int[] falls = {0};
        final int[] stuck = {0};
        final double[] lastY = {0};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "above it all") || Hud.seen(s.mark, "magma caught") || Hud.seen(s.mark, "cleared") || Rec.tick > dl) {
                return true;
            }
            Vec3 p = mc.player.position();
            // the next target: the lowest stop above the feet
            double[] t = null;
            for (double[] q : path) {
                if (q[1] > p.y + 0.3) {
                    t = q;
                    break;
                }
            }
            if (t == null) {
                t = path.get(path.size() - 1);
            }
            if (p.y < lastY[0] - 3) {
                falls[0]++;
            }
            lastY[0] = Math.max(p.y, lastY[0] - 0.02);
            trace(mc, n, "rising t=" + t[0] + "," + t[1] + "," + t[2]);
            double d = Math.hypot(t[0] - p.x, t[2] - p.z);
            boolean ground = mc.player.onGround();
            // stuck (under a ledge after a fall): walk to the shaft's middle first
            if (ground && speed(mc) < 0.01) {
                stuck[0]++;
            } else if (speed(mc) > 0.05) {
                stuck[0] = 0;
            }
            if (stuck[0] > 12) {
                Bot.face(mc, Bot.yawTo(p.x, p.z, (gx0 + gx1) / 2.0 + 0.5, (gz0 + gz1) / 2.0 + 0.5), 5f);
                Bot.keys(mc, true, false, false, false, false, false);
                if (stuck[0] > 40) {
                    stuck[0] = 0;
                }
                return false;
            }
            Bot.face(mc, Bot.yawTo(p.x, p.z, t[0], t[2]), 5f);
            // a walking jump from the ledge's edge lands about 2.2 blocks on: right for a ledge 2 blocks along and 1 up
            boolean jump = ground && d <= 1.55 && t[1] > p.y + 0.3;
            Bot.keys(mc, true, false, false, false, jump, false);
            return false;
        }, 20 * 100);
        Rec.event("rising_done", "finished", fin, "falls", falls[0], "pos", b.pos().toString());
    }

    private static final Set<String> HOLDS = Set.of("red_concrete", "yellow_concrete", "light_blue_concrete", "white_concrete", "magenta_concrete", "lime_concrete",
        "smooth_stone_slab", "oak_fence", "cobblestone_wall", "emerald_block", "gold_block");

    void parkour() {
        Vec3 p0 = b.pos();
        int fy = b.call(() -> b.mc.player.blockPosition().below().getY());
        // footholds float on their own: a candidate block with air on its four sides and above (decoration is clustered)
        final int sx = (int) Math.floor(p0.x), sz = (int) Math.floor(p0.z);
        List<BlockPos> cands = b.call(() -> {
            List<BlockPos> out = new ArrayList<>();
            Minecraft mc = b.mc;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int z = sz + 3; z <= sz + 230; z++) {
                for (int x = sx - 16; x <= sx + 16; x++) {
                    for (int y = fy - 16; y <= fy + 16; y++) {
                        m.set(x, y, z);
                        if (mc.level.getBlockState(m).isAir() || !HOLDS.contains(W.id(mc.level.getBlockState(m)))) {
                            continue;
                        }
                        boolean iso = true;
                        for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
                            if (!mc.level.getBlockState(m.offset(d[0], d[1], d[2])).isAir()) {
                                iso = false;
                                break;
                            }
                        }
                        if (iso) {
                            out.add(m.immutable());
                        }
                    }
                }
            }
            return out;
        });
        // the course is the chain from the start: each next foothold a few blocks further along z, close in x and height
        cands.sort(Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        List<BlockPos> course = new ArrayList<>();
        int cx = sx, cy = fy, cz = sz + 2;
        while (true) {
            BlockPos next = null;
            for (BlockPos q : cands) {
                if (q.getZ() > cz && q.getZ() <= cz + 5 && Math.abs(q.getX() - cx) <= 3 && q.getY() - cy <= 3 && cy - q.getY() <= 8) {
                    next = q;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            course.add(next);
            cx = next.getX();
            cy = next.getY();
            cz = next.getZ();
        }
        if (!course.isEmpty()) {
            BlockPos last = course.get(course.size() - 1);
            for (int dz = 2; dz <= 4; dz++) {
                BlockPos g = last.offset(0, 0, dz);
                if (W.idAt(b, g).equals("gold_block")) {
                    course.add(g);
                    break;
                }
            }
        }
        Map<String, Integer> kinds = new TreeMap<>();
        course.forEach(q -> kinds.merge(W.idAt(b, q), 1, Integer::sum));
        Rec.event("board", "kind", "parkour", "footholds", course.size(), "kinds", kinds.toString(), "decorFloating", cands.size() - course.size());
        if (course.isEmpty()) {
            Solvers.problem("parkour: no footholds in view");
            return;
        }
        // reachability report: gaps and rises between consecutive footholds as a player sees them
        int maxGap = 0, maxRise = -99, jumps = 0;
        BlockPos prev = null;
        for (BlockPos q : course) {
            if (prev != null && q.getZ() > prev.getZ()) {
                maxGap = Math.max(maxGap, Math.max(Math.abs(q.getZ() - prev.getZ()), Math.abs(q.getX() - prev.getX())) - 1);
                maxRise = Math.max(maxRise, q.getY() - prev.getY());
                jumps++;
            }
            prev = q;
        }
        Rec.event("parkour_course", "jumps", jumps, "maxGapBlocks", maxGap, "maxRise", maxRise);
        StringBuilder cs = new StringBuilder();
        course.forEach(q -> cs.append(q.getX()).append(',').append(q.getY()).append(',').append(q.getZ()).append(W.idAt(b, q).replace("_concrete", "").replace("_block", "")).append(' '));
        Rec.event("parkour_blocks", "list", cs.toString().trim());
        final double[] tops = new double[course.size()];
        for (int i = 0; i < course.size(); i++) {
            String id = W.idAt(b, course.get(i));
            tops[i] = course.get(i).getY() + (id.equals("smooth_stone_slab") ? 0.5 : id.equals("oak_fence") || id.equals("cobblestone_wall") ? 1.5 : 1.0);
        }
        final long dl = s.deadline;
        final int[] stats = {0, 0, 0}; // falls, jumps, furthest index
        final double[] last = {p0.y};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "failed") || Rec.tick > dl) {
                return true;
            }
            Vec3 p = mc.player.position();
            if (p.y < last[0] - 4) {
                stats[0]++;
            }
            last[0] = p.y;
            trace(mc, n, "parkour");
            // the next foothold: the first one whose top is not yet under our feet and that lies further along than where we stand
            int ti = -1;
            for (int i = 0; i < course.size(); i++) {
                BlockPos q = course.get(i);
                boolean here = Math.abs(q.getX() + 0.5 - p.x) < 0.8 && Math.abs(q.getZ() + 0.5 - p.z) < 0.8 && p.y >= tops[i] - 0.2 && p.y < tops[i] + 0.3 && mc.player.onGround();
                if (here) {
                    ti = Math.min(i + 1, course.size() - 1);
                    break;
                }
                if (q.getZ() + 0.5 > p.z + 0.6 && ti < 0) {
                    ti = i;
                    break;
                }
            }
            if (ti < 0) {
                ti = course.size() - 1;
            }
            stats[2] = Math.max(stats[2], ti);
            BlockPos t = course.get(ti);
            double tx = t.getX() + 0.5, tz = t.getZ() + 0.5;
            double hd = Math.hypot(tx - p.x, tz - p.z);
            double hx = (tx - p.x) / Math.max(hd, 1e-6), hz = (tz - p.z) / Math.max(hd, 1e-6);
            Bot.face(mc, Bot.yawTo(p.x, p.z, tx, tz), 5f);
            Vec3 v = mc.player.getDeltaMovement();
            if (!mc.player.onGround()) {
                // in the air: let go, hold or brake so the drift ends over the foothold
                double y = p.y, vy = v.y;
                int T = 0;
                while (T < 45) {
                    y += vy;
                    vy = (vy - 0.08) * 0.98;
                    T++;
                    if (vy < 0 && y <= tops[ti]) {
                        break;
                    }
                }
                double vh = v.x * hx + v.z * hz;
                int bestA = 0;
                double bestErr = 1e9;
                for (int a = -1; a <= 1; a++) {
                    double vv = vh, xx = 0;
                    for (int k = 0; k < T; k++) {
                        vv += a * 0.0196; // vanilla: input acceleration, move, then air drag
                        xx += vv;
                        vv *= 0.91;
                    }
                    double err = Math.abs(xx - hd) + (a == 0 ? 0 : 0.05);
                    if (err < bestErr) {
                        bestErr = err;
                        bestA = a;
                    }
                }
                Bot.keys(mc, bestA > 0, bestA < 0, false, false, false, false);
                return false;
            }
            double look = Math.max(0.25, Math.min(0.9, speed(mc) * 2.5));
            boolean edge = !solidPoint(mc, p.x + hx * look, p.y - 0.05, p.z + hz * look);
            boolean jump = edge || (t.getY() + 1 > p.y + 0.4 && hd < 1.6);
            if (jump) {
                stats[1]++;
            }
            // how much of the way the platform under us still covers; the rest must be flown: sprint only for long jumps
            double e = 0;
            while (e < 4 && solidPoint(mc, p.x + hx * e, p.y - 0.05, p.z + hz * e)) {
                e += 0.1;
            }
            // a step up needs speed to be over the edge in time; a flat gap needs it only when long
            boolean sprint = hd - e > 3.0 || tops[ti] - p.y >= 0.9 && hd - e > 0.8;
            if (!sprint && mc.player.isSprinting()) {
                Bot.keys(mc, false, false, false, false, false, false); // a sprint only ends when the forward key is let go
                return false;
            }
            Bot.keys(mc, true, false, false, false, jump, sprint);
            return false;
        }, 20 * 160);
        Rec.event("parkour_done", "finished", fin, "falls", stats[0], "jumps", stats[1], "furthestFoothold", stats[2] + "/" + course.size(), "pos", b.pos().toString());
    }

    // ------------------------------------------------------------------ Hold the ring (fighting)

    void holdTheRing() {
        int slot = b.call(() -> {
            for (int i = 0; i < 9; i++) {
                if (b.mc.player.getInventory().getItem(i).getItem().toString().contains("sword")) {
                    return i;
                }
            }
            return 0;
        });
        b.select(slot);
        final long dl = s.deadline;
        final int[] stat = {0, 0, 0}; // swings, minHealth*10 tracker, mobsSeenMax
        final float[] minHp = {20f};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "ring holds") || Hud.seen(s.mark, "cleared") || Hud.seen(s.mark, "failed") || mc.player.getHealth() <= 0 || Rec.tick > dl) {
                return true;
            }
            minHp[0] = Math.min(minHp[0], mc.player.getHealth());
            net.minecraft.world.entity.LivingEntity best = null;
            double bd = 1e9;
            int count = 0;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.monster.Enemy && e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()) {
                    count++;
                    double d = e.distanceTo(mc.player);
                    if (d < bd) {
                        bd = d;
                        best = le;
                    }
                }
            }
            stat[2] = Math.max(stat[2], count);
            if (best == null) {
                Bot.keys(mc, false, false, false, false, false, false);
                return false;
            }
            Vec3 e = mc.player.getEyePosition();
            Vec3 c = best.getBoundingBox().getCenter();
            double dx = c.x - e.x, dy = c.y - e.y, dz = c.z - e.z;
            double h = Math.hypot(dx, dz);
            Bot.face(mc, (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0), (float) -Math.toDegrees(Math.atan2(dy, h)));
            Bot.pick(mc);
            boolean close = bd <= 2.6;
            Bot.keys(mc, !close, false, false, false, false, bd > 6);
            if (close && mc.player.getAttackStrengthScale(0f) >= 0.98f) {
                if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    Bot.attack(mc);
                    stat[0]++;
                }
            }
            return false;
        }, 20 * 400);
        Rec.event("ring_done", "finished", fin, "swings", stat[0], "maxMobsSeen", stat[2], "minHealth", minHp[0], "health", b.call(() -> (double) b.mc.player.getHealth()));
    }

    // ------------------------------------------------------------------ The drop

    void drop() {
        for (int attempt = 0; attempt < 6 && Rec.tick < s.deadline && !Hud.seen(s.mark, "cleared"); attempt++) {
            if (!dropOnce(attempt)) {
                break;
            }
        }
    }

    private boolean dropOnce(int attempt) {
        // wait for the player to be at the top (after a miss the server puts them back)
        Vec3 p0 = b.pos();
        int topFeet = (int) Math.floor(p0.y);
        List<BlockPos> water = W.scanExact(b, 12, 70, 12, "water");
        if (water.isEmpty()) {
            Solvers.problem("drop: no pool in view");
            return false;
        }
        int wy = water.stream().mapToInt(BlockPos::getY).min().getAsInt();
        List<BlockPos> pool = water.stream().filter(q -> q.getY() == wy).toList();
        // shaft walls: sea lanterns in the ring at the rim give the bounds
        List<BlockPos> lant = W.scanExact(b, 12, 4, 12, "sea_lantern").stream().filter(q -> q.getY() == topFeet - 1).toList();
        if (lant.isEmpty()) {
            Solvers.problem("drop: could not see the shaft rim lanterns");
            return false;
        }
        int x0 = lant.stream().mapToInt(BlockPos::getX).min().getAsInt(), x1 = lant.stream().mapToInt(BlockPos::getX).max().getAsInt();
        int z0 = lant.stream().mapToInt(BlockPos::getZ).min().getAsInt(), z1 = lant.stream().mapToInt(BlockPos::getZ).max().getAsInt();
        // interior cells (x0+1..x1-1, z0+1..z1-1)
        int ix0 = x0 + 1, ix1 = x1 - 1, iz0 = z0 + 1, iz1 = z1 - 1;
        // ledges: red/white concrete inside the interior below the rim
        List<BlockPos> ledgeBlocks = W.scan(b, 12, 70, 12, id -> id.equals("red_concrete") || id.equals("white_concrete"));
        Map<Integer, Set<Long>> byY = new TreeMap<>(Comparator.reverseOrder());
        for (BlockPos q : ledgeBlocks) {
            if (q.getX() >= ix0 && q.getX() <= ix1 && q.getZ() >= iz0 && q.getZ() <= iz1 && q.getY() > wy + 1 && q.getY() < topFeet - 2) {
                byY.computeIfAbsent(q.getY(), k -> new HashSet<>()).add(Solvers.key(q.getX(), q.getZ()));
            }
        }
        Set<Long> poolCells = new HashSet<>();
        pool.forEach(q -> poolCells.add(Solvers.key(q.getX(), q.getZ())));
        Rec.event("drop_plan", "attempt", attempt, "interior", ix0 + ".." + ix1 + " x " + iz0 + ".." + iz1, "pool", pool.size() + " cells at y" + wy + " x" + pool.stream().mapToInt(BlockPos::getX).min().getAsInt() + ".." + pool.stream().mapToInt(BlockPos::getX).max().getAsInt()
                + " z" + pool.stream().mapToInt(BlockPos::getZ).min().getAsInt() + ".." + pool.stream().mapToInt(BlockPos::getZ).max().getAsInt(),
            "ledgeLayers", byY.size(), "ledgeBlocks", byY.values().stream().mapToInt(Set::size).sum());
        // Steering: every tick, try holding each of nine directions for a few ticks and then coasting, simulating the fall
        // (air control, drag, gravity) against the ledges and the shaft walls; take the first move of the sequence that
        // ends closest to the pool without touching anything.
        final List<double[]> boxes = new ArrayList<>(); // {x, y, z} of ledge blocks
        byY.forEach((yy, cells) -> cells.forEach(k -> boxes.add(new double[] {(int) (k.longValue() >> 32), yy.doubleValue(), (int) k.longValue()})));
        final double[][] poolC = new double[pool.size()][];
        for (int i = 0; i < pool.size(); i++) {
            poolC[i] = new double[] {pool.get(i).getX() + 0.5, pool.get(i).getZ() + 0.5};
        }
        final double minX = ix0 + 0.3, maxX = ix1 + 1 - 0.3, minZ = iz0 + 0.3, maxZ = iz1 + 1 - 0.3;
        final double landY = wy + 1.0;
        Rec.event("drop_path", "mode", "receding-horizon steering", "ledges", boxes.size(), "pool", pool.size());
        final long dl = s.deadline;
        final boolean[] airborne = {false};
        final double[] lastIn = {0, 0, 0};
        boolean fin = b.drive((mc, n) -> {
            if (Hud.seen(s.mark, "splash") || Rec.tick > dl) {
                return true;
            }
            Vec3 p = mc.player.position();
            if (!airborne[0]) {
                Bot.face(mc, 0f, 10f);
                if (!mc.player.onGround() && p.y < topFeet - 0.4) {
                    airborne[0] = true;
                }
                Bot.keys(mc, true, false, false, false, false, false);
                return false;
            }
            if (mc.player.onGround()) {
                Vec3 q = mc.player.position();
                Rec.event("drop_landed", "lastAirX", lastIn[0], "lastAirY", lastIn[1], "lastAirZ", lastIn[2], "x", q.x, "y", q.y, "z", q.z);
                return true;
            }
            if (lastIn[1] > 0 && p.y > lastIn[1] + 10) {
                Rec.event("drop_landed", "lastAirX", lastIn[0], "lastAirY", lastIn[1], "lastAirZ", lastIn[2], "note", "put back at the top");
                return true;
            }
            lastIn[0] = p.x;
            lastIn[1] = p.y;
            lastIn[2] = p.z;
            trace(mc, n, "drop");
            Vec3 v = mc.player.getDeltaMovement();
            int[] bestDir = planDrop(p, v, boxes, poolC, minX, maxX, minZ, maxZ, landY);
            Bot.face(mc, 0f, 30f);
            // facing +z: forward = +z, back = -z, left = +x, right = -x
            Bot.keys(mc, bestDir[1] > 0, bestDir[1] < 0, bestDir[0] > 0, bestDir[0] < 0, false, false);
            return false;
        }, 20 * 25);
        Rec.event("drop_attempt", "attempt", attempt, "finished", fin, "pos", b.pos().toString(), "cleared", Hud.seen(s.mark, "splash"));
        if (!Hud.seen(s.mark, "splash")) {
            b.until(() -> b.mc.player.getY() > topFeet - 1, 80);
            b.ticks(10);
        }
        return true;
    }

    /** Beam search over key sequences (nine directions, each held three ticks) through the fall; returns the first move. */
    static int[] planDrop(Vec3 p, Vec3 v, List<double[]> boxes, double[][] poolC, double minX, double maxX, double minZ, double maxZ, double landY) {
        // state: x, z, vx, vz, y, vy, firstDx, firstDz, steps
        List<double[]> beam = new ArrayList<>();
        beam.add(new double[] {p.x, p.z, v.x, v.z, p.y, v.y, 99, 99, 0});
        double[] best = null;
        double bestScore = 1e18;
        for (int step = 0; step < 30 && !beam.isEmpty(); step++) {
            Map<Long, double[]> next = new HashMap<>();
            for (double[] st : beam) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        double x = st[0], z = st[1], vx = st[2], vz = st[3], y = st[4], vy = st[5];
                        boolean ok = true, landed = false;
                        double ax = dx * 0.0196, az = dz * 0.0196;
                        if (dx != 0 && dz != 0) {
                            ax *= 0.7071;
                            az *= 0.7071;
                        }
                        for (int t = 0; t < 3 && ok; t++) {
                            vx += ax;
                            vz += az;
                            x += vx;
                            z += vz;
                            vx *= 0.91;
                            vz *= 0.91;
                            y += vy;
                            vy = (vy - 0.08) * 0.98;
                            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                                ok = false;
                                break;
                            }
                            for (double[] bx : boxes) {
                                if (y < bx[1] + 1 && y + 1.8 > bx[1] && Math.abs(x - (bx[0] + 0.5)) < 0.8 && Math.abs(z - (bx[2] + 0.5)) < 0.8) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (y <= landY) {
                                landed = true;
                                break;
                            }
                        }
                        if (!ok) {
                            continue;
                        }
                        double dmin = 1e9;
                        for (double[] c : poolC) {
                            dmin = Math.min(dmin, Math.max(Math.abs(x - c[0]), Math.abs(z - c[1])));
                        }
                        double[] ns = {x, z, vx, vz, y, vy, st[6] == 99 ? dx : st[6], st[6] == 99 ? dz : st[7], st[8] + 1};
                        if (landed) {
                            double sc = Math.max(0, dmin - 0.2) * 10 + ns[8] * 0.01;
                            if (sc < bestScore) {
                                bestScore = sc;
                                best = ns;
                            }
                            continue;
                        }
                        // remaining fall time (ticks) bounds how much further we can still move: rank by what is left to cover
                        double remaining = 0;
                        {
                            double yy = y, vv = vy;
                            while (yy > landY && remaining < 80) {
                                yy += vv;
                                vv = (vv - 0.08) * 0.98;
                                remaining++;
                            }
                        }
                        double h = Math.max(0, dmin - 0.2 - 0.14 * remaining * 0.6) + dmin * 0.05;
                        ns[8] = st[8] + 1;
                        long key = ((long) Math.round(x * 5) << 40) ^ ((long) Math.round(z * 5) << 20) ^ ((long) Math.round(vx * 25) << 10) ^ Math.round(vz * 25) ^ ((long) Math.round(y) << 50);
                        double[] old = next.get(key);
                        if (old == null || h < old[9]) {
                            double[] ns2 = Arrays.copyOf(ns, 10);
                            ns2[9] = h;
                            next.put(key, ns2);
                        }
                    }
                }
            }
            if (next.isEmpty()) {
                break;
            }
            List<double[]> all = new ArrayList<>(next.values());
            all.sort(Comparator.comparingDouble(a -> a[9]));
            beam = new ArrayList<>(all.subList(0, Math.min(250, all.size())));
        }
        if (best == null) {
            // nothing landed within the horizon without touching: take the closest survivor
            for (double[] st : beam) {
                if (best == null || st[9] < best[9]) {
                    best = st;
                }
            }
        }
        if (best == null || best[6] == 99) {
            return new int[] {0, 0};
        }
        return new int[] {(int) best[6], (int) best[7]};
    }

    /** Roughly how far a player can drift sideways while falling h blocks (air control from rest). */
    static double lateralReach(double h) {
        double y = 0, vy = 0;
        int t = 0;
        while (y < h && t < 200) {
            vy = (vy + 0.08) * 0.98;
            y += vy;
            t++;
        }
        double v = 0, x = 0;
        for (int i = 0; i < t; i++) {
            v = v * 0.91 + 0.0196;
            x += v;
        }
        return x;
    }
}
