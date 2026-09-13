package com.silver.atlantis.worldgen;

import com.silver.atlantis.AtlantisMod;
import com.mojang.serialization.Codec;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

public final class AtlantisDriftwoodFeature extends Feature<NoneFeatureConfiguration> {
    private static final AtomicLong GENERATE_CALLS = new AtomicLong();
    private static final AtomicLong CHUNKS_WITH_PLACEMENT = new AtomicLong();
    private static final AtomicLong PLACED_BLOCKS_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_DEPTH_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_WATER_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_BAD_FLOOR_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_REPLACEABLE_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_COLUMN_MATCH_TOTAL = new AtomicLong();

    public AtlantisDriftwoodFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        long calls = GENERATE_CALLS.incrementAndGet();

        boolean placedAny = false;
        int placedBlocksThisCall = 0;
        int attempts = 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos floorPos = new BlockPos.MutableBlockPos();
        int rejectedNoDepth = 0;
        int rejectedNoWater = 0;
        int rejectedBadFloor = 0;
        int rejectedNoReplaceable = 0;
        int rejectedNoColumnMatch = 0;

        for (int i = 0; i < attempts; i++) {
            int x = origin.getX() + random.nextIntBetweenInclusive(-3, 3);
            int z = origin.getZ() + random.nextIntBetweenInclusive(-3, 3);

            int floorY = findOceanFloorY(world, x, z);
            if (floorY == Integer.MIN_VALUE) {
                rejectedNoColumnMatch++;
                continue;
            }

            pos.set(x, floorY + 1, z);
            BlockState waterState = world.getBlockState(pos);
            if (!waterState.getFluidState().is(Fluids.WATER)) {
                rejectedNoWater++;
                continue;
            }

            floorPos.set(x, floorY, z);
            BlockState floorState = world.getBlockState(floorPos);
            if (!isValidOceanFloor(floorState)) {
                rejectedBadFloor++;
                continue;
            }

            Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            int length = 1 + random.nextInt(2);
            int dx = axis == Direction.Axis.X ? 1 : 0;
            int dz = axis == Direction.Axis.Z ? 1 : 0;
            BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);

            for (int j = 0; j < length; j++) {
                int sx = x + dx * j;
                int sz = z + dz * j;
                int segmentFloorY = findOceanFloorY(world, sx, sz);
                if (segmentFloorY == Integer.MIN_VALUE) {
                    rejectedNoColumnMatch++;
                    continue;
                }

                pos.set(sx, segmentFloorY + 1, sz);

                BlockState existing = world.getBlockState(pos);
                if (!canReplace(existing)) {
                    rejectedNoReplaceable++;
                    continue;
                }

                world.setBlock(pos, log, Block.UPDATE_CLIENTS);
                placedAny = true;
                placedBlocksThisCall++;
            }
        }

        REJECT_NO_DEPTH_TOTAL.addAndGet(rejectedNoDepth);
        REJECT_NO_WATER_TOTAL.addAndGet(rejectedNoWater);
        REJECT_BAD_FLOOR_TOTAL.addAndGet(rejectedBadFloor);
        REJECT_NO_REPLACEABLE_TOTAL.addAndGet(rejectedNoReplaceable);
        REJECT_NO_COLUMN_MATCH_TOTAL.addAndGet(rejectedNoColumnMatch);
        if (placedBlocksThisCall > 0) {
            PLACED_BLOCKS_TOTAL.addAndGet(placedBlocksThisCall);
        }

        if (placedAny) {
            CHUNKS_WITH_PLACEMENT.incrementAndGet();
        }

        return placedAny;
    }

    private static boolean isValidOceanFloor(BlockState state) {
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return true;
        }

        return state.is(Blocks.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.STONE)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.DIRT)
            || state.is(Blocks.MUD)
            || state.is(Blocks.DEEPSLATE);
    }

    private static boolean canReplace(BlockState state) {
        return state.getFluidState().is(Fluids.WATER)
            || state.isAir()
            || state.is(BlockTags.REPLACEABLE_BY_TREES)
            || state.canBeReplaced();
    }

    private static int findOceanFloorY(WorldGenLevel world, int x, int z) {
        int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int seaLevel = world.getLevel().getSeaLevel();
        int startY = Math.max(world.getMinY() + 2, Math.max(seaLevel + 24, topY));
        int minY = world.getMinY() + 1;

        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        for (int y = startY; y >= minY; y--) {
            above.set(x, y, z);
            below.set(x, y - 1, z);

            BlockState aboveState = world.getBlockState(above);
            if (!aboveState.getFluidState().is(Fluids.WATER)) {
                continue;
            }

            BlockState belowState = world.getBlockState(below);
            if (!belowState.getFluidState().is(Fluids.WATER) && isValidOceanFloor(belowState)) {
                return y - 1;
            }
        }

        return Integer.MIN_VALUE;
    }
}
