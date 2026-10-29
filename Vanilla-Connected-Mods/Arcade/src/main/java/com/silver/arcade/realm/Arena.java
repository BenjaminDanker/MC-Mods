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
 * The list is also kept on disk (world/arcade-arenas/slot-N.bin) until the slot is cleared, so whatever a crash or a
 * stop left behind is cleared on the next start.
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
    private boolean ledgerDirty;
    private static java.nio.file.Path ledgers;
    /** Set while the server stops: arenas then keep their ledgers instead of clearing. */
    public static volatile boolean stopping;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("arcade");

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
        ledgerDirty = true;
    }

    private java.nio.file.Path ledger() {
        return ledgers == null ? null : ledgers.resolve("slot-" + slot + ".bin");
    }

    /** Writes the list of placed blocks to disk if it changed (called every few seconds by the run). */
    public void flushLedger() {
        java.nio.file.Path file = ledger();
        if (!ledgerDirty || file == null) {
            return;
        }
        ledgerDirty = false;
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(placed.size() * Long.BYTES);
        placed.forEach(pos -> buffer.putLong(pos.asLong()));
        try {
            java.nio.file.Files.createDirectories(ledgers);
            java.nio.file.Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            java.nio.file.Files.write(temporary, buffer.array());
            java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not write the arena ledger {}", file, failure);
        }
    }

    /** At start: clears every block a room left behind last time (from the ledgers), then forgets the ledgers. */
    public static void clearLeftovers(ServerLevel level, java.nio.file.Path dir) {
        ledgers = dir;
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (var files = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path file : files.toList()) {
                if (!file.getFileName().toString().endsWith(".bin")) {
                    java.nio.file.Files.deleteIfExists(file);
                    continue;
                }
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(file));
                int cleared = 0;
                while (buffer.remaining() >= Long.BYTES) {
                    BlockPos pos = BlockPos.of(buffer.getLong());
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                        cleared++;
                    }
                }
                java.nio.file.Files.delete(file);
                LOGGER.info("Cleared {} blocks a room left behind ({})", cleared, file.getFileName());
            }
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not clear the arenas' leftovers", failure);
        }
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
        if (stopping) {
            // The ledger stays on disk; the next start clears this arena.
            ledgerDirty = true;
            flushLedger();
            return;
        }
        List<BlockPos> order = new ArrayList<>(placed);
        for (BlockPos pos : order) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        placed.clear();
        try {
            if (ledger() != null) {
                java.nio.file.Files.deleteIfExists(ledger());
            }
        } catch (java.io.IOException failure) {
            LOGGER.warn("Could not remove the arena ledger {}", ledger(), failure);
        }
        synchronized (Arena.class) {
            USED.clear(slot);
        }
    }
}
