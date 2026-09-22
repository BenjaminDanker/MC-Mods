package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.PetSpecies;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAdoptionTest {
    private static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void preCheckoutIsNotAnActiveStartedCheckoutAndStartedIntentHasAbsoluteCap() {
        PendingAdoption preCheckout = intent(
                PendingAdoptionState.PRE_CHECKOUT, CREATED.plusSeconds(900), null, null, null);
        assertFalse(preCheckout.checkoutStillActive(CREATED));

        Instant hardExpiry = CREATED.plusSeconds(86_400);
        PendingAdoption started = intent(
                PendingAdoptionState.CHECKOUT_STARTED,
                CREATED.plusSeconds(900), CREATED, hardExpiry, null);
        assertTrue(started.checkoutStillActive(hardExpiry.minusSeconds(1)));
        assertFalse(started.checkoutStillActive(hardExpiry));
        assertFalse(started.checkoutStillActive(hardExpiry.plusSeconds(1)));

        PendingAdoption completed = intent(
                PendingAdoptionState.COMPLETED,
                CREATED.plusSeconds(900), CREATED, hardExpiry, CREATED.plusSeconds(100));
        assertFalse(completed.checkoutStillActive(CREATED.plusSeconds(101)));
    }

    private static PendingAdoption intent(
            PendingAdoptionState state,
            Instant linkExpiry,
            Instant checkoutStarted,
            Instant hardExpiry,
            Instant checkoutCompleted) {
        return new PendingAdoption(
                UUID.randomUUID(), UUID.randomUUID(), PetSpecies.CAT, "Mochi", state,
                CREATED, linkExpiry, checkoutStarted, hardExpiry, "a".repeat(64),
                checkoutStarted == null ? null : "cs_test_123", null,
                checkoutCompleted, null, null, false);
    }
}
