package com.silver.aipets.fabric.placement;

import com.silver.aipets.common.domain.WorldPosition;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.Set;

/** Conservative loaded-chunk-only search for a non-lava, supported, collision-free spawn position. */
public final class SafePlacementFinder {
    private static final Set<Block> HAZARDOUS_BLOCKS = Set.of(
            Blocks.CACTUS,
            Blocks.CAMPFIRE,
            Blocks.FIRE,
            Blocks.LAVA,
            Blocks.MAGMA_BLOCK,
            Blocks.POWDER_SNOW,
            Blocks.SOUL_CAMPFIRE,
            Blocks.SOUL_FIRE);

    private final int horizontalRadius;
    private final int verticalRadius;

    public SafePlacementFinder(int horizontalRadius, int verticalRadius) {
        if (horizontalRadius < 0 || horizontalRadius > 16) {
            throw new IllegalArgumentException("horizontalRadius must be in 0..16");
        }
        if (verticalRadius < 0 || verticalRadius > 8) {
            throw new IllegalArgumentException("verticalRadius must be in 0..8");
        }
        this.horizontalRadius = horizontalRadius;
        this.verticalRadius = verticalRadius;
    }

    public Optional<WorldPosition> find(
            ServerWorld world,
            LivingEntity prototype,
            BlockPos origin) {
        java.util.Objects.requireNonNull(world, "world");
        java.util.Objects.requireNonNull(prototype, "prototype");
        java.util.Objects.requireNonNull(origin, "origin");

        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int verticalOffset : verticalOffsets()) {
                        BlockPos candidate = origin.add(dx, verticalOffset, dz);
                        if (isSafe(world, prototype, candidate)) {
                            Vec3d center = Vec3d.ofBottomCenter(candidate);
                            return Optional.of(new WorldPosition(center.x, center.y, center.z));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isSafe(ServerWorld world, LivingEntity prototype, BlockPos feet) {
        BlockPos support = feet.down();
        if (world.isOutOfHeightLimit(feet)
                || world.isOutOfHeightLimit(support)
                || !world.getChunkManager().isChunkLoaded(
                        Math.floorDiv(feet.getX(), 16),
                        Math.floorDiv(feet.getZ(), 16))
                || !world.getWorldBorder().contains(feet)) {
            return false;
        }

        Vec3d center = Vec3d.ofBottomCenter(feet);
        Box body = prototype.getDimensions(prototype.getPose()).getBoxAt(center);
        if (!world.getWorldBorder().contains(body) || !allIntersectingChunksLoaded(world, body)) {
            return false;
        }

        BlockState feetState = world.getBlockState(feet);
        BlockState supportState = world.getBlockState(support);
        if (isHazard(feetState)
                || isHazard(supportState)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(support).isIn(FluidTags.LAVA)
                || !supportState.isSideSolidFullSquare(world, support, Direction.UP)) {
            return false;
        }

        if (!world.isSpaceEmpty(prototype, body)) {
            return false;
        }
        return world.getOtherEntities(
                        prototype,
                        body.expand(0.05),
                        SafePlacementFinder::blocksPlacement)
                .isEmpty();
    }

    private int[] verticalOffsets() {
        int[] offsets = new int[verticalRadius * 2 + 1];
        offsets[0] = 0;
        for (int distance = 1; distance <= verticalRadius; distance++) {
            offsets[distance * 2 - 1] = distance;
            offsets[distance * 2] = -distance;
        }
        return offsets;
    }

    private static boolean allIntersectingChunksLoaded(ServerWorld world, Box box) {
        int minimumChunkX = Math.floorDiv(MathHelper.floor(box.minX), 16);
        int maximumChunkX = Math.floorDiv(MathHelper.floor(Math.nextDown(box.maxX)), 16);
        int minimumChunkZ = Math.floorDiv(MathHelper.floor(box.minZ), 16);
        int maximumChunkZ = Math.floorDiv(MathHelper.floor(Math.nextDown(box.maxZ)), 16);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        return HAZARDOUS_BLOCKS.contains(state.getBlock());
    }

    private static boolean blocksPlacement(Entity entity) {
        return entity.isAlive() && !entity.isSpectator();
    }
}
