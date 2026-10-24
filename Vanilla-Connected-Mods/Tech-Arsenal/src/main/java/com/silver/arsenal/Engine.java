package com.silver.arsenal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Runs the arsenal's multi-tick tasks and holds the small amounts of per-player state weapons need between hits. */
final class Engine {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> INCOMING = new ArrayList<>();
    private static final Map<String, Integer> COUNTERS = new HashMap<>();
    private static final Map<UUID, Long> REV_UNTIL = new HashMap<>();
    private static long now;

    private Engine() {
    }

    static void add(Task task) {
        INCOMING.add(task);
    }

    static long tickCount() {
        return now;
    }

    static void tick(MinecraftServer server) {
        now = server.getTickCount();
        TASKS.addAll(INCOMING);
        INCOMING.clear();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
            Task task = it.next();
            boolean done;
            try {
                done = task.tick(server);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger("tech-arsenal").error("ALERT: a {} task failed and was dropped", task.getClass().getSimpleName(), failure);
                task.cancel();
                done = true;
            }
            if (done) {
                it.remove();
            }
        }
    }

    static void shutdown() {
        for (Task task : TASKS) {
            task.cancel();
        }
        for (Task task : INCOMING) {
            task.cancel();
        }
        TASKS.clear();
        INCOMING.clear();
        COUNTERS.clear();
        REV_UNTIL.clear();
    }

    /** Counts uses of something per player and returns the new count; resets to 0 when it reaches {@code every}. */
    static boolean everyNth(UUID player, String key, int every) {
        String full = player + ":" + key;
        int count = COUNTERS.merge(full, 1, Integer::sum);
        if (count >= every) {
            COUNTERS.remove(full);
            return true;
        }
        return false;
    }

    static void startRev(UUID player, long untilTick) {
        REV_UNTIL.put(player, untilTick);
    }

    static boolean revving(UUID player) {
        Long until = REV_UNTIL.get(player);
        if (until != null && now >= until) {
            REV_UNTIL.remove(player);
            return false;
        }
        return until != null;
    }
}
