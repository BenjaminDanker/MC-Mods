package com.silver.arcade.realm;

import java.util.TreeMap;

/**
 * Which climb tiers a room appears at, and how hard it plays there. The climb tier is the reward ladder; each room has
 * its own difficulty level underneath, given in RoomCatalog with {@link #curve}:
 * <pre>
 *   Difficulty.curve(After.HOLD, 1, 1, 2, 2, 3, 3, 5, 4)   // climb tier 1 -> level 1, 2 -> 2, 3-4 -> 3, 5+ -> 4
 * </pre>
 * Pairs are (climb tier, level). Tiers below the first pair: not offered. Between pairs: the level of the pair below.
 * Above the last pair: {@link After#STOP} (not offered), {@link After#HOLD} (the last level) or {@link After#GROW} (one
 * level more per tier). A level of 0 means "not at this tier". Rooms without a curve play at the climb tier from their
 * catalogue first tier on.
 */
public final class Difficulty {
    public enum After { STOP, HOLD, GROW }

    public record Curve(TreeMap<Integer, Integer> levels, After after) {
        public int level(int tier) {
            var at = levels.floorEntry(tier);
            if (at == null) {
                return 0;
            }
            if (tier > levels.lastKey()) {
                return switch (after) {
                    case HOLD -> levels.lastEntry().getValue();
                    case GROW -> levels.lastEntry().getValue() + (tier - levels.lastKey());
                    case STOP -> 0;
                };
            }
            return at.getValue();
        }
    }

    private Difficulty() {
    }

    /** {@code pairs}: climb tier, level, climb tier, level, ... */
    public static Curve curve(After after, int... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("pairs of (climb tier, level) expected");
        }
        TreeMap<Integer, Integer> levels = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            levels.put(pairs[i], pairs[i + 1]);
        }
        return new Curve(levels, after);
    }

    /** One level per tier from {@code from} to {@code to}, then {@code after}. */
    public static Curve linear(int from, int to, After after) {
        TreeMap<Integer, Integer> levels = new TreeMap<>();
        for (int t = from; t <= to; t++) {
            levels.put(t, t - from + 1);
        }
        return new Curve(levels, after);
    }

    /** The room's level at this climb tier, or 0 when it is not offered there. */
    public static int level(RoomType type, int tier) {
        if (type.curve() == null) {
            return tier >= type.minTier() ? tier : 0;
        }
        return type.curve().level(tier);
    }
}
