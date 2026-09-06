package com.silver.aipets.service.sleep;

import java.util.Objects;

public record PetSleepTransition(PetSleepState state, PetSleepEvent event) {
    public PetSleepTransition {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
    }

    public boolean changed() {
        return event != PetSleepEvent.UNCHANGED;
    }
}
