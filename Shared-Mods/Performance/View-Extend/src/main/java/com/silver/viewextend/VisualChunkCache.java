package com.silver.viewextend;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.function.ToIntFunction;

/** Server-thread LRU bounded by both retained bytes and entries. Expiry never refreshes on reads. */
final class VisualChunkCache<K, V> {
    private record Entry<K, V>(K key, V value, int bytes, long expires, long generation,
            boolean reused) {}
    private record Expiry<K>(K key, long expires, long generation) {}
    private final LinkedHashMap<K, Entry<K, V>> entries = new LinkedHashMap<>(256, .75f, false);
    private final ArrayDeque<Expiry<K>> expirations = new ArrayDeque<>();
    private final int maxEntries;
    private final long maxBytes;
    private final int ttl;
    private final ToIntFunction<V> weigh;
    private long bytes;
    private long generation;
    private long expirationsCount;
    private long entryLimitEvictions;
    private long byteLimitEvictions;
    private long expiredWithoutReuse;

    VisualChunkCache(int maxEntries, long maxBytes, int ttl, ToIntFunction<V> weigh) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.ttl = ttl;
        this.weigh = weigh;
    }

    V get(K key, long tick) {
        Entry<K, V> entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expires <= tick) { expireEntry(key, entry); return null; }
        if (!entry.reused) entry = new Entry<>(entry.key, entry.value, entry.bytes,
                entry.expires, entry.generation, true);
        entries.putLast(key, entry);
        return entry.value;
    }

    boolean put(K key, V value, long tick) { return put(key, value, tick, false); }

    boolean put(K key, V value, long tick, boolean alreadyReused) {
        invalidate(key);
        int weight = Math.max(0, weigh.applyAsInt(value));
        if (maxEntries <= 0 || ttl <= 0 || weight > maxBytes || maxBytes <= 0) return false;
        Entry<K, V> entry = new Entry<>(key, value, weight, tick + ttl, ++generation,
                alreadyReused);
        entries.put(key, entry);
        expirations.addLast(new Expiry<>(key, entry.expires, entry.generation));
        bytes += weight;
        while (entries.size() > maxEntries || bytes > maxBytes) {
            boolean overEntries = entries.size() > maxEntries;
            bytes -= entries.pollFirstEntry().getValue().bytes;
            if (overEntries) entryLimitEvictions++; else byteLimitEvictions++;
        }
        if (expirations.size() > entries.size() * 4 + 1024) {
            // Metadata lookups must not refresh LRU order or retain evicted payloads.
            expirations.removeIf(old -> {
                Entry<K, V> current = entries.get(old.key);
                return current == null || current.generation != old.generation;
            });
        }
        return true;
    }

    void expire(long tick) {
        while (!expirations.isEmpty() && expirations.peekFirst().expires <= tick) {
            Expiry<K> expired = expirations.removeFirst();
            Entry<K, V> current = entries.get(expired.key);
            if (current != null && current.generation == expired.generation) {
                expireEntry(expired.key, current);
            }
        }
    }

    private void expireEntry(K key, Entry<K, V> entry) {
        entries.remove(key);
        bytes -= entry.bytes;
        expirationsCount++;
        if (!entry.reused) expiredWithoutReuse++;
    }

    void invalidate(K key) {
        Entry<K, V> removed = entries.remove(key);
        if (removed != null) bytes -= removed.bytes;
    }

    int size() { return entries.size(); }
    long bytes() { return bytes; }
    boolean containsIdentity(K key, V value) {
        Entry<K, V> entry = entries.get(key);
        return entry != null && entry.value == value;
    }
    EventStats drainEventStats() {
        EventStats stats = new EventStats(expirationsCount, entryLimitEvictions,
                byteLimitEvictions, expiredWithoutReuse);
        expirationsCount = 0;
        entryLimitEvictions = 0;
        byteLimitEvictions = 0;
        expiredWithoutReuse = 0;
        return stats;
    }
    void clear() { entries.clear(); expirations.clear(); bytes = 0; }

    record EventStats(long expirations, long entryLimitEvictions,
            long byteLimitEvictions, long expiredWithoutReuse) {
        EventStats plus(EventStats other) {
            return new EventStats(expirations + other.expirations,
                    entryLimitEvictions + other.entryLimitEvictions,
                    byteLimitEvictions + other.byteLimitEvictions,
                    expiredWithoutReuse + other.expiredWithoutReuse);
        }
    }
}
