package com.silver.aipets.service.adoption;

import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Clock;
import java.util.Objects;

/** Bounded durable retry pump for webhook-confirmed adoption intents. */
public final class PendingAdoptionCompletionWorker {
    private final PendingAdoptionRepository pending;
    private final PetAdoptionService adoptions;
    private final SubscriptionAccess subscriptions;
    private final Clock clock;

    public PendingAdoptionCompletionWorker(
            PendingAdoptionRepository pending,
            PetAdoptionService adoptions,
            SubscriptionAccess subscriptions,
            Clock clock) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.adoptions = Objects.requireNonNull(adoptions, "adoptions");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int processBatch() {
        pending.expireUnstarted(clock.instant());
        int completed = 0;
        for (PendingAdoption intent : pending.findCheckoutCompleted(100)) {
            if (!subscriptions.canAdopt(intent.ownerUuid())) continue;
            AdoptionResult adoption = adoptions.adopt(
                    intent.ownerUuid(), intent.species(), intent.name());
            if ((adoption.status() == AdoptionStatus.CREATED
                    || adoption.status() == AdoptionStatus.EXISTING)
                    && adoption.pet().isPresent()
                    && pending.complete(intent.ownerUuid(), intent.intentId(),
                            adoption.pet().orElseThrow().petId(), clock.instant())) {
                completed++;
            }
        }
        return completed;
    }
}
