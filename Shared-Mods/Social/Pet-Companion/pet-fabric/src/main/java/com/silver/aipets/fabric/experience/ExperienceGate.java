package com.silver.aipets.fabric.experience;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread bounded awareness and debounce policy. */
public final class ExperienceGate {
    private final Map<String,Long> recent = new LinkedHashMap<>();

    public static boolean aware(boolean loaded, boolean sleeping, boolean removed,
            boolean ownerSameWorld, double distanceSquared) {
        return loaded && !sleeping && !removed && ownerSameWorld
                && Double.isFinite(distanceSquared) && distanceSquared <= 16 * 16;
    }

    public static boolean witnessedOwnerAction(boolean loaded, boolean sleeping,
            boolean removed, boolean sameWorld, double distanceSquared,
            boolean lineOfSight) {
        return aware(loaded, sleeping, removed, sameWorld, distanceSquared)
                && lineOfSight;
    }

    public boolean claim(UUID petId, String type, String detail, long tick, long cooldown) {
        String key=petId+":"+type+":"+detail;
        Long previous=recent.get(key);
        if (previous!=null && tick-previous<cooldown) return false;
        if (recent.size()>=2048) recent.remove(recent.keySet().iterator().next());
        recent.put(key,tick);
        return true;
    }

    public void clear() { recent.clear(); }
}
