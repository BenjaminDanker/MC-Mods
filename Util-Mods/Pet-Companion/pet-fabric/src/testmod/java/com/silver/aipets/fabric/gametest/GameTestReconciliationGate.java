package com.silver.aipets.fabric.gametest;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the shared GameTest world's unrelated fixture pets out of the global authority mock. */
public final class GameTestReconciliationGate {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> PERIODIC_ENABLED = ThreadLocal.withInitial(() -> false);

    private GameTestReconciliationGate() { }

    public static Runnable enable(UUID petId) {
        ENABLED.add(petId);
        return () -> ENABLED.remove(petId);
    }

    public static boolean enabled(UUID petId) {
        return ENABLED.contains(petId);
    }

    public static Runnable enablePeriodic() {
        boolean previous = PERIODIC_ENABLED.get();
        PERIODIC_ENABLED.set(true);
        return () -> PERIODIC_ENABLED.set(previous);
    }

    public static boolean periodicEnabled() {
        return PERIODIC_ENABLED.get();
    }
}
