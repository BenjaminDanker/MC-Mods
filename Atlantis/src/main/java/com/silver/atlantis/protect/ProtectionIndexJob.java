package com.silver.atlantis.protect;

import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Incrementally applies a ProtectionEntry to a ProtectionIndex (add or remove)
 * over multiple ticks so huge protection sets cannot watchdog the server.
 */
final class ProtectionIndexJob {

    enum Mode {
        ADD,
        REMOVE
    }

    private final Mode mode;
    private final ProtectionEntry entry;
    private final ProtectionIndex index;

    private final LongIterator placedIt;
    private final LongIterator interiorIt;

    private boolean placedDone;
    private boolean interiorDone;

    ProtectionIndexJob(Mode mode, ProtectionEntry entry, ProtectionIndex index) {
        this.mode = mode;
        this.entry = entry;
        this.index = index;

        this.placedIt = entry.placedPositions().iterator();
        this.interiorIt = entry.interiorPositions().iterator();
    }

    String entryId() {
        return entry.id();
    }

    String dimensionId() {
        return entry.dimensionId();
    }

    boolean isDone() {
        return placedDone && interiorDone;
    }

    void step(long budgetNanos) {
        if (budgetNanos <= 0L) {
            budgetNanos = 1L;
        }

        long start = System.nanoTime();
        while ((System.nanoTime() - start) < budgetNanos) {
            if (!placedDone) {
                if (placedIt.hasNext()) {
                    long key = placedIt.nextLong();
                    if (mode == Mode.ADD) {
                        index.increment(key, true, false);
                    } else {
                        index.decrement(key, true, false);
                    }
                    continue;
                }
                placedDone = true;
            }

            if (!interiorDone) {
                if (interiorIt.hasNext()) {
                    long key = interiorIt.nextLong();
                    if (mode == Mode.ADD) {
                        index.increment(key, false, true);
                    } else {
                        index.decrement(key, false, true);
                    }
                    continue;
                }
                interiorDone = true;
            }

            return;
        }
    }
}
