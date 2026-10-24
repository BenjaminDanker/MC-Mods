package com.silver.atlantis.construct;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Answers "is this chunk loaded?" for the IO thread without touching the server's chunk map off-thread: the server
 * thread is asked in batches (the next {@value #BATCH} chunks of the plan at once) and the answer is reused for up to
 * {@value #MAX_AGE_MILLIS} ms. One round trip per batch keeps an apply over thousands of chunks from waiting a tick per
 * chunk, and the check itself is a hash lookup.
 */
final class LoadedChunkProbe {
    private static final int BATCH = 64;
    private static final long MAX_AGE_MILLIS = 100L;

    private final ServerLevel world;
    private final long[] plan;
    private int planCursor;
    private LongSet answered = new LongOpenHashSet();
    private LongSet loaded = new LongOpenHashSet();
    private long answeredAtMillis;

    LoadedChunkProbe(ServerLevel world, List<Long> chunkKeysInOrder) {
        this.world = world;
        this.plan = chunkKeysInOrder.stream().mapToLong(Long::longValue).toArray();
    }

    boolean isLoaded(ChunkPos pos) {
        long key = ChunkPos.pack(pos.x(), pos.z());
        long now = System.currentTimeMillis();
        if (!answered.contains(key) || now - answeredAtMillis > MAX_AGE_MILLIS) {
            refresh(key);
        }
        return loaded.contains(key);
    }

    private void refresh(long wanted) {
        LongArrayList keys = new LongArrayList();
        keys.add(wanted);
        while (planCursor < plan.length && plan[planCursor] != wanted) {
            planCursor++;
        }
        for (int i = planCursor; i < plan.length && keys.size() < BATCH; i++) {
            if (plan[i] != wanted) {
                keys.add(plan[i]);
            }
        }
        long[] query = keys.toLongArray();
        LongSet loadedNow = world.getServer().submit(() -> {
            LongSet result = new LongOpenHashSet();
            for (long key : query) {
                if (world.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) {
                    result.add(key);
                }
            }
            return result;
        }).orTimeout(30, TimeUnit.SECONDS).join();
        answered = new LongOpenHashSet(query);
        loaded = loadedNow;
        answeredAtMillis = System.currentTimeMillis();
    }
}
