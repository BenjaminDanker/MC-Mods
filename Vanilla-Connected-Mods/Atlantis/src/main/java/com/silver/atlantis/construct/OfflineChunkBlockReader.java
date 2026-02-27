package com.silver.atlantis.construct;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Optional;

/**
 * Lightweight read-only helper to query block strings from unloaded chunk NBT.
 */
public final class OfflineChunkBlockReader {

    private OfflineChunkBlockReader() {
    }

    public static Optional<ChunkSnapshot> load(ServerWorld world, ChunkPos chunkPos) {
        if (world == null || chunkPos == null) {
            return Optional.empty();
        }

        Optional<NbtCompound> chunkNbt = UnloadedChunkNbtEditor.loadChunkNbt(world, chunkPos);
        if (chunkNbt.isEmpty()) {
            return Optional.empty();
        }

        UnloadedChunkNbtEditor.ChunkMutationContext context = new UnloadedChunkNbtEditor.ChunkMutationContext(chunkPos, chunkNbt.get());
        return Optional.of(new ChunkSnapshot(chunkPos, context));
    }

    public static final class ChunkSnapshot {
        private final ChunkPos chunkPos;
        private final UnloadedChunkNbtEditor.ChunkMutationContext context;

        private ChunkSnapshot(ChunkPos chunkPos, UnloadedChunkNbtEditor.ChunkMutationContext context) {
            this.chunkPos = chunkPos;
            this.context = context;
        }

        public ChunkPos chunkPos() {
            return chunkPos;
        }

        public String blockStringAt(BlockPos pos) {
            if (pos == null) {
                return "minecraft:air";
            }
            return blockStringAt(pos.getX(), pos.getY(), pos.getZ());
        }

        public String blockStringAt(int x, int y, int z) {
            return context.getSnapshot(x, y, z).blockString();
        }
    }
}
