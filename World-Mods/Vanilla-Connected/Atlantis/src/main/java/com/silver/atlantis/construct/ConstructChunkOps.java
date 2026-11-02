package com.silver.atlantis.construct;

import com.silver.atlantis.construct.mixin.ServerChunkManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Chunk helpers shared by the build ({@link ConstructTask}) and the undo ({@link ConstructUndoTask}). Server thread. */
final class ConstructChunkOps {
    /**
     * The tickets that can keep a target chunk loaded once players are moved out: the generation ticket that
     * world.getChunk adds (UNKNOWN) and forced tickets (setChunkForced, and the Leviathan preloader at radius 2).
     * Other mods' and players' tickets are left alone.
     */
    private static final List<TicketType> RELEASED_TICKET_TYPES = List.of(TicketType.UNKNOWN, TicketType.FORCED);
    private static final int MAX_TICKET_RADIUS = 4;

    private ConstructChunkOps() {
    }

    /** Drops this mod's hold on a chunk so the server can unload it. */
    static void releaseChunk(ServerLevel world, long key) {
        int chunkX = ChunkPos.getX(key);
        int chunkZ = ChunkPos.getZ(key);
        world.setChunkForced(chunkX, chunkZ, false);
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        for (TicketType type : RELEASED_TICKET_TYPES) {
            for (int radius = 0; radius <= MAX_TICKET_RADIUS; radius++) {
                world.getChunkSource().removeTicketWithRadius(type, pos, radius);
            }
        }
    }

    static int countLoaded(ServerLevel world, Set<Long> chunkKeys) {
        if (world == null || chunkKeys == null || chunkKeys.isEmpty()) {
            return 0;
        }
        int loaded = 0;
        for (long key : chunkKeys) {
            if (world.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) {
                loaded++;
            }
        }
        return loaded;
    }

    /** IO thread: chunks with no saved data on disk. */
    static List<ChunkPos> findMissingChunkNbts(ServerLevel world, Set<Long> chunkKeys) {
        List<ChunkPos> missing = new ArrayList<>();
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return missing;
        }
        for (long key : chunkKeys) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (!UnloadedChunkNbtEditor.hasChunkNbt(world, pos)) {
                missing.add(pos);
            }
        }
        return missing;
    }

    /** Un-forces every chunk in the block bounds; returns {total, loadedBefore}. */
    static int[] unforceChunksInBounds(ServerLevel world, int minX, int minZ, int maxX, int maxZ) {
        int total = 0;
        int loadedBefore = 0;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                total++;
                if (world.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    loadedBefore++;
                }
                world.setChunkForced(chunkX, chunkZ, false);
            }
        }
        return new int[] {total, loadedBefore};
    }

    /** Asks the server to save loaded target chunks now; returns how many were loaded. */
    static int markLoadedNeedSaving(ServerLevel world, Set<Long> chunkKeys) {
        if (world == null || chunkKeys == null || chunkKeys.isEmpty()) {
            return 0;
        }
        int saved = 0;
        for (long key : chunkKeys) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (world.getChunkSource().hasChunk(chunkX, chunkZ)) {
                ((ServerChunkManagerAccessor) world.getChunkSource().chunkMap).atlantis$setChunkUnsaved(new ChunkPos(chunkX, chunkZ));
                saved++;
            }
        }
        return saved;
    }

    static Set<Long> merge(Set<Long> first, Set<Long> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Set.of();
        }
        if (first == null || first.isEmpty()) {
            return Set.copyOf(second);
        }
        if (second == null || second.isEmpty()) {
            return Set.copyOf(first);
        }
        Set<Long> merged = new HashSet<>(first);
        merged.addAll(second);
        return Set.copyOf(merged);
    }

    static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.isBlank()) ? root.getClass().getSimpleName() : msg;
    }

    /** Walks the chunks to unload a few per tick, round and round, until they are all unloaded. */
    static final class UnloadCursor {
        private Set<Long> keys = Set.of();
        private long[] order = new long[0];
        private int cursor;

        void set(Set<Long> chunkKeys) {
            keys = chunkKeys == null || chunkKeys.isEmpty() ? Set.of() : Set.copyOf(chunkKeys);
            order = keys.stream().mapToLong(Long::longValue).toArray();
            cursor = 0;
        }

        Set<Long> keys() {
            return keys;
        }

        void releaseSome(ServerLevel world, Set<Long> fallback, int budget) {
            if (order.length == 0) {
                set(fallback);
            }
            if (world == null || order.length == 0) {
                return;
            }
            for (int i = 0; i < Math.min(budget, order.length); i++) {
                if (cursor < 0) {
                    cursor = 0;
                }
                releaseChunk(world, order[cursor++ % order.length]);
            }
        }
    }
}
