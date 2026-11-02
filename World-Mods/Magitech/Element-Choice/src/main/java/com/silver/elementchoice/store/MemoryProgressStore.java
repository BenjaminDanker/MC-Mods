package com.silver.elementchoice.store;

import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for tests and scratch servers; nothing is persisted. */
public final class MemoryProgressStore implements ProgressStore {
    private record Row(int unlocks, int points, String tree) { }

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();
    private final Row start;

    public MemoryProgressStore() {
        this(0, 0);
    }

    /** Every new player starts with these unlock bits and points (used on scratch servers). */
    public MemoryProgressStore(int startingUnlocks, int startingPoints) {
        this.start = new Row(startingUnlocks, startingPoints, "");
    }

    @Override
    public synchronized SkillState load(UUID id, String name) {
        Row row = rows.getOrDefault(id, start);
        return SkillState.parse(row.unlocks, row.points, row.tree);
    }

    @Override
    public synchronized boolean spend(UUID id, String name, int cost, String expectedTree, String newTree) {
        Row row = rows.getOrDefault(id, start);
        if (row.points < cost || !row.tree.equals(expectedTree)) {
            return false;
        }
        rows.put(id, new Row(row.unlocks, row.points - cost, newTree));
        return true;
    }

    @Override
    public synchronized boolean unlock(UUID id, String name, Realm element) {
        Row row = rows.getOrDefault(id, start);
        if ((row.unlocks & element.unlockBit()) != 0) {
            return false;
        }
        rows.put(id, new Row(row.unlocks | element.unlockBit(), row.points, row.tree));
        return true;
    }

    @Override
    public synchronized void addPoints(UUID id, String name, int delta) {
        Row row = rows.getOrDefault(id, start);
        rows.put(id, new Row(row.unlocks, Math.max(0, row.points + delta), row.tree));
    }
}
