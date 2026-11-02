package com.silver.structurereset;

import java.util.ArrayList;
import java.util.List;

/** One generated structure being tracked. Plain fields so Gson stores it directly. */
public final class Instance {
    public enum State { SNAPSHOTTING, READY, BEATEN }

    public String key;
    public String dimension;
    public String structure;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public State state = State.SNAPSHOTTING;
    public long discoveredAt;
    /** Wall-clock millis when it was beaten; 0 while it is not. */
    public long beatenAt;
    public int resets;
    /** Loot containers found in the snapshot (BlockPos.asLong), and the ones seen opened since. */
    public List<Long> lootPositions = new ArrayList<>();
    public List<Long> opened = new ArrayList<>();

    public Zone zone() {
        return new Zone(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public String folder() {
        return key.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /** True when the structure is beaten and a full interval has passed since. */
    public boolean dueForReset(long now, long intervalMillis) {
        return state == State.BEATEN && now - beatenAt >= intervalMillis;
    }

    /** Records one opened loot container; returns true when that makes every container opened. */
    public boolean markOpened(long position) {
        if (!lootPositions.contains(position) || opened.contains(position)) {
            return false;
        }
        opened.add(position);
        return opened.size() >= lootPositions.size();
    }

    public void markBeaten(long now) {
        state = State.BEATEN;
        beatenAt = now;
    }

    public void markRestored() {
        state = State.READY;
        beatenAt = 0;
        opened.clear();
        resets++;
    }
}
