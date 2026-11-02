package com.silver.magitech;

import java.util.ArrayDeque;
import java.util.BitSet;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * One landmass, read from the world generator's biome noise (no chunk is loaded or generated): every cell of a coarse
 * grid that is not ocean and can be walked to from the start without crossing ocean. Rivers count as land. Safe off the
 * server thread, since it only samples noise.
 */
final class ContinentMap {
    static final int STEP = 32;
    /** How far from the start the map reaches; a landmass bigger than this is simply cut off at the edge. */
    static final int RADIUS = 4096;
    private static final int CELLS = RADIUS / STEP;
    private static final int SIDE = CELLS * 2 + 1;

    private final int originX;
    private final int originZ;
    private final BitSet land = new BitSet(SIDE * SIDE);
    private int size;

    private ContinentMap(int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
    }

    /** The landmass under (x, z), or an empty map if (x, z) is ocean. */
    static ContinentMap around(ServerLevel level, int x, int z) {
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int y = QuartPos.fromBlock(level.getSeaLevel());
        ContinentMap map = new ContinentMap(x, z);
        BitSet seen = new BitSet(SIDE * SIDE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = map.index(CELLS, CELLS);
        seen.set(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            int cx = cell % SIDE;
            int cz = cell / SIDE;
            int bx = x + (cx - CELLS) * STEP;
            int bz = z + (cz - CELLS) * STEP;
            if (biomes.getNoiseBiome(QuartPos.fromBlock(bx), y, QuartPos.fromBlock(bz), sampler).is(BiomeTags.IS_OCEAN)) {
                continue;
            }
            map.land.set(cell);
            map.size++;
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : around) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                if (nx < 0 || nz < 0 || nx >= SIDE || nz >= SIDE) {
                    continue;
                }
                int next = map.index(nx, nz);
                if (!seen.get(next)) {
                    seen.set(next);
                    queue.add(next);
                }
            }
        }
        return map;
    }

    private int index(int cx, int cz) {
        return cz * SIDE + cx;
    }

    /** Land cells found, each STEP blocks square. */
    int size() {
        return size;
    }

    boolean contains(double x, double z) {
        int cx = (int) Math.round((x - originX) / STEP) + CELLS;
        int cz = (int) Math.round((z - originZ) / STEP) + CELLS;
        return cx >= 0 && cz >= 0 && cx < SIDE && cz < SIDE && land.get(index(cx, cz));
    }

    /** Like {@link #contains}, but a point on the coast counts too: its own cell or any neighbouring cell is land. */
    boolean touches(double x, double z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (contains(x + dx * STEP, z + dz * STEP)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The land cell of this map closest to (x, z), as block coordinates {x, z}; null if the map is empty. */
    int[] nearest(int x, int z) {
        int[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int cell = land.nextSetBit(0); cell >= 0; cell = land.nextSetBit(cell + 1)) {
            int bx = originX + (cell % SIDE - CELLS) * STEP;
            int bz = originZ + (cell / SIDE - CELLS) * STEP;
            long dx = bx - x;
            long dz = bz - z;
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new int[] {bx, bz};
            }
        }
        return best;
    }
}
