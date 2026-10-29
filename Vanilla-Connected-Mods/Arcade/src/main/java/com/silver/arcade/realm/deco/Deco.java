package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A room's decoration, drawn first and placed afterwards: blocks are collected here (later calls overwrite earlier
 * ones), then {@link #commit()} hands them to the arena, which places them over the next ticks. Decoration never
 * replaces a block the room itself placed, and never goes into a keep-out box (space the game needs empty).
 * Block states are written like in commands ("lantern[hanging=true]"); "a|b|c" picks one at random per block.
 */
public final class Deco {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");
    private static final Map<String, BlockState> STATES = new HashMap<>();

    private final Arena arena;
    private final Map<Long, BlockState> cells = new LinkedHashMap<>();
    private final List<int[]> keep = new ArrayList<>();
    public final Random rnd;

    public Deco(Arena arena, long seed) {
        this.arena = arena;
        this.rnd = new Random(seed);
    }

    public Arena arena() {
        return arena;
    }

    /** A block state from its command form ("minecraft:" may be left out). */
    public static BlockState st(String text) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(text, t -> {
                try {
                    return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, t, false).blockState();
                } catch (Exception bad) {
                    throw new IllegalArgumentException("bad block state " + t, bad);
                }
            });
        }
    }

    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A state or null when the text is not a valid block state (logged once; decoration never breaks a room). */
    private BlockState pick(String text) {
        String one = text;
        if (text.indexOf('|') >= 0) {
            String[] options = text.split("\\|");
            one = options[rnd.nextInt(options.length)];
        }
        try {
            return st(one);
        } catch (IllegalArgumentException bad) {
            if (WARNED.add(one)) {
                LOGGER.warn("Decoration: unknown block state '{}' left out", one);
            }
            return null;
        }
    }

    /** Space the game needs as it is: nothing is drawn into it. */
    public Deco keepOut(int x1, int y1, int z1, int x2, int y2, int z2) {
        keep.add(new int[] {Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
        return this;
    }

    private boolean kept(int x, int y, int z) {
        for (int[] k : keep) {
            if (x >= k[0] && x <= k[3] && y >= k[1] && y <= k[4] && z >= k[2] && z <= k[5]) {
                return true;
            }
        }
        return false;
    }

    public void set(int x, int y, int z, String state) {
        BlockState picked = pick(state);
        if (picked != null) {
            set(x, y, z, picked);
        }
    }

    public void set(int x, int y, int z, BlockState state) {
        if (Math.abs(x) > 200 || Math.abs(z) > 200 || y < -90 || y > 150) {
            return; // stays inside the slot
        }
        cells.put(BlockPos.asLong(x, y, z), state);
    }

    /** Removes something drawn earlier (it is not placed). */
    public void erase(int x, int y, int z) {
        cells.remove(BlockPos.asLong(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        return cells.containsKey(BlockPos.asLong(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    public void eraseBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    erase(x, y, z);
                }
            }
        }
    }

    /** The four side walls of a box (no floor, no ceiling). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        fill(x1, y1, z1, x2, y2, z1, state);
        fill(x1, y1, z2, x2, y2, z2, state);
        fill(x1, y1, z1, x1, y2, z2, state);
        fill(x2, y1, z1, x2, y2, z2, state);
    }

    /** Solid vertical cylinder (centre on block centres when cx, cz end in .5). */
    public void cyl(double cx, double cz, double r, int y1, int y2, String state) {
        tube(cx, cz, r, -1, y1, y2, state);
    }

    /** Ring between radius rIn (exclusive) and rOut (inclusive). */
    public void tube(double cx, double cz, double rOut, double rIn, int y1, int y2, String state) {
        int r = (int) Math.ceil(rOut) + 1;
        for (int x = (int) Math.floor(cx - r); x <= cx + r; x++) {
            for (int z = (int) Math.floor(cz - r); z <= cz + r; z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d <= rOut && d > rIn) {
                    for (int y = y1; y <= y2; y++) {
                        set(x, y, z, state);
                    }
                }
            }
        }
    }

    /** Solid ellipsoid; with shell > 0 only the outer shell of that thickness. */
    public void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double shell, String state) {
        for (int x = (int) Math.floor(cx - rx - 1); x <= cx + rx + 1; x++) {
            for (int y = (int) Math.floor(cy - ry - 1); y <= cy + ry + 1; y++) {
                for (int z = (int) Math.floor(cz - rz - 1); z <= cz + rz + 1; z++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d > 1) {
                        continue;
                    }
                    if (shell > 0) {
                        double ix = (x + 0.5 - cx) / Math.max(0.1, rx - shell), iy = (y + 0.5 - cy) / Math.max(0.1, ry - shell),
                            iz = (z + 0.5 - cz) / Math.max(0.1, rz - shell);
                        if (ix * ix + iy * iy + iz * iz <= 1) {
                            continue;
                        }
                    }
                    set(x, y, z, state);
                }
            }
        }
    }

    /** A straight line of blocks (3D). */
    public void line(double x1, double y1, double z1, double x2, double y2, double z2, String state) {
        int n = (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1))) * 2) + 1;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            set((int) Math.floor(x1 + (x2 - x1) * t), (int) Math.floor(y1 + (y2 - y1) * t), (int) Math.floor(z1 + (z2 - z1) * t), state);
        }
    }

    /** Hands everything to the arena (placed over the next ticks). Returns the number of blocks queued. */
    public int commit() {
        long started = System.nanoTime();
        List<Map.Entry<Long, BlockState>> list = new ArrayList<>(cells.entrySet());
        // Bottom up, so whatever hangs or stands on something is placed after its support.
        list.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
        int n = 0;
        for (Map.Entry<Long, BlockState> e : list) {
            int x = BlockPos.getX(e.getKey()), y = BlockPos.getY(e.getKey()), z = BlockPos.getZ(e.getKey());
            if (kept(x, y, z)) {
                continue;
            }
            arena.setLater(x, y, z, e.getValue());
            n++;
        }
        LOGGER.info("Decoration: {} blocks queued in {} ms", n, (System.nanoTime() - started) / 1_000_000);
        cells.clear();
        return n;
    }
}
