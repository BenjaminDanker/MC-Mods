package com.silver.elementchoice.skills;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which advancements pay skill points and how many. An advancement pays once, when it is first completed,
 * by its frame: task, goal or challenge. Only advancements from allowlisted namespaces pay; root tabs do not. Bosses of packs that
 * have no advancements for them pay by entity tag instead.
 */
public record AdvancementPoints(Set<String> namespaces, Set<String> excluded, int task, int goal, int challenge,
                                Map<String, Integer> bossTags) {
    public static final AdvancementPoints DEFAULT = new AdvancementPoints(Set.of("minecraft"), Set.of(), 1, 2, 3, Map.of());

    /** Points for killing a mob carrying one of the configured boss tags (the highest one wins), or 0. */
    public int bossPoints(Set<String> entityTags) {
        int best = 0;
        for (String tag : entityTags) {
            best = Math.max(best, bossTags.getOrDefault(tag, 0));
        }
        return best;
    }

    /** Points for a completed advancement, or 0 when it does not pay. {@code frame} is the lowercase frame name. */
    public int pointsFor(String id, String frame, boolean root) {
        if (root || excluded.contains(id)) {
            return 0;
        }
        int colon = id.indexOf(':');
        if (colon < 0 || !namespaces.contains(id.substring(0, colon))) {
            return 0;
        }
        return switch (frame.toLowerCase(Locale.ROOT)) {
            case "task" -> task;
            case "goal" -> goal;
            case "challenge" -> challenge;
            default -> 0;
        };
    }
}
