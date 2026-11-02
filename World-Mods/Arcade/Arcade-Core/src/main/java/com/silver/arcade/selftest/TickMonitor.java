package com.silver.arcade.selftest;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Longest server tick (milliseconds of work) since the last {@link #take}. */
public final class TickMonitor {
    private static final AtomicLong MAX = new AtomicLong();
    private static final AtomicLong OVER = new AtomicLong();
    private static long started;
    private static boolean installed;

    private TickMonitor() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ServerTickEvents.START_SERVER_TICK.register(server -> started = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long ms = (System.nanoTime() - started) / 1_000_000;
            MAX.accumulateAndGet(ms, Math::max);
            if (ms > 60) {
                OVER.incrementAndGet();
            }
        });
    }

    public static void reset() {
        MAX.set(0);
        OVER.set(0);
    }

    public static long max() {
        return MAX.get();
    }

    public static long over60() {
        return OVER.get();
    }
}
