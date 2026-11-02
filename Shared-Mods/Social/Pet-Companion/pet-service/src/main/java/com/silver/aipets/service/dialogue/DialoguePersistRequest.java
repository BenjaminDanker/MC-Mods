package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DialoguePersistRequest(
        UUID eventId,
        Pet expectedPet,
        String normalizedPlayerMessage,
        ValidatedDialogueOutput output,
        DialogueUsage usage,
        DialogueGameContext gameContext,
        Instant occurredAt) {
    public DialoguePersistRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(expectedPet, "expectedPet");
        Objects.requireNonNull(normalizedPlayerMessage, "normalizedPlayerMessage");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(gameContext, "gameContext");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!usage.petId().equals(expectedPet.petId())
                || !usage.ownerUuid().equals(expectedPet.ownerUuid())
                || usage.status() != DialogueUsage.Status.SUCCEEDED) {
            throw new IllegalArgumentException("Successful usage identity is inconsistent");
        }
    }
}
