package com.silver.elementchoice.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silver.elementchoice.store.MemoryProgressStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryStoreTest {
    private final UUID id = UUID.randomUUID();

    @Test
    void unlocksAccumulateAndAreReportedOnlyOnce() {
        var store = new MemoryProgressStore();
        assertTrue(store.unlock(id, "p", Realm.FIRE));
        assertFalse(store.unlock(id, "p", Realm.FIRE));
        assertTrue(store.unlock(id, "p", Realm.EARTH));
        assertTrue(store.load(id, "p").unlocked(Realm.FIRE) && store.load(id, "p").unlocked(Realm.EARTH));
        assertFalse(store.load(id, "p").unlocked(Realm.WATER));
    }

    @Test
    void spendingIsAtomicAndRejectsStaleOrUnaffordableChanges() {
        var store = new MemoryProgressStore();
        store.addPoints(id, "p", 5);
        assertTrue(store.spend(id, "p", 2, "", "water"));
        assertFalse(store.spend(id, "p", 2, "", "water,earth"), "stale tree text");
        assertFalse(store.spend(id, "p", 9, "water", "water,a:torrent=3"), "too expensive");
        var state = store.load(id, "p");
        assertEquals(3, state.points());
        assertEquals("water", state.serializeTree());
        store.addPoints(id, "p", -100);
        assertEquals(0, store.load(id, "p").points());
    }
}
