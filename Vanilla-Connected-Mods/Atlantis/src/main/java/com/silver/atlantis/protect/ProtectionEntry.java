package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * One protected schematic paste instance.
 *
 * placedPositions: positions where the paste actually changed a block (break-protected).
 * interiorPositions: positions marked by barrier blocks (place-protected even though they end up air).
 */
public final class ProtectionEntry {

    private final String id;
    private final String dimensionId;
    private final LongSet placedPositions;
    private final LongSet interiorPositions;

    public ProtectionEntry(String id, String dimensionId, LongSet placedPositions, LongSet interiorPositions) {
        this.id = id;
        this.dimensionId = dimensionId;
        this.placedPositions = (placedPositions != null) ? placedPositions : new LongOpenHashSet();
        this.interiorPositions = (interiorPositions != null) ? interiorPositions : new LongOpenHashSet();
    }

    public String id() {
        return id;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public LongSet placedPositions() {
        return placedPositions;
    }

    public LongSet interiorPositions() {
        return interiorPositions;
    }
}
