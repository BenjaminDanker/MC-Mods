package com.silver.aipets.service.experience;

import java.time.Instant;
import java.util.Set;

/** Model admission independent of the provider: routine events and live windows stay local. */
public final class DecisionPolicy {
    private static final Set<String> NOVEL = Set.of("ARRIVED_TOGETHER", "NEW_AREA",
            "SURVIVED_DANGER", "OWNER_RETURNED_AFTER_ABSENCE");
    private DecisionPolicy() { }

    public static boolean shouldInterpret(String type, boolean novel, boolean aiAccess,
            Instant now, Instant nextModelAt) {
        return aiAccess && novel && NOVEL.contains(type)
                && (nextModelAt == null || !nextModelAt.isAfter(now));
    }

    public static long boundedSeconds(long suggested) {
        // The model may consume most of its eight-second deadline. Keep a useful
        // delivery window after it returns, while Fabric applies a stricter event-age gate.
        return Math.clamp(suggested, 30, 120);
    }
}
