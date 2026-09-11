package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * One protected schematic paste instance.
 *
 * placedPositions: positions where the paste actually changed a block.
 * interiorMask: barrier/structure-void positions that end up as air but remain protected.
 */
public final class ProtectionEntry {

    private final String id;
    private final String dimensionId;
    private final LongSet placedPositions;
    private final InteriorMask interiorMask;

    public ProtectionEntry(String id, String dimensionId, LongSet placedPositions, InteriorMask interiorMask) {
        this.id = id;
        this.dimensionId = dimensionId;
        this.placedPositions = (placedPositions != null) ? placedPositions : new LongOpenHashSet();
        this.interiorMask = interiorMask;
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

    public InteriorMask interiorMask() {
        return interiorMask;
    }
}
