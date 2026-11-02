package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkRetryCacheTest {
    @Test void repeatedFailuresBackOffAcrossExpiryAndResetOnAvailability() {
        var cache = new ChunkRetryCache<String>(10);
        assertEquals(100, cache.fail("chunk", 0, "unfinished", 100));
        assertEquals(0, cache.remaining("chunk", 100));
        assertEquals(200, cache.fail("chunk", 100, "unfinished", 100));
        for (int i = 0; i < 20; i++) cache.fail("chunk", 1000, "unfinished", 100);
        assertEquals(6000, cache.remaining("chunk", 1000));
        cache.invalidate("chunk");
        assertEquals(100, cache.fail("chunk", 2000, "unfinished", 100));
        assertEquals(20, cache.fail("chunk", 2100, "overload", 20));
    }

    @Test void anotherObserverGetsRemainingCooldownWithoutExtendingIt() {
        var cache = new ChunkRetryCache<String>(4);
        cache.put("overworld:1,2", 10, 100);
        assertEquals(100, cache.remaining("overworld:1,2", 10));
        assertEquals(40, cache.remaining("overworld:1,2", 70));
        assertEquals(0, cache.remaining("overworld:1,2", 110));
    }
    @Test void liveChunkInvalidatesCooldownImmediatelyAndDimensionsStayIndependent() {
        var cache = new ChunkRetryCache<String>(4);
        cache.put("overworld:1,2", 10, 1200);
        assertEquals(0, cache.remaining("nether:1,2", 11));
        cache.invalidate("overworld:1,2");
        assertEquals(0, cache.remaining("overworld:1,2", 11));
    }
    @Test void explorationCannotGrowCacheBeyondCapacity() {
        var cache = new ChunkRetryCache<Integer>(2);
        cache.put(1, 0, 100); cache.put(2, 0, 100); cache.put(3, 0, 100);
        assertEquals(0, cache.remaining(1, 1));
        assertEquals(99, cache.remaining(2, 1));
        assertEquals(99, cache.remaining(3, 1));
    }
}
