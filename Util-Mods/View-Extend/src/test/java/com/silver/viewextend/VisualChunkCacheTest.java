package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VisualChunkCacheTest {
    @Test void expiryQueueNeverOwnsEvictedPayloads() throws Exception {
        var cache = new VisualChunkCache<String, byte[]>(1, 1024, 6000, a -> a.length);
        byte[] evicted = new byte[1024];
        cache.put("old", evicted, 0);
        cache.put("new", new byte[1024], 1);
        var field = VisualChunkCache.class.getDeclaredField("expirations");
        field.setAccessible(true);
        for (Object expiry : (Iterable<?>) field.get(cache)) {
            for (var component : expiry.getClass().getDeclaredFields()) {
                component.setAccessible(true);
                assertNotSame(evicted, component.get(expiry));
                assertTrue(component.getType() == Object.class || component.getType() == long.class);
            }
        }
        cache.clear();
        assertEquals(0, cache.bytes());
        assertFalse(((Iterable<?>) field.get(cache)).iterator().hasNext());
    }

    @Test void byteLimitEvictsLeastRecentlyUsedWithoutEvictingReaders() {
        var cache = new VisualChunkCache<String, byte[]>(10, 6, 100, a -> a.length);
        byte[] first = new byte[3];
        cache.put("first", first, 0);
        cache.put("second", new byte[3], 1);
        assertSame(first, cache.get("first", 2));
        cache.put("third", new byte[3], 3);
        assertNull(cache.get("second", 3));
        assertSame(first, cache.get("first", 3));
        assertEquals(6, cache.bytes());
    }
    @Test void staleExpiryDoesNotMakeReplacementRecentlyUsed() {
        var cache = new VisualChunkCache<String, String>(2, 100, 10, String::length);
        cache.put("key", "old", 0);
        cache.put("key", "new", 1);
        cache.put("other", "other", 2);
        cache.expire(10);
        cache.put("third", "third", 10);
        assertNull(cache.get("key", 10));
        assertEquals("other", cache.get("other", 10));
    }
    @Test void staleExpiryCannotRemoveReplacement() {
        var cache = new VisualChunkCache<String, String>(10, 100, 10, String::length);
        cache.put("key", "old", 0);
        cache.put("key", "replacement", 5);
        cache.expire(10);
        assertEquals("replacement", cache.get("key", 10));
        cache.expire(15);
        assertEquals(0, cache.size());
        assertEquals(0, cache.bytes());
    }
    @Test void oversizedEntryDoesNotEvictUsefulEntries() {
        var cache = new VisualChunkCache<String, String>(10, 4, 10, String::length);
        cache.put("small", "ok", 0);
        cache.put("huge", "oversized", 1);
        assertEquals("ok", cache.get("small", 1));
        assertNull(cache.get("huge", 1));
    }
}
