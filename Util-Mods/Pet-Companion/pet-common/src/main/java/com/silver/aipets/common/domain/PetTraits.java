package com.silver.aipets.common.domain;

import java.time.Instant;
import java.util.Objects;

/** Authoritative explicit personality and owner-relationship values. */
public record PetTraits(
        int curiosity,
        int boldness,
        int playfulness,
        int expressiveness,
        int independence,
        int attachment,
        int trust,
        int security,
        String relationshipSummary,
        long summaryVersion,
        Instant updatedAt) {
    public PetTraits {
        requirePercentage(curiosity, "curiosity");
        requirePercentage(boldness, "boldness");
        requirePercentage(playfulness, "playfulness");
        requirePercentage(expressiveness, "expressiveness");
        requirePercentage(independence, "independence");
        requirePercentage(attachment, "attachment");
        requirePercentage(trust, "trust");
        requirePercentage(security, "security");
        Objects.requireNonNull(relationshipSummary, "relationshipSummary");
        if (summaryVersion < 0) {
            throw new IllegalArgumentException("summaryVersion must be non-negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static PetTraits initial(
            int curiosity,
            int boldness,
            int playfulness,
            int expressiveness,
            int independence,
            Instant createdAt) {
        return new PetTraits(
                curiosity,
                boldness,
                playfulness,
                expressiveness,
                independence,
                40,
                45,
                40,
                "",
                0,
                createdAt);
    }

    public int value(TraitName name) {
        return switch (Objects.requireNonNull(name, "name")) {
            case CURIOSITY -> curiosity;
            case BOLDNESS -> boldness;
            case PLAYFULNESS -> playfulness;
            case EXPRESSIVENESS -> expressiveness;
            case INDEPENDENCE -> independence;
            case ATTACHMENT -> attachment;
            case TRUST -> trust;
            case SECURITY -> security;
        };
    }

    private static void requirePercentage(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be in 0..100");
        }
    }
}
