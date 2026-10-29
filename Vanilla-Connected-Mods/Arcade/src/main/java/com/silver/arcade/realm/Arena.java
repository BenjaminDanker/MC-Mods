package com.silver.arcade.realm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A slot in the void world where one room is built: slots sit 512 blocks apart along +x at y=100, far enough that
 * one room never shows in another's view. Every block a room places is remembered and cleared when the slot is freed.
 */
public final class Arena {
    public static final int SPACING = 512;
    public static final int BASE_Y = 100;
    private static final BitSet USED = new BitSet();

    private final ServerLevel level;
    private final int slot;
    private final BlockPos origin;
    private final Deque<BlockPos> placed = new ArrayDeque<>();
    private final List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();

    private Arena(ServerLevel level, int slot) {
        this.level = level;
        this.slot = slot;
        this.origin = new BlockPos(SPACING * (slot + 1), BASE_Y, 0);
    }

    public static synchronized Arena take(ServerLevel level) {
        int slot = USED.nextClearBit(0);
        USED.set(slot);
        return new Arena(level, slot);
    }

    public ServerLevel level() {
        return level;
    }

    /** The arena's corner; rooms build at origin + offsets. */
    public BlockPos origin() {
        return origin;
    }

    public BlockPos at(int dx, int dy, int dz) {
        return origin.offset(dx, dy, dz);
    }

    public void set(int dx, int dy, int dz, BlockState state) {
        BlockPos pos = at(dx, dy, dz);
        level.setBlock(pos, state, 2);
        placed.push(pos.immutable());
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Adds an entity the room made (a mob, a target); it is removed with the arena. */
    public <T extends net.minecraft.world.entity.Entity> T spawn(T entity, double dx, double dy, double dz) {
        entity.snapTo(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, 0F, 0F);
        entity.addTag("arcade_room");
        level.addFreshEntity(entity);
        spawned.add(entity);
        return entity;
    }

    /** Removes one block a room placed (it stays remembered; clearing it again later is harmless). */
    public void clearBlock(int dx, int dy, int dz) {
        level.setBlock(at(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
    }

    /** True when pos lies in this arena's slot. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX() - SPACING / 2 && pos.getX() < origin.getX() + SPACING / 2;
    }

    public void put(ServerPlayer player, double dx, double dy, double dz, float yaw) {
        player.teleportTo(level, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, Set.of(), yaw, 0F, false);
        player.setDeltaMovement(0, 0, 0);
        player.resetFallDistance();
    }

    /** Clears everything the room placed and frees the slot. */
    public void release() {
        spawned.forEach(net.minecraft.world.entity.Entity::discard);
        spawned.clear();
        // Arrows and drops left lying around in the slot go too.
        level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(origin.getX() - SPACING / 2.0, BASE_Y - 64,
                -SPACING / 2.0, origin.getX() + SPACING / 2.0, BASE_Y + 64, SPACING / 2.0),
            e -> e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity
                || e.entityTags().contains("arcade_room"))
            .forEach(net.minecraft.world.entity.Entity::discard);
        List<BlockPos> order = new ArrayList<>(placed);
        for (BlockPos pos : order) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        placed.clear();
        synchronized (Arena.class) {
            USED.clear(slot);
        }
    }
}
