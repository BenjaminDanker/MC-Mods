package com.silver.viewextend;

import java.util.LinkedHashMap;

/** Bounded, server-thread cooldowns shared across players and LODs. Reads never extend a cooldown. */
final class ChunkRetryCache<K> {
    private final LinkedHashMap<K, Long> deadlines = new LinkedHashMap<>();
    private final int capacity;
    private record Failure(Object reason, int delay) {}
    private final LinkedHashMap<K, Failure> failures = new LinkedHashMap<>();
    int fail(K key, long tick, Object reason, int baseDelay) {
        Failure previous = failures.remove(key);
        int delay = previous != null && previous.reason.equals(reason)
                ? Math.min(6000, previous.delay * 2) : baseDelay;
        failures.put(key, new Failure(reason, delay));
        if (failures.size() > capacity) failures.pollFirstEntry();
        put(key, tick, delay);
        return delay;
    }
    ChunkRetryCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }
    void put(K key, long tick, int delay) {
        deadlines.remove(key);
        deadlines.put(key, tick + delay);
        if (deadlines.size() > capacity) deadlines.pollFirstEntry();
    }
    int remaining(K key, long tick) {
        Long deadline = deadlines.get(key);
        if (deadline == null) return 0;
        if (deadline <= tick) { deadlines.remove(key); return 0; }
        return (int) Math.min(Integer.MAX_VALUE, deadline - tick);
    }
    void invalidate(K key) { deadlines.remove(key); failures.remove(key); }
    void clear() { deadlines.clear(); failures.clear(); }
}
