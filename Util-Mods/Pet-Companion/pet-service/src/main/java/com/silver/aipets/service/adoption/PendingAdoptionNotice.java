package com.silver.aipets.service.adoption;

import java.util.Objects;
import java.util.UUID;

public record PendingAdoptionNotice(UUID intentId, String petName) {
    public PendingAdoptionNotice {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(petName, "petName");
    }
}
