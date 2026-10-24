package com.silver.magitech;

/** The numbers of a ley well and the arithmetic of its charge, kept apart from the game so it is tested directly. */
public final class WellRules {
    private WellRules() {
    }

    /** What happened when a bender channelled at a well. */
    public enum Outcome { COUNTED, YIELDED, COOLING, TOO_SOON }

    /** State of one well. */
    public static final class State {
        public int charge;
        public long readyAtMillis;
        /** Who had channelled recently when the well last yielded: the group that shares in a cooperative yield. */
        public java.util.List<java.util.UUID> participants = java.util.List.of();
        /** When each player last counted, so one player cannot spam charge. */
        private final java.util.Map<java.util.UUID, Long> lastCounted = new java.util.HashMap<>();

        public Outcome channel(java.util.UUID who, long now, int chargeNeeded, long gapMillis, long cooldownMillis) {
            return channel(who, now, chargeNeeded, gapMillis, cooldownMillis, 60_000);
        }

        /** {@code windowMillis} is how recently someone must have channelled to count as part of the group when the well yields. */
        public Outcome channel(java.util.UUID who, long now, int chargeNeeded, long gapMillis, long cooldownMillis, long windowMillis) {
            if (now < readyAtMillis) {
                return Outcome.COOLING;
            }
            Long last = lastCounted.get(who);
            if (last != null && now - last < gapMillis) {
                return Outcome.TOO_SOON;
            }
            lastCounted.put(who, now);
            charge++;
            if (charge >= chargeNeeded) {
                participants = lastCounted.entrySet().stream().filter(entry -> now - entry.getValue() <= windowMillis)
                    .map(java.util.Map.Entry::getKey).toList();
                charge = 0;
                readyAtMillis = now + cooldownMillis;
                lastCounted.clear();
                return Outcome.YIELDED;
            }
            return Outcome.COUNTED;
        }
    }

    /**
     * Extra crystals a group earns beyond the base yield: {@code perExtra} for each bender past the first, but never taking the
     * whole yield above {@code cap}. A bender on their own gets exactly the base yield, so nothing here is required.
     */
    public static int extraCrystals(int participants, int perExtra, int base, int cap) {
        return Math.max(0, Math.min(Math.max(0, participants - 1) * perExtra, cap - base));
    }
}
