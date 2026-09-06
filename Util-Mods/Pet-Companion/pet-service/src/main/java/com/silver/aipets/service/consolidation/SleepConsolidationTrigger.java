package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.sleep.PetSleepEvent;
import com.silver.aipets.service.sleep.PetSleepTransition;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Converts each persisted sleep-start transition into one deterministic consolidation cycle. */
public final class SleepConsolidationTrigger implements Consumer<PetSleepTransition> {
    private final ConsolidationWorker worker;

    public SleepConsolidationTrigger(ConsolidationWorker worker) {
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    public void accept(PetSleepTransition transition) {
        Objects.requireNonNull(transition, "transition");
        if (transition.event() != PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT
                && transition.event() != PetSleepEvent.SLEEP_STARTED_FORCED) {
            return;
        }
        String identity = transition.state().petId() + ":"
                + transition.state().sleepStartedAt().orElseThrow();
        UUID cycleId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        worker.enqueueAtSleepStart(transition.state().petId(), cycleId);
    }
}
