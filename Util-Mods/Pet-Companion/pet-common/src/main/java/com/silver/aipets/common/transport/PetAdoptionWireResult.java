package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

public record PetAdoptionWireResult(
        PetAdoptionWireStatus status,
        Optional<Pet> pet,
        Optional<String> checkoutUrl,
        Optional<Instant> checkoutExpiresAt) {
    public PetAdoptionWireResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        checkoutUrl = Objects.requireNonNull(checkoutUrl, "checkoutUrl");
        checkoutExpiresAt = Objects.requireNonNull(checkoutExpiresAt, "checkoutExpiresAt");
        boolean denied = status == PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED
                || status == PetAdoptionWireStatus.SPECIES_UNAVAILABLE
                || status == PetAdoptionWireStatus.CHECKOUT_RATE_LIMITED
                || status == PetAdoptionWireStatus.CHECKOUT_REQUIRED
                || status == PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS;
        if (denied == pet.isPresent()) {
            throw new IllegalArgumentException("Adoption result pet presence does not match status");
        }
        if ((status == PetAdoptionWireStatus.CHECKOUT_REQUIRED)
                != (checkoutUrl.isPresent() && checkoutExpiresAt.isPresent())) {
            throw new IllegalArgumentException("Checkout URL presence does not match status");
        }
        if (status != PetAdoptionWireStatus.CHECKOUT_REQUIRED && status != PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS
                && (checkoutUrl.isPresent() || checkoutExpiresAt.isPresent())) {
            throw new IllegalArgumentException("Unexpected checkout URL on adoption result");
        }
        if (status == PetAdoptionWireStatus.CHECKOUT_REQUIRED && pet.isPresent()) {
            throw new IllegalArgumentException("Checkout-required result cannot include a pet");
        }
    }

    public PetAdoptionWireResult(PetAdoptionWireStatus status, Optional<Pet> pet) {
        this(status, pet, Optional.empty(), Optional.empty());
    }
}
