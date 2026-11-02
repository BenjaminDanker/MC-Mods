package com.silver.asyncsave;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The write queue: one background thread writes every player file in the order the saves were taken, so a newer save
 * of a file is never overtaken by an older one. Before a player's files are read, their pending writes are waited for.
 */
public final class AsyncSaves {
    static final Logger LOGGER = LoggerFactory.getLogger("asyncsave");
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "async-player-save");
        thread.setDaemon(false); // a shutdown waits for it (see drain)
        return thread;
    });
    /** Player -> their latest queued write (later writes queue behind earlier ones on the one thread). */
    private static final Map<UUID, CompletableFuture<Void>> LATEST = new ConcurrentHashMap<>();

    public interface Write {
        void run() throws Exception;
    }

    private AsyncSaves() {
    }

    public static void submit(UUID player, String what, Write write) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                write.run();
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not save {} for {}", what, player, failure);
            }
        }, WRITER);
        LATEST.put(player, future);
        future.whenComplete((ok, failure) -> LATEST.remove(player, future));
    }

    /** Waits (on the calling thread) until this player's queued writes are on disk. Usually instant. */
    public static void awaitPlayer(UUID player) {
        CompletableFuture<Void> pending = LATEST.get(player);
        if (pending == null) {
            return;
        }
        try {
            pending.get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            LOGGER.error("ALERT: waited too long for {}'s files to be written", player, failure);
        }
    }

    /** At shutdown: everything queued is written before the server exits. */
    static void drain() {
        WRITER.shutdown();
        try {
            if (!WRITER.awaitTermination(60, TimeUnit.SECONDS)) {
                LOGGER.error("ALERT: player saves still pending after 60 s at shutdown");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
