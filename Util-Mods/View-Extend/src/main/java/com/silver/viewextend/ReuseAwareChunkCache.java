package com.silver.viewextend;

import java.util.function.ToIntFunction;

/** One-use exploration has a small, short-lived probation; reuse earns protected retention. */
final class ReuseAwareChunkCache<K, V> {
    private final VisualChunkCache<K,V> probation, reused;
    private long promotions;
    ReuseAwareChunkCache(int entries, long bytes, int ttl, ToIntFunction<V> weigh) {
        long coldBytes = bytes / 8;
        int coldEntries = entries / 8;
        probation = new VisualChunkCache<>(coldEntries, coldBytes, Math.min(ttl, 100), weigh);
        reused = new VisualChunkCache<>(entries - coldEntries, bytes - coldBytes, ttl, weigh);
    }
    V get(K key, long tick) {
        V value = reused.get(key, tick);
        if (value != null) return value;
        value = probation.get(key, tick);
        if (value != null) {
            probation.invalidate(key); reused.put(key, value, tick); promotions++;
        }
        return value;
    }
    void put(K key, V value, long tick, boolean shared) {
        invalidate(key);
        if (shared) reused.put(key, value, tick); else probation.put(key, value, tick);
    }
    void invalidate(K key) { probation.invalidate(key); reused.invalidate(key); }
    void expire(long tick) { probation.expire(tick); reused.expire(tick); }
    void clear() { probation.clear(); reused.clear(); }
    int size() { return probation.size() + reused.size(); }
    long bytes() { return probation.bytes() + reused.bytes(); }
    long promotions() { return promotions; }
}
