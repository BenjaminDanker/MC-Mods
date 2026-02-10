package com.silver.atlantis.find;

import net.minecraft.block.BlockState;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.noise.NoiseConfig;

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
    private final HeightLimitView heightLimitView;
    private final NoiseConfig noiseConfig;
    private final int scanStartYInclusive;

    private WorldgenSurfaceHeightSampler(
        ChunkGenerator generator,
        HeightLimitView heightLimitView,
        NoiseConfig noiseConfig,
        int scanStartYInclusive
    ) {
        this.generator = generator;
        this.heightLimitView = heightLimitView;
        this.noiseConfig = noiseConfig;
        this.scanStartYInclusive = scanStartYInclusive;
    }

    public static WorldgenSurfaceHeightSampler forWorld(ServerWorld world) {
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        HeightLimitView heightLimitView = world;
        NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
        int scanStartYInclusive = Math.min(START_SCAN_Y_INCLUSIVE, heightLimitView.getTopYInclusive());
        return new WorldgenSurfaceHeightSampler(generator, heightLimitView, noiseConfig, scanStartYInclusive);
    }

    @Override
    public int sampleSurfaceY(int x, int z) {
        // Prefer a deterministic scan so roof bedrock can never be treated as surface.
        // Scan downward from Y=317 (or world top if lower) to find the first solid, non-fluid block.
        VerticalBlockSample column = generator.getColumnSample(x, z, heightLimitView, noiseConfig);
        int bottomY = heightLimitView.getBottomY();

        for (int y = scanStartYInclusive; y >= bottomY; y--) {
            BlockState state = column.getState(y);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            if (!state.blocksMovement()) {
                continue;
            }
            return y + 1;
        }

        // Fallback.
        return bottomY;
    }
}
