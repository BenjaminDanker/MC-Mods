package com.silver.atlantis.find;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Samples height using the world generator (no chunk loading).
 *
 * Uses the OCEAN_FLOOR_WG heightmap type to avoid trees/features and to measure
 * the ground height under fluids (instead of the water surface height).
 */
public final class WorldgenSurfaceHeightSampler implements SurfaceHeightSampler {

    // Atlantis uses a roofed-ocean world. Always start height sampling below the roof.
    private static final int START_SCAN_Y_INCLUSIVE = 317;

    private final ChunkGenerator generator;
    private final LevelHeightAccessor heightLimitView;
    private final RandomState noiseConfig;
    private final int scanStartYInclusive;

    private WorldgenSurfaceHeightSampler(
        ChunkGenerator generator,
        LevelHeightAccessor heightLimitView,
        RandomState noiseConfig,
        int scanStartYInclusive
    ) {
        this.generator = generator;
        this.heightLimitView = heightLimitView;
        this.noiseConfig = noiseConfig;
        this.scanStartYInclusive = scanStartYInclusive;
    }

    public static WorldgenSurfaceHeightSampler forWorld(ServerLevel world) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        LevelHeightAccessor heightLimitView = world;
        RandomState noiseConfig = world.getChunkSource().randomState();
        int scanStartYInclusive = Math.min(START_SCAN_Y_INCLUSIVE, heightLimitView.getMaxY());
        return new WorldgenSurfaceHeightSampler(generator, heightLimitView, noiseConfig, scanStartYInclusive);
    }

    @Override
    public int sampleSurfaceY(int x, int z) {
        // Prefer a deterministic scan so roof bedrock can never be treated as surface.
        // Scan downward from Y=317 (or world top if lower) to find the first solid, non-fluid block.
        NoiseColumn column = generator.getBaseColumn(x, z, heightLimitView, noiseConfig);
        int bottomY = heightLimitView.getMinY();

        for (int y = scanStartYInclusive; y >= bottomY; y--) {
            BlockState state = column.getBlock(y);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.blocksMotion()) {
                continue;
            }
            return y + 1;
        }

        // Fallback.
        return bottomY;
    }
}
