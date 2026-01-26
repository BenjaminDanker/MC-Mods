package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Collects per-run protected positions during paste.
 */
public final class ProtectionCollector {

    private final String id;
    private final String dimensionId;

    private final LongSet placed = new LongOpenHashSet();
    private final LongSet interior = new LongOpenHashSet();

    public ProtectionCollector(String id, String dimensionId) {
        this.id = id;
        this.dimensionId = dimensionId;
    }

    public void addPlaced(long posKey) {
        placed.add(posKey);
    }

    public void addInterior(long posKey) {
        interior.add(posKey);
    }

    public boolean isEmpty() {
        return placed.isEmpty() && interior.isEmpty();
    }

    public ProtectionEntry buildEntry() {
        return new ProtectionEntry(id, dimensionId, placed, interior);
    }
}
