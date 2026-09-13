package com.silver.aipets.fabric.placement;

import com.silver.aipets.common.domain.WorldPosition;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
            ServerLevel world,
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
                        BlockPos candidate = origin.offset(dx, verticalOffset, dz);
                        if (isSafe(world, prototype, candidate)) {
                            Vec3 center = Vec3.atBottomCenterOf(candidate);
                            return Optional.of(new WorldPosition(center.x, center.y, center.z));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isSafe(ServerLevel world, LivingEntity prototype, BlockPos feet) {
        BlockPos support = feet.below();
        if (world.isOutsideBuildHeight(feet)
                || world.isOutsideBuildHeight(support)
                || !world.getChunkSource().hasChunk(
                        Math.floorDiv(feet.getX(), 16),
                        Math.floorDiv(feet.getZ(), 16))
                || !world.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }

        Vec3 center = Vec3.atBottomCenterOf(feet);
        AABB body = prototype.getDimensions(prototype.getPose()).makeBoundingBox(center);
        if (!world.getWorldBorder().isWithinBounds(body) || !allIntersectingChunksLoaded(world, body)) {
            return false;
        }

        BlockState feetState = world.getBlockState(feet);
        BlockState supportState = world.getBlockState(support);
        if (isHazard(feetState)
                || isHazard(supportState)
                || world.getFluidState(feet).is(FluidTags.LAVA)
                || world.getFluidState(support).is(FluidTags.LAVA)
                || !supportState.isFaceSturdy(world, support, Direction.UP)) {
            return false;
        }

        if (!world.noCollision(prototype, body)) {
            return false;
        }
        return world.getEntities(
                        prototype,
                        body.inflate(0.05),
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

    private static boolean allIntersectingChunksLoaded(ServerLevel world, AABB box) {
        int minimumChunkX = Math.floorDiv(Mth.floor(box.minX), 16);
        int maximumChunkX = Math.floorDiv(Mth.floor(Math.nextDown(box.maxX)), 16);
        int minimumChunkZ = Math.floorDiv(Mth.floor(box.minZ), 16);
        int maximumChunkZ = Math.floorDiv(Mth.floor(Math.nextDown(box.maxZ)), 16);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.getChunkSource().hasChunk(chunkX, chunkZ)) {
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
