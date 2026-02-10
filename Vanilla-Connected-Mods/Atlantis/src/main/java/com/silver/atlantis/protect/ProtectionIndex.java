package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Per-dimension compact index for O(1) protection checks.
 *
 * We store two 16-bit counters in one int:
 * - high 16: break-protected count
 * - low 16: place-protected count
 */
final class ProtectionIndex {

    private static final int BREAK_SHIFT = 16;
    private static final int PLACE_MASK = 0xFFFF;

    private final Long2IntOpenHashMap counts = new Long2IntOpenHashMap();

    ProtectionIndex() {
        counts.defaultReturnValue(0);
    }

    void addEntry(ProtectionEntry entry) {
        if (entry == null) {
            return;
        }

        LongIterator placedIt = entry.placedPositions().iterator();
        while (placedIt.hasNext()) {
            long key = placedIt.nextLong();
            increment(key, true, false);
        }

        LongIterator interiorIt = entry.interiorPositions().iterator();
        while (interiorIt.hasNext()) {
            long key = interiorIt.nextLong();
            increment(key, false, true);
        }
    }

    void removeEntry(ProtectionEntry entry) {
        if (entry == null) {
            return;
        }

        LongIterator placedIt = entry.placedPositions().iterator();
        while (placedIt.hasNext()) {
            long key = placedIt.nextLong();
            decrement(key, true, false);
        }

        LongIterator interiorIt = entry.interiorPositions().iterator();
        while (interiorIt.hasNext()) {
            long key = interiorIt.nextLong();
            decrement(key, false, true);
        }
    }

    boolean isBreakProtected(long posKey) {
        int packed = counts.get(posKey);
        return (packed >>> BREAK_SHIFT) != 0;
    }

    boolean isPlaceProtected(long posKey) {
        int packed = counts.get(posKey);
        return (packed & PLACE_MASK) != 0;
    }

    boolean isAnyProtected(long posKey) {
        return counts.get(posKey) != 0;
    }

    void increment(long key, boolean incBreak, boolean incPlace) {
        int packed = counts.get(key);
        int breakCount = (packed >>> BREAK_SHIFT);
        int placeCount = (packed & PLACE_MASK);

        if (incBreak) {
            breakCount = Math.min(0xFFFF, breakCount + 1);
        }
        if (incPlace) {
            placeCount = Math.min(0xFFFF, placeCount + 1);
        }

        counts.put(key, (breakCount << BREAK_SHIFT) | (placeCount & PLACE_MASK));
    }

    void decrement(long key, boolean decBreak, boolean decPlace) {
        int packed = counts.get(key);
        if (packed == 0) {
            return;
        }

        int breakCount = (packed >>> BREAK_SHIFT);
        int placeCount = (packed & PLACE_MASK);

        if (decBreak && breakCount > 0) {
            breakCount--;
        }
        if (decPlace && placeCount > 0) {
            placeCount--;
        }

        int next = (breakCount << BREAK_SHIFT) | (placeCount & PLACE_MASK);
        if (next == 0) {
            counts.remove(key);
        } else {
            counts.put(key, next);
        }
    }
}
