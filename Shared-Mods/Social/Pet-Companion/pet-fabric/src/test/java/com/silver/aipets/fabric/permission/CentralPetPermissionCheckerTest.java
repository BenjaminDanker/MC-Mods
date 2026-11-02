package com.silver.aipets.fabric.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silver.authorization.Authorization;
import com.silver.authorization.AuthorizationDecision;
import com.silver.authorization.AuthorizationScope;
import com.silver.authorization.AuthorizationSubject;
import com.silver.authorization.PermissionEffect;
import com.silver.authorization.PermissionNode;
import com.silver.authorization.PermissionPattern;
import com.silver.authorization.PermissionRule;
import com.silver.authorization.PermissionNodes;
import com.silver.authorization.RuleOrigin;
import com.silver.authorization.RoleId;
import com.silver.authorization.SnapshotSigner;
import com.silver.authorization.AuthorizationSnapshot;
import com.silver.authorization.AuthorizationSnapshotStore;
import com.silver.authorization.SignedAuthorizationSnapshot;
import com.silver.authorization.SnapshotApplyResult;
import com.silver.authorization.ServerId;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CentralPetPermissionCheckerTest {
    private static final UUID PLAYER_ID = UUID.fromString("58f248d0-0000-4000-8000-000000000001");
    private static final ServerId SERVER_ID = ServerId.of("sky-island");

    @Test
    void forwardsUuidTypedPermissionAndServerToSharedAuthorization() {
        AtomicReference<AuthorizationSubject> subjectSeen = new AtomicReference<>();
        AtomicReference<PermissionNode> permissionSeen = new AtomicReference<>();
        AtomicReference<ServerId> serverSeen = new AtomicReference<>();
        Authorization authorization = (subject, permission, server) -> {
            subjectSeen.set(subject);
            permissionSeen.set(permission);
            serverSeen.set(server);
            return AuthorizationDecision.of(permission, server, 0,
                    java.util.Optional.of(PermissionRule.role(PermissionPattern.of(permission.value()),
                            AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("TEST"))), List.of());
        };
        var checker = new CentralPetPermissionChecker(authorization, SERVER_ID);

        assertTrue(checker.hasPermission(PLAYER_ID, PermissionNodes.AIPETS_ADMIN_INSPECT));
        assertEquals(AuthorizationSubject.player(PLAYER_ID), subjectSeen.get());
        assertEquals(PermissionNodes.AIPETS_ADMIN_INSPECT, permissionSeen.get());
        assertEquals(SERVER_ID, serverSeen.get());
    }

    @Test
    void unknownPetNodeAndNonPlayerAreDeniedWithoutCallingProvider() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        Authorization authorization = (subject, permission, server) -> {
            called.set(true);
            return AuthorizationDecision.defaultDeny(permission, server, 0);
        };
        var checker = new CentralPetPermissionChecker(authorization, SERVER_ID);

        assertFalse(checker.hasPermission((UUID) null, PermissionNodes.AIPETS_USE));
        assertFalse(called.get());
    }

    @Test
    void seededRolePoliciesAndDirectServerOverridesPreservePetFeatures() {
        var clock = new TestClock(Instant.parse("2026-09-19T12:00:00Z"));
        byte[] key = new byte[32];
        UUID epoch = UUID.randomUUID();
        var store = new AuthorizationSnapshotStore(clock, new SnapshotSigner());
        store.beginServerSession(SERVER_ID, epoch);
        var checker = new CentralPetPermissionChecker(store, SERVER_ID);
        UUID player = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        List<PermissionRule> publicPet = List.of(
                PermissionRule.role(PermissionPattern.of("aipets.use"), AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER")),
                PermissionRule.role(PermissionPattern.of("aipets.adopt"), AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER")),
                PermissionRule.role(PermissionPattern.of("aipets.chat"), AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER")),
                PermissionRule.role(PermissionPattern.of("aipets.compass"), AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER")),
                PermissionRule.role(PermissionPattern.of("aipets.recall"), AuthorizationScope.global(), PermissionEffect.ALLOW, RoleId.of("PLAYER")));
        apply(store, key, epoch, player, 1, publicPet, clock.instant(), clock.instant().plusSeconds(90));
        apply(store, key, epoch, moderator, 2, publicPet, clock.instant(), clock.instant().plusSeconds(90));
        var adminRules = new java.util.ArrayList<>(publicPet);
        adminRules.add(PermissionRule.role(PermissionPattern.of("aipets.admin.*"), AuthorizationScope.global(),
                PermissionEffect.ALLOW, RoleId.of("ADMIN")));
        apply(store, key, epoch, admin, 3, adminRules, clock.instant(), clock.instant().plusSeconds(90));
        apply(store, key, epoch, owner, 4, List.of(
                PermissionRule.role(PermissionPattern.of("*"), AuthorizationScope.global(),
                        PermissionEffect.ALLOW, RoleId.of("OWNER")),
                PermissionRule.direct(PermissionPattern.of("aipets.admin.inspect"),
                        AuthorizationScope.server(SERVER_ID), PermissionEffect.DENY)),
                clock.instant(), clock.instant().plusSeconds(90));

        for (UUID ordinary : List.of(player, moderator, admin, owner)) {
            for (PermissionNode node : List.of(PermissionNodes.AIPETS_USE, PermissionNodes.AIPETS_ADOPT,
                    PermissionNodes.AIPETS_CHAT, PermissionNodes.AIPETS_COMPASS, PermissionNodes.AIPETS_RECALL)) {
                assertTrue(checker.hasPermission(ordinary, node), ordinary + " should retain " + node);
            }
        }
        assertFalse(checker.hasPermission(player, PermissionNodes.AIPETS_ADMIN_INSPECT));
        assertFalse(checker.hasPermission(moderator, PermissionNodes.AIPETS_ADMIN_INSPECT));
        assertTrue(checker.hasPermission(admin, PermissionNodes.AIPETS_ADMIN_INSPECT));
        assertFalse(checker.hasPermission(owner, PermissionNodes.AIPETS_ADMIN_INSPECT), "server DENY must override OWNER *");
        assertTrue(checker.hasPermission(owner, PermissionNodes.AIPETS_ADMIN_RECOVER));

        UUID explicitlyGranted = UUID.randomUUID();
        var grantedRules = new java.util.ArrayList<>(publicPet);
        grantedRules.add(PermissionRule.direct(PermissionPattern.of("aipets.admin.inspect"),
                AuthorizationScope.server(SERVER_ID), PermissionEffect.ALLOW));
        apply(store, key, epoch, explicitlyGranted, 5, grantedRules,
                clock.instant(), clock.instant().plusSeconds(90));
        assertTrue(checker.hasPermission(explicitlyGranted, PermissionNodes.AIPETS_ADMIN_INSPECT));

        var scopedDeny = new java.util.ArrayList<>(publicPet);
        scopedDeny.add(PermissionRule.direct(PermissionPattern.of("aipets.recall"),
                AuthorizationScope.server(SERVER_ID), PermissionEffect.DENY));
        UUID restricted = UUID.randomUUID();
        apply(store, key, epoch, restricted, 6, scopedDeny,
                clock.instant(), clock.instant().plusSeconds(90));
        assertFalse(checker.hasPermission(restricted, PermissionNodes.AIPETS_RECALL));
        assertTrue(checker.hasPermission(restricted, PermissionNodes.AIPETS_USE));
    }

    @Test
    void missingAndExpiredSnapshotsFailClosedWithoutVanillaLevelFallback() {
        var clock = new TestClock(Instant.parse("2026-09-19T12:00:00Z"));
        byte[] key = new byte[32];
        UUID epoch = UUID.randomUUID();
        var store = new AuthorizationSnapshotStore(clock, new SnapshotSigner());
        store.beginServerSession(SERVER_ID, epoch);
        var checker = new CentralPetPermissionChecker(store, SERVER_ID);
        UUID player = UUID.randomUUID();
        assertFalse(checker.hasPermission(player, PermissionNodes.AIPETS_USE));

        apply(store, key, epoch, player, 1, List.of(PermissionRule.role(
                PermissionPattern.of("aipets.use"), AuthorizationScope.global(),
                PermissionEffect.ALLOW, RoleId.of("PLAYER"))), clock.instant(), clock.instant().plusSeconds(10));
        assertTrue(checker.hasPermission(player, PermissionNodes.AIPETS_USE));
        clock.advanceSeconds(11);
        assertFalse(checker.hasPermission(player, PermissionNodes.AIPETS_USE));
    }

    private static void apply(AuthorizationSnapshotStore store, byte[] key, UUID epoch, UUID subject,
                              long revision, List<PermissionRule> rules, Instant issuedAt, Instant expiresAt) {
        var snapshot = new AuthorizationSnapshot(AuthorizationSnapshot.CURRENT_PROTOCOL_VERSION,
                AuthorizationSubject.player(subject), SERVER_ID, revision, epoch, UUID.randomUUID(),
                issuedAt, expiresAt, rules);
        SignedAuthorizationSnapshot signed = new SnapshotSigner().sign(snapshot, key);
        assertEquals(SnapshotApplyResult.APPLIED, store.apply(signed,
                AuthorizationSubject.player(subject), SERVER_ID, key));
    }

    private static final class TestClock extends Clock {
        private Instant current;
        TestClock(Instant current) { this.current = current; }
        void advanceSeconds(long seconds) { current = current.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
