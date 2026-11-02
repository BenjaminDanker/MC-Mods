package com.silver.aipets.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable authoritative aggregate. */
public record Pet(
        UUID petId,
        UUID ownerUuid,
        String name,
        PetAppearance appearance,
        PetTraits traits,
        PetMood mood,
        PetPlacement placement,
        long recordVersion,
        Instant createdAt,
        Instant updatedAt) {
    public static final int MAX_NAME_CODE_POINTS = 64;

    public Pet {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        name = validateName(name);
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(mood, "mood");
        Objects.requireNonNull(placement, "placement");
        if (recordVersion < 0) {
            throw new IllegalArgumentException("recordVersion must be non-negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
    }

    public static Pet adopted(
            UUID petId,
            UUID ownerUuid,
            String name,
            PetAppearance appearance,
            PetTraits traits,
            PetMood mood,
            Instant adoptedAt) {
        return new Pet(
                petId,
                ownerUuid,
                name,
                appearance,
                traits,
                mood,
                HeldPlacement.INSTANCE,
                0,
                adoptedAt,
                adoptedAt);
    }

    public PlacementState placementState() {
        return placement.state();
    }

    private static String validateName(String value) {
        Objects.requireNonNull(value, "name");
        if (!value.equals(value.strip()) || value.isBlank()) {
            throw new IllegalArgumentException("Pet name must be non-blank with no surrounding whitespace");
        }
        if (value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("Pet name is too long");
        }
        if (value.indexOf('\u00a7') >= 0 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Pet name contains unsafe formatting/control characters");
        }
        return value;
    }
}
