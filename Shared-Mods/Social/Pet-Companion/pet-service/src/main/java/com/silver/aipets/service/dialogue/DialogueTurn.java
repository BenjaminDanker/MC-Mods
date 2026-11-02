package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Objects;

public record DialogueTurn(Role role, String text, Instant occurredAt) {
    public DialogueTurn {
        Objects.requireNonNull(role, "role");
        text = ShortTermMemorySnippet.bounded(text, 2_000, "text");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public enum Role {
        OWNER,
        PET
    }
}
