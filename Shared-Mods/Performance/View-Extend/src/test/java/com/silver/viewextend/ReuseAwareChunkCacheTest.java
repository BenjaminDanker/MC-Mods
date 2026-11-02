package com.silver.viewextend;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ReuseAwareChunkCacheTest {
    @Test void oneWayExplorationCannotEvictReusedChunksOrFillWholeBudget() {
        var cache=new ReuseAwareChunkCache<String,byte[]>(80,800,1200,a->a.length);
        cache.put("hot",new byte[10],0,false);assertNotNull(cache.get("hot",1));
        for(int i=0;i<100;i++) cache.put("cold"+i,new byte[10],2,false);
        assertTrue(cache.bytes()<=110);assertNotNull(cache.get("hot",3));
        cache.expire(102);assertEquals(10,cache.bytes());
        cache.expire(1201);assertEquals(0,cache.bytes());
    }
    @Test void coalescedChunksAreAdmittedDirectlyToReuseTierAndInvalidationClearsBoth() {
        var cache=new ReuseAwareChunkCache<String,byte[]>(80,800,1200,a->a.length);
        cache.put("shared",new byte[10],0,true);cache.expire(101);
        assertNotNull(cache.get("shared",101));cache.invalidate("shared");assertEquals(0,cache.bytes());
    }

    @Test void usefulnessStatsSeparateAdmissionTiersPromotionAndUnusedExpiry() {
        var cache = new ReuseAwareChunkCache<String, byte[]>(80, 800, 1200, a -> a.length);
        cache.put("promoted", new byte[10], 0, false);
        cache.put("unused", new byte[10], 0, false);
        cache.put("shared", new byte[10], 0, true);
        assertNotNull(cache.get("promoted", 1));
        cache.expire(1201);
        var stats = cache.drainStats();
        assertEquals(3, stats.admissions());
        assertEquals(2, stats.probationAdmissions());
        assertEquals(1, stats.protectedAdmissions());
        assertEquals(1, stats.hits());
        assertEquals(1, stats.promotions());
        assertEquals(3, stats.expirations());
        assertEquals(1, stats.expiredWithoutReuse());
        assertEquals(0, cache.drainStats().admissions());
    }
}
