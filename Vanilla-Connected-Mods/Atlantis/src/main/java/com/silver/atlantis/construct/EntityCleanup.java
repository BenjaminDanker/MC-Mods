package com.silver.atlantis.construct;

import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public final class EntityCleanup {

    private EntityCleanup() {
    }

    public static Box cleanupBox(ServerWorld world, BlockPos center, BlockVector3 overallMinOrNull, BlockVector3 overallMaxOrNull, int marginBlocks) {
        // Cleanup should be X/Z based (like player exclusion), and span full world height.
        // Mobs can be above/below the schematic Y range or drift a bit.
        int extra = Math.max(0, marginBlocks);
        int fallbackHalfSize = 255 + extra;
        int minX = center.getX() - fallbackHalfSize;
        int maxX = center.getX() + fallbackHalfSize;
        int minZ = center.getZ() - fallbackHalfSize;
        int maxZ = center.getZ() + fallbackHalfSize;

        if (overallMinOrNull != null && overallMaxOrNull != null) {
            minX = Math.min(minX, overallMinOrNull.x() - extra);
            maxX = Math.max(maxX, overallMaxOrNull.x() + extra);
            minZ = Math.min(minZ, overallMinOrNull.z() - extra);
            maxZ = Math.max(maxZ, overallMaxOrNull.z() + extra);
        }

        int bottomY = world.getBottomY();
        int topYExclusive = world.getTopYInclusive() + 1;

        return new Box(
            minX, bottomY, minZ,
            maxX + 1, topYExclusive, maxZ + 1
        );
    }

    public static List<? extends Entity> collectMobs(ServerWorld world, Box box) {
        return world.getEntitiesByClass(MobEntity.class, box, e -> true);
    }

    public static List<Entity> collectItemsAndXp(ServerWorld world, Box box) {
        List<Entity> all = new ArrayList<>();
        all.addAll(world.getEntitiesByClass(ItemEntity.class, box, e -> true));
        all.addAll(world.getEntitiesByClass(ExperienceOrbEntity.class, box, e -> true));
        return all;
    }

    public static int discardSome(List<? extends Entity> entities, int startIndex, int maxPerTick) {
        if (entities == null) {
            return startIndex;
        }

        int processed = 0;
        int index = startIndex;
        while (index < entities.size() && processed < maxPerTick) {
            Entity entity = entities.get(index);
            index++;
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            if (entity instanceof ServerPlayerEntity) {
                continue;
            }
            entity.discard();
            processed++;
        }

        return index;
    }
}
