package com.silver.atlantis.worldgen;

import com.silver.atlantis.AtlantisMod;
import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.concurrent.atomic.AtomicLong;

public final class AtlantisDriftwoodFeature extends Feature<DefaultFeatureConfig> {
    private static final AtomicLong GENERATE_CALLS = new AtomicLong();
    private static final AtomicLong CHUNKS_WITH_PLACEMENT = new AtomicLong();
    private static final AtomicLong PLACED_BLOCKS_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_DEPTH_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_WATER_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_BAD_FLOOR_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_REPLACEABLE_TOTAL = new AtomicLong();
    private static final AtomicLong REJECT_NO_COLUMN_MATCH_TOTAL = new AtomicLong();

    public AtlantisDriftwoodFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos origin = context.getOrigin();
        long calls = GENERATE_CALLS.incrementAndGet();

        boolean placedAny = false;
        int placedBlocksThisCall = 0;
        int attempts = 1;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable floorPos = new BlockPos.Mutable();
        int rejectedNoDepth = 0;
        int rejectedNoWater = 0;
        int rejectedBadFloor = 0;
        int rejectedNoReplaceable = 0;
        int rejectedNoColumnMatch = 0;

        for (int i = 0; i < attempts; i++) {
            int x = origin.getX() + random.nextBetween(-3, 3);
            int z = origin.getZ() + random.nextBetween(-3, 3);

            int floorY = findOceanFloorY(world, x, z);
            if (floorY == Integer.MIN_VALUE) {
                rejectedNoColumnMatch++;
                continue;
            }

            pos.set(x, floorY + 1, z);
            BlockState waterState = world.getBlockState(pos);
            if (!waterState.getFluidState().isOf(Fluids.WATER)) {
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
            BlockState log = Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);

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

                world.setBlockState(pos, log, Block.NOTIFY_LISTENERS);
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
        if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
            return true;
        }

        return state.isOf(Blocks.SAND)
            || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.STONE)
            || state.isOf(Blocks.CLAY)
            || state.isOf(Blocks.DIRT)
            || state.isOf(Blocks.MUD)
            || state.isOf(Blocks.DEEPSLATE);
    }

    private static boolean canReplace(BlockState state) {
        return state.getFluidState().isOf(Fluids.WATER)
            || state.isAir()
            || state.isIn(BlockTags.REPLACEABLE_BY_TREES)
            || state.isReplaceable();
    }

    private static int findOceanFloorY(StructureWorldAccess world, int x, int z) {
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        int seaLevel = world.toServerWorld().getSeaLevel();
        int startY = Math.max(world.getBottomY() + 2, Math.max(seaLevel + 24, topY));
        int minY = world.getBottomY() + 1;

        BlockPos.Mutable above = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();

        for (int y = startY; y >= minY; y--) {
            above.set(x, y, z);
            below.set(x, y - 1, z);

            BlockState aboveState = world.getBlockState(above);
            if (!aboveState.getFluidState().isOf(Fluids.WATER)) {
                continue;
            }

            BlockState belowState = world.getBlockState(below);
            if (!belowState.getFluidState().isOf(Fluids.WATER) && isValidOceanFloor(belowState)) {
                return y - 1;
            }
        }

        return Integer.MIN_VALUE;
    }
}
