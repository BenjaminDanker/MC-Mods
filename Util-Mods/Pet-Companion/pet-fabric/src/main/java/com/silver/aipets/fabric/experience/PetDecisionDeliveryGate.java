package com.silver.aipets.fabric.experience;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread event generation gate for delayed spontaneous decisions. */
public final class PetDecisionDeliveryGate {
    private static final Duration MAX_EVENT_AGE = Duration.ofSeconds(20);
    private final Map<UUID, Pending> pending = new java.util.concurrent.ConcurrentHashMap<>();

    public void begin(UUID petId, UUID entityId, UUID eventId, Instant at) {
        pending.put(petId, new Pending(entityId, eventId, at));
    }

    public void invalidate(UUID petId) { pending.remove(petId); }

    /** Consumes a result once. A retry can never show the same words twice. */
    public boolean accept(UUID petId, UUID entityId, UUID eventId, Instant now) {
        Pending current = pending.get(petId);
        if (current == null || !current.entityId().equals(entityId)
                || !current.eventId().equals(eventId)) return false;
        if (!pending.remove(petId, current)) return false;
        Duration age = Duration.between(current.at(), now);
        return !age.isNegative() && age.compareTo(MAX_EVENT_AGE) <= 0;
    }

    public boolean isPending(UUID petId, UUID entityId, UUID eventId, Instant now) {
        Pending current = pending.get(petId);
        return current != null && current.entityId().equals(entityId)
                && current.eventId().equals(eventId)
                && !now.isAfter(current.at().plus(MAX_EVENT_AGE));
    }

    public void clear() { pending.clear(); }

    private record Pending(UUID entityId, UUID eventId, Instant at) {
        private Pending {
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(at);
        }
    }
}
