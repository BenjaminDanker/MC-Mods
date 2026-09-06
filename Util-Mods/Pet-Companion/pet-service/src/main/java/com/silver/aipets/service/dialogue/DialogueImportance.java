package com.silver.aipets.service.dialogue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public enum DialogueImportance {
    LOW,
    MEDIUM,
    HIGH;

    public Optional<Instant> expiresAt(Instant occurredAt) {
        return switch (this) {
            case LOW -> Optional.of(occurredAt.plus(Duration.ofHours(2)));
            case MEDIUM -> Optional.of(occurredAt.plus(Duration.ofHours(8)));
            case HIGH -> Optional.empty();
        };
    }
}
