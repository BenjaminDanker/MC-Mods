package com.silver.viewextend;

import java.util.function.ToIntFunction;

/** One-use exploration has a small, short-lived probation; reuse earns protected retention. */
final class ReuseAwareChunkCache<K, V> {
    private final VisualChunkCache<K,V> probation, reused;
    private long promotions;
    private long admissions;
    private long probationAdmissions;
    private long protectedAdmissions;
    private long hits;
    ReuseAwareChunkCache(int entries, long bytes, int ttl, ToIntFunction<V> weigh) {
        long coldBytes = bytes / 8;
        int coldEntries = entries / 8;
        probation = new VisualChunkCache<>(coldEntries, coldBytes, Math.min(ttl, 100), weigh);
        reused = new VisualChunkCache<>(entries - coldEntries, bytes - coldBytes, ttl, weigh);
    }
    V get(K key, long tick) {
        V value = reused.get(key, tick);
        if (value != null) { hits++; return value; }
        value = probation.get(key, tick);
        if (value != null) {
            hits++;
            probation.invalidate(key);
            reused.put(key, value, tick, true);
            promotions++;
        }
        return value;
    }
    void put(K key, V value, long tick, boolean shared) {
        invalidate(key);
        boolean admitted = shared ? reused.put(key, value, tick, true) : probation.put(key, value, tick);
        if (admitted) {
            admissions++;
            if (shared) protectedAdmissions++; else probationAdmissions++;
        }
    }
    void invalidate(K key) { probation.invalidate(key); reused.invalidate(key); }
    void expire(long tick) { probation.expire(tick); reused.expire(tick); }
    void clear() { probation.clear(); reused.clear(); }
    int size() { return probation.size() + reused.size(); }
    long bytes() { return probation.bytes() + reused.bytes(); }
    boolean containsIdentity(K key, V value) {
        return probation.containsIdentity(key, value) || reused.containsIdentity(key, value);
    }
    CacheStats drainStats() {
        VisualChunkCache.EventStats events = probation.drainEventStats()
                .plus(reused.drainEventStats());
        CacheStats stats = new CacheStats(admissions, probationAdmissions, protectedAdmissions,
                hits, promotions, events.expirations(), events.entryLimitEvictions(),
                events.byteLimitEvictions(), events.expiredWithoutReuse());
        admissions = 0;
        probationAdmissions = 0;
        protectedAdmissions = 0;
        hits = 0;
        promotions = 0;
        return stats;
    }
    record CacheStats(long admissions, long probationAdmissions, long protectedAdmissions,
            long hits, long promotions, long expirations, long entryLimitEvictions,
            long byteLimitEvictions, long expiredWithoutReuse) {}
}
