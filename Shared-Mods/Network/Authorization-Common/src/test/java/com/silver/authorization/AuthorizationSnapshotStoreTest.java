package com.silver.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthorizationSnapshotStoreTest {
    private static final byte[] KEY = new byte[32];
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private static final AuthorizationSubject PLAYER = AuthorizationSubject.player(
            UUID.fromString("58f248d0-0000-4000-8000-000000000001"));
    private static final ServerId SERVER = ServerId.of("sky-island");
    private static final UUID EPOCH = UUID.fromString("1d3f33e3-0000-4000-8000-000000000001");
    private static final PermissionRule ALLOW_RESTART = new PermissionRule(
            PermissionPattern.of("server.restart"), AuthorizationScope.global(),
            RuleOrigin.ROLE, PermissionEffect.ALLOW);

    @Test
    void signsAndAppliesSnapshotThenAnswersFromMemoryUntilLeaseExpires() {
        MutableClock clock = new MutableClock(NOW);
        var store = new AuthorizationSnapshotStore(clock, new SnapshotSigner());
        store.beginServerSession(SERVER, EPOCH);
        var signed = signed(snapshot(4, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART)));

        assertEquals(SnapshotApplyResult.APPLIED, store.apply(signed, PLAYER, SERVER, KEY));
        assertTrue(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));

        clock.set(NOW.plusSeconds(61));
        assertFalse(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));
    }

    @Test
    void rejectsWrongSubjectServerAndBackendSession() {
        var store = new AuthorizationSnapshotStore(Clock.fixed(NOW, ZoneOffset.UTC), new SnapshotSigner());
        store.beginServerSession(SERVER, EPOCH);
        var valid = snapshot(1, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of());

        assertEquals(SnapshotApplyResult.WRONG_SUBJECT, store.apply(
                signed(valid), AuthorizationSubject.player(UUID.randomUUID()), SERVER, KEY));
        assertEquals(SnapshotApplyResult.WRONG_SERVER, store.apply(
                signed(valid), PLAYER, ServerId.of("magic"), KEY));
        var otherEpoch = new AuthorizationSnapshot(valid.protocolVersion(), valid.subject(), valid.serverId(),
                valid.revision(), UUID.randomUUID(), valid.nonce(), valid.issuedAt(), valid.expiresAt(), valid.rules());
        assertEquals(SnapshotApplyResult.WRONG_SESSION, store.apply(
                signed(otherEpoch), PLAYER, SERVER, KEY));
        assertEquals(SnapshotApplyResult.NO_ACTIVE_SESSION, new AuthorizationSnapshotStore()
                .apply(signed(valid), PLAYER, SERVER, KEY));
    }

    @Test
    void rejectsTamperingAndKeysShorterThan256Bits() {
        var signer = new SnapshotSigner();
        var original = snapshot(1, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART));
        var signature = signer.sign(original, KEY).signature();
        var changed = snapshot(1, NOW, NOW.plusSeconds(60), original.nonce(), List.of());

        assertFalse(signer.verify(new SignedAuthorizationSnapshot(changed, signature), KEY));
        assertThrows(IllegalArgumentException.class, () -> signer.sign(original, new byte[31]));
    }

    @Test
    void rejectsExpiredFutureAndOverlongLeases() {
        var store = activeStore();
        assertEquals(SnapshotApplyResult.EXPIRED, store.apply(signed(snapshot(
                1, NOW.minusSeconds(120), NOW.minusSeconds(1), UUID.randomUUID(), List.of())), PLAYER, SERVER, KEY));
        assertEquals(SnapshotApplyResult.NOT_YET_VALID, store.apply(signed(snapshot(
                1, NOW.plus(AuthorizationSnapshotStore.MAX_CLOCK_SKEW).plusMillis(1),
                NOW.plusSeconds(60), UUID.randomUUID(), List.of())), PLAYER, SERVER, KEY));
        assertEquals(SnapshotApplyResult.INVALID_LEASE, store.apply(signed(snapshot(
                1, NOW, NOW.plus(AuthorizationSnapshotStore.MAX_LEASE).plusMillis(1), UUID.randomUUID(), List.of())),
                PLAYER, SERVER, KEY));
    }

    @Test
    void rejectsStaleReplayAndRevisionContentConflicts() {
        var store = activeStore();
        var initial = signed(snapshot(7, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(initial, PLAYER, SERVER, KEY));
        assertEquals(SnapshotApplyResult.REPLAY, store.apply(initial, PLAYER, SERVER, KEY));

        var stale = signed(snapshot(6, NOW.plusSeconds(1), NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.STALE_REVISION, store.apply(stale, PLAYER, SERVER, KEY));

        var conflict = signed(snapshot(7, NOW.plusSeconds(1), NOW.plusSeconds(60), UUID.randomUUID(), List.of()));
        assertEquals(SnapshotApplyResult.REVISION_CONFLICT,
                store.apply(conflict, PLAYER, SERVER, KEY));

        var refresh = signed(snapshot(7, NOW.plusSeconds(1), NOW.plusSeconds(61), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(refresh, PLAYER, SERVER, KEY));
    }

    @Test
    void aNewBackendSessionInvalidatesOldStateAndWatermarks() {
        var store = activeStore();
        var old = signed(snapshot(8, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(old, PLAYER, SERVER, KEY));
        store.beginServerSession(SERVER, UUID.randomUUID());

        assertFalse(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));
        assertEquals(SnapshotApplyResult.WRONG_SESSION, store.apply(old, PLAYER, SERVER, KEY));
    }

    @Test
    void disconnectClearsOnlyThatSubjectsSnapshotForThisBackend() {
        var store = activeStore();
        var valid = signed(snapshot(9, NOW, NOW.plusSeconds(60), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(valid, PLAYER, SERVER, KEY));
        store.clearSubject(PLAYER, SERVER);
        assertFalse(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));
        assertTrue(store.snapshot(PLAYER, SERVER).isEmpty());
    }

    @Test
    void proxyLinkLossRetainsAcceptedStateOnlyUntilLeaseExpiry() {
        MutableClock clock = new MutableClock(NOW);
        var store = new AuthorizationSnapshotStore(clock, new SnapshotSigner());
        store.beginServerSession(SERVER, EPOCH);
        var valid = signed(snapshot(10, NOW, NOW.plusSeconds(30), UUID.randomUUID(), List.of(ALLOW_RESTART)));
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(valid, PLAYER, SERVER, KEY));

        // A proxy/backend connection loss does not clear the accepted lease.
        clock.set(NOW.plusSeconds(29));
        assertTrue(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));
        clock.set(NOW.plusSeconds(31));
        assertFalse(store.has(PLAYER, PermissionNode.of("server.restart"), SERVER));
        assertEquals(1, store.purgeExpired());
        assertTrue(store.snapshot(PLAYER, SERVER).isEmpty());
    }

    private static AuthorizationSnapshotStore activeStore() {
        var store = new AuthorizationSnapshotStore(Clock.fixed(NOW, ZoneOffset.UTC), new SnapshotSigner());
        store.beginServerSession(SERVER, EPOCH);
        return store;
    }

    private static AuthorizationSnapshot snapshot(
            long revision, Instant issuedAt, Instant expiresAt, UUID nonce, List<PermissionRule> rules) {
        return new AuthorizationSnapshot(AuthorizationSnapshot.CURRENT_PROTOCOL_VERSION,
                PLAYER, SERVER, revision, EPOCH, nonce, issuedAt, expiresAt, rules);
    }

    private static SignedAuthorizationSnapshot signed(AuthorizationSnapshot snapshot) {
        return new SnapshotSigner().sign(snapshot, KEY);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        private void set(Instant value) {
            instant.set(value);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
