package com.silver.viewextend;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.function.ToIntFunction;

/** Server-thread LRU bounded by both retained bytes and entries. Expiry never refreshes on reads. */
final class VisualChunkCache<K, V> {
    private record Entry<K, V>(K key, V value, int bytes, long expires, long generation) {}
    private record Expiry<K>(K key, long expires, long generation) {}
    private final LinkedHashMap<K, Entry<K, V>> entries = new LinkedHashMap<>(256, .75f, false);
    private final ArrayDeque<Expiry<K>> expirations = new ArrayDeque<>();
    private final int maxEntries;
    private final long maxBytes;
    private final int ttl;
    private final ToIntFunction<V> weigh;
    private long bytes;
    private long generation;

    VisualChunkCache(int maxEntries, long maxBytes, int ttl, ToIntFunction<V> weigh) {
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.ttl = ttl;
        this.weigh = weigh;
    }

    V get(K key, long tick) {
        Entry<K, V> entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expires <= tick) { invalidate(key); return null; }
        entries.putLast(key, entry);
        return entry.value;
    }

    void put(K key, V value, long tick) {
        invalidate(key);
        int weight = Math.max(0, weigh.applyAsInt(value));
        if (maxEntries <= 0 || ttl <= 0 || weight > maxBytes || maxBytes <= 0) return;
        Entry<K, V> entry = new Entry<>(key, value, weight, tick + ttl, ++generation);
        entries.put(key, entry);
        expirations.addLast(new Expiry<>(key, entry.expires, entry.generation));
        bytes += weight;
        while (entries.size() > maxEntries || bytes > maxBytes) {
            bytes -= entries.pollFirstEntry().getValue().bytes;
        }
        if (expirations.size() > entries.size() * 4 + 1024) {
            // Metadata lookups must not refresh LRU order or retain evicted payloads.
            expirations.removeIf(old -> {
                Entry<K, V> current = entries.get(old.key);
                return current == null || current.generation != old.generation;
            });
        }
    }

    void expire(long tick) {
        while (!expirations.isEmpty() && expirations.peekFirst().expires <= tick) {
            Expiry<K> expired = expirations.removeFirst();
            Entry<K, V> current = entries.get(expired.key);
            if (current != null && current.generation == expired.generation) invalidate(expired.key);
        }
    }

    void invalidate(K key) {
        Entry<K, V> removed = entries.remove(key);
        if (removed != null) bytes -= removed.bytes;
    }

    int size() { return entries.size(); }
    long bytes() { return bytes; }
    void clear() { entries.clear(); expirations.clear(); bytes = 0; }
}
