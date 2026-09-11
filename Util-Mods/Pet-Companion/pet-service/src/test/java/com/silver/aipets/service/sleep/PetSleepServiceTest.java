package com.silver.aipets.service.sleep;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PetSleepServiceTest {
    private static final PetSleepPolicy POLICY = PetSleepPolicy.defaults();
    private static final Instant START = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void logoutSleepIsExactRestartSafeAndDoesNotRepeatDuringOneAbsence() {
        UUID ownerId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID petId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        InMemoryPetSleepStateStore store = new InMemoryPetSleepStateStore();
        store.put(ownerId, PetSleepState.initial(petId, START, POLICY));

        service(store, START).ownerOnline(ownerId);
        UUID absenceId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        assertEquals(PetSleepEvent.PRESENCE_UPDATED,
                service(store, START.plusSeconds(60)).ownerOffline(ownerId, absenceId)
                        .orElseThrow().event());

        // A duplicate/backend disconnect cannot replace the Velocity absence session or deadline.
        assertEquals(PetSleepEvent.UNCHANGED,
                service(store, START.plus(Duration.ofMinutes(20)))
                        .ownerOffline(ownerId, UUID.randomUUID()).orElseThrow().event());
        assertTrue(service(store, START.plus(Duration.ofMinutes(30))).processDue(10).isEmpty());

        // A fresh service instance sees the persisted due row at exactly logout + 30 minutes.
        PetSleepTransition started = service(store, START.plus(Duration.ofMinutes(31)))
                .processDue(10).getFirst();
        assertEquals(PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT, started.event());
        assertEquals(START.plus(Duration.ofMinutes(91)), started.state().sleepEndsAt().orElseThrow());
        assertEquals(absenceId, started.state().ownerAbsenceSessionId().orElseThrow());

        assertTrue(service(store, START.plus(Duration.ofMinutes(90))).processDue(10).isEmpty());
        PetSleepTransition completed = service(store, START.plus(Duration.ofMinutes(91)))
                .processDue(10).getFirst();
        assertEquals(PetSleepEvent.SLEEP_COMPLETED, completed.event());
        assertFalse(completed.state().sleeping());
        assertEquals(START.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(31)),
                completed.state().forcedSleepDueAt());

        assertTrue(service(store, START.plus(Duration.ofDays(10))).processDue(10).isEmpty());
        assertTrue(store.find(petId).orElseThrow().absenceSleepTriggered());
    }

    @Test
    void forcedSleepAndEarlyOwnerReturnPreserveTheOneHourWindow() {
        UUID ownerId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID petId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        InMemoryPetSleepStateStore store = new InMemoryPetSleepStateStore();
        store.put(ownerId, PetSleepState.initial(petId, START, POLICY));
        service(store, START).ownerOnline(ownerId);

        assertTrue(service(store, START.plus(Duration.ofHours(23)).minusMillis(1))
                .processDue(10).isEmpty());
        PetSleepTransition started = service(store, START.plus(Duration.ofHours(23)))
                .processDue(10).getFirst();
        assertEquals(PetSleepEvent.SLEEP_STARTED_FORCED, started.event());
        Instant exactEnd = START.plus(Duration.ofHours(24));
        assertEquals(exactEnd, started.state().sleepEndsAt().orElseThrow());

        // Presence changes and physical placement operations do not participate in sleep timing.
        service(store, START.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(10)))
                .ownerOnline(ownerId);
        assertTrue(store.find(petId).orElseThrow().sleeping());
        assertTrue(service(store, exactEnd.minusMillis(1)).processDue(10).isEmpty());
        PetSleepTransition completed = service(store, exactEnd).processDue(10).getFirst();
        assertEquals(PetSleepEvent.SLEEP_COMPLETED, completed.event());
        assertEquals(exactEnd.plus(Duration.ofHours(23)), completed.state().forcedSleepDueAt());
    }

    @Test
    void proxyRestartReconciliationStartsAbsenceForPersistedOnlineOwner() {
        UUID staleOwner = UUID.fromString("10000000-0000-0000-0000-000000000003");
        UUID stillOnlineOwner = UUID.fromString("10000000-0000-0000-0000-000000000004");
        InMemoryPetSleepStateStore store = new InMemoryPetSleepStateStore();
        store.put(staleOwner, PetSleepState.initial(
                UUID.fromString("20000000-0000-0000-0000-000000000003"), START, POLICY));
        store.put(stillOnlineOwner, PetSleepState.initial(
                UUID.fromString("20000000-0000-0000-0000-000000000004"), START, POLICY));
        service(store, START).ownerOnline(staleOwner);
        service(store, START).ownerOnline(stillOnlineOwner);

        Instant reconciliationTime = START.plusSeconds(60);
        int changed = service(store, reconciliationTime)
                .reconcileOnlineOwners(Set.of(stillOnlineOwner), reconciliationTime);

        assertEquals(2, changed);
        assertFalse(store.find(UUID.fromString("20000000-0000-0000-0000-000000000003"))
                .orElseThrow().ownerNetworkOnline());
        assertTrue(store.find(UUID.fromString("20000000-0000-0000-0000-000000000004"))
                .orElseThrow().ownerNetworkOnline());
        assertTrue(store.find(UUID.fromString("20000000-0000-0000-0000-000000000003"))
                .orElseThrow().ownerLastLogoutAt().isPresent());
    }

    private static PetSleepService service(InMemoryPetSleepStateStore store, Instant instant) {
        return new PetSleepService(store, POLICY, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
