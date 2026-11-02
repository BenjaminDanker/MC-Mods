package com.silver.authorization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, SQL-free local snapshot cache with signature, scope, lease and replay validation. */
public final class AuthorizationSnapshotStore implements Authorization {
    public static final Duration MAX_LEASE = Duration.ofMinutes(5);
    public static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);

    private final Clock clock;
    private final SnapshotSigner signer;
    private final Map<SnapshotKey, AuthorizationSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<ServerId, UUID> activeEpochs = new HashMap<>();
    private final Map<SnapshotKey, Watermark> watermarks = new HashMap<>();

    public AuthorizationSnapshotStore() {
        this(Clock.systemUTC(), new SnapshotSigner());
    }

    public AuthorizationSnapshotStore(Clock clock, SnapshotSigner signer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.signer = Objects.requireNonNull(signer, "signer");
    }

    /** Starts a fresh backend process/session and invalidates its prior snapshots. */
    public synchronized void beginServerSession(ServerId server, UUID backendEpoch) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(backendEpoch, "backendEpoch");
        activeEpochs.put(server, backendEpoch);
        removeServerEntries(server);
    }

    public synchronized void endServerSession(ServerId server, UUID backendEpoch) {
        Objects.requireNonNull(server, "server");
        if (!Objects.equals(activeEpochs.get(server), backendEpoch)) return;
        activeEpochs.remove(server);
        removeServerEntries(server);
    }

    /** Clears one departed player's state on a backend switch/disconnect without affecting others. */
    public synchronized void clearSubject(AuthorizationSubject subject, ServerId server) {
        SnapshotKey key = new SnapshotKey(Objects.requireNonNull(subject, "subject"),
                Objects.requireNonNull(server, "server"));
        snapshots.remove(key);
        watermarks.remove(key);
    }

    public synchronized SnapshotApplyResult apply(
            SignedAuthorizationSnapshot signed,
            AuthorizationSubject expectedSubject,
            ServerId expectedServer,
            byte[] backendKey) {
        Objects.requireNonNull(signed, "signed");
        Objects.requireNonNull(expectedSubject, "expectedSubject");
        Objects.requireNonNull(expectedServer, "expectedServer");
        Instant now = clock.instant();
        AuthorizationSnapshot snapshot = signed.snapshot();

        if (!snapshot.serverId().equals(expectedServer)) return SnapshotApplyResult.WRONG_SERVER;
        if (!snapshot.subject().equals(expectedSubject)) return SnapshotApplyResult.WRONG_SUBJECT;
        UUID activeEpoch = activeEpochs.get(expectedServer);
        if (activeEpoch == null) return SnapshotApplyResult.NO_ACTIVE_SESSION;
        if (!snapshot.backendEpoch().equals(activeEpoch)) return SnapshotApplyResult.WRONG_SESSION;
        if (snapshot.issuedAt().isAfter(now.plus(MAX_CLOCK_SKEW))) return SnapshotApplyResult.NOT_YET_VALID;
        if (!snapshot.expiresAt().isAfter(now)) return SnapshotApplyResult.EXPIRED;
        Duration lease = Duration.between(snapshot.issuedAt(), snapshot.expiresAt());
        if (lease.isNegative() || lease.isZero() || lease.compareTo(MAX_LEASE) > 0) {
            return SnapshotApplyResult.INVALID_LEASE;
        }
        if (!signer.verify(signed, backendKey)) return SnapshotApplyResult.INVALID_SIGNATURE;

        SnapshotKey key = new SnapshotKey(expectedSubject, expectedServer);
        Watermark previous = watermarks.get(key);
        if (previous != null) {
            if (snapshot.revision() < previous.revision()) return SnapshotApplyResult.STALE_REVISION;
            if (snapshot.revision() == previous.revision() && !snapshot.rules().equals(previous.rules())) {
                return SnapshotApplyResult.REVISION_CONFLICT;
            }
            if (!snapshot.issuedAt().isAfter(previous.issuedAt()) || snapshot.nonce().equals(previous.nonce())) {
                return SnapshotApplyResult.REPLAY;
            }
        }

        snapshots.put(key, snapshot);
        watermarks.put(key, new Watermark(snapshot.revision(), snapshot.issuedAt(), snapshot.expiresAt(),
                snapshot.nonce(), snapshot.rules()));
        return SnapshotApplyResult.APPLIED;
    }

    /** Removes expired cache and replay-watermark entries without extending any permission lease. */
    public synchronized int purgeExpired() {
        Instant now = clock.instant();
        int before = snapshots.size();
        watermarks.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) return false;
            snapshots.remove(entry.getKey());
            return true;
        });
        return before - snapshots.size();
    }

    public Optional<AuthorizationSnapshot> snapshot(AuthorizationSubject subject, ServerId server) {
        return Optional.ofNullable(snapshots.get(new SnapshotKey(subject, server)));
    }

    @Override
    public boolean hasRole(AuthorizationSubject subject, RoleId role, ServerId server) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(server, "server");
        AuthorizationSnapshot snapshot = snapshots.get(new SnapshotKey(subject, server));
        Instant now = clock.instant();
        return snapshot != null && !snapshot.issuedAt().isAfter(now) && snapshot.expiresAt().isAfter(now)
                && snapshot.rules().stream().anyMatch(rule -> rule.sourceRole().filter(role::equals).isPresent());
    }

    @Override
    public AuthorizationDecision decide(AuthorizationSubject subject, PermissionNode permission, ServerId server) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(server, "server");
        AuthorizationSnapshot snapshot = snapshots.get(new SnapshotKey(subject, server));
        Instant now = clock.instant();
        if (snapshot == null || snapshot.issuedAt().isAfter(now) || !snapshot.expiresAt().isAfter(now)) {
            return AuthorizationDecision.defaultDeny(permission, server, 0);
        }
        return PermissionEvaluator.evaluate(snapshot.rules(), permission, server, snapshot.revision());
    }

    @Override
    public boolean has(AuthorizationSubject subject, PermissionNode permission, ServerId server) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(server, "server");
        AuthorizationSnapshot snapshot = snapshots.get(new SnapshotKey(subject, server));
        Instant now = clock.instant();
        return snapshot != null && !snapshot.issuedAt().isAfter(now) && snapshot.expiresAt().isAfter(now)
                && PermissionEvaluator.has(snapshot.rules(), permission, server);
    }

    private void removeServerEntries(ServerId server) {
        snapshots.keySet().removeIf(key -> key.server().equals(server));
        watermarks.keySet().removeIf(key -> key.server().equals(server));
    }

    private record SnapshotKey(AuthorizationSubject subject, ServerId server) {
    }

    private record Watermark(long revision, Instant issuedAt, Instant expiresAt, UUID nonce,
                             java.util.List<PermissionRule> rules) {
    }
}
