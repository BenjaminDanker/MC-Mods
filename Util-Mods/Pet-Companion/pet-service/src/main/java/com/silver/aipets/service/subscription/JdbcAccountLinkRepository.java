package com.silver.aipets.service.subscription;

import com.silver.aipets.service.persistence.PetPersistenceException;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.service.adoption.CheckoutLaunchClaim;
import com.silver.aipets.service.adoption.PendingAdoption;
import com.silver.aipets.service.adoption.PendingAdoptionLinkStatus;
import com.silver.aipets.service.adoption.PendingAdoptionNotice;
import com.silver.aipets.service.adoption.PendingAdoptionRepository;
import com.silver.aipets.service.adoption.PendingAdoptionState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** MariaDB persistence enforcing generation limits and one active UUID-bound Checkout link. */
public final class JdbcAccountLinkRepository
        implements AccountLinkRepository, PendingAdoptionRepository {
    private static final long CHECKOUT_HARD_LIFETIME_SECONDS = 24L * 60L * 60L;
    private final DataSource dataSource;

    public JdbcAccountLinkRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean create(
            UUID ownerUuid,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt,
            Instant generationWindowStart,
            int maximumGenerations) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(tokenHash, "tokenHash");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureSubscriptionIdentity(connection, ownerUuid);
                if (generationCount(connection, ownerUuid, generationWindowStart)
                        >= maximumGenerations) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement invalidate = connection.prepareStatement("""
                        UPDATE account_link_tokens
                        SET consumed_at = ?
                        WHERE owner_uuid = ? AND consumed_at IS NULL
                          AND stripe_checkout_session_id IS NULL
                        """)) {
                    invalidate.setTimestamp(1, Timestamp.from(createdAt));
                    invalidate.setString(2, ownerUuid.toString());
                    invalidate.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO account_link_tokens
                            (link_token_hash, owner_uuid, expires_at, created_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    insert.setString(1, tokenHash);
                    insert.setString(2, ownerUuid.toString());
                    insert.setTimestamp(3, Timestamp.from(expiresAt));
                    insert.setTimestamp(4, Timestamp.from(createdAt));
                    insert.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not create account-link token", failure);
        }
    }

    @Override
    public Optional<AccountLinkTarget> findValid(String tokenHash, Instant checkedAt) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(checkedAt, "checkedAt");
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT owner_uuid
                    FROM account_link_tokens
                    WHERE link_token_hash = ? AND consumed_at IS NULL AND expires_at > ?
                    """)) {
                select.setString(1, tokenHash);
                select.setTimestamp(2, Timestamp.from(checkedAt));
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) return Optional.empty();
                    return Optional.of(new AccountLinkTarget(
                            UUID.fromString(rows.getString(1)), tokenHash));
                }
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not resolve account-link token", failure);
        }
    }

    @Override
    public boolean attachCheckout(
            String tokenHash, String checkoutSessionId, Instant checkoutStartedAt) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(checkoutSessionId, "checkoutSessionId");
        Objects.requireNonNull(checkoutStartedAt, "checkoutStartedAt");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                UUID ownerUuid;
                String priorSession;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT owner_uuid, stripe_checkout_session_id, expires_at, consumed_at
                        FROM account_link_tokens WHERE link_token_hash = ? FOR UPDATE
                        """)) {
                    select.setString(1, tokenHash);
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            connection.rollback();
                            return false;
                        }
                        ownerUuid = UUID.fromString(rows.getString("owner_uuid"));
                        priorSession = rows.getString("stripe_checkout_session_id");
                        Timestamp consumedAt = rows.getTimestamp("consumed_at");
                        if (checkoutSessionId.equals(priorSession) && consumedAt == null) {
                            connection.commit();
                            return true;
                        }
                        Timestamp expiresAt = rows.getTimestamp("expires_at");
                        if (consumedAt != null || expiresAt.toInstant().compareTo(checkoutStartedAt) <= 0
                                || (priorSession != null && !priorSession.equals(checkoutSessionId))) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                boolean hasPending;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT intent_id FROM pending_adoptions
                        WHERE owner_uuid = ? AND account_link_hash = ? FOR UPDATE
                        """)) {
                    select.setString(1, ownerUuid.toString());
                    select.setString(2, tokenHash);
                    try (ResultSet rows = select.executeQuery()) {
                        hasPending = rows.next();
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE account_link_tokens
                        SET stripe_checkout_session_id = ?, checkout_started_at = ?
                        WHERE link_token_hash = ? AND consumed_at IS NULL AND expires_at > ?
                          AND (stripe_checkout_session_id IS NULL OR stripe_checkout_session_id = ?)
                        """)) {
                    update.setString(1, checkoutSessionId);
                    update.setTimestamp(2, Timestamp.from(checkoutStartedAt));
                    update.setString(3, tokenHash);
                    update.setTimestamp(4, Timestamp.from(checkoutStartedAt));
                    update.setString(5, checkoutSessionId);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                if (hasPending) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE pending_adoptions
                            SET state = 'CHECKOUT_STARTED', checkout_started_at = ?,
                                hard_expires_at = ?, stripe_checkout_session_id = ?,
                                checkout_launch_claimed_at = NULL, updated_at = ?
                            WHERE owner_uuid = ? AND account_link_hash = ?
                              AND state = 'PRE_CHECKOUT' AND expires_at > ?
                              AND checkout_launch_claimed_at IS NOT NULL
                            """)) {
                        update.setTimestamp(1, Timestamp.from(checkoutStartedAt));
                        update.setTimestamp(2, Timestamp.from(
                                checkoutStartedAt.plusSeconds(CHECKOUT_HARD_LIFETIME_SECONDS)));
                        update.setString(3, checkoutSessionId);
                        update.setTimestamp(4, Timestamp.from(checkoutStartedAt));
                        update.setString(5, ownerUuid.toString());
                        update.setString(6, tokenHash);
                        update.setTimestamp(7, Timestamp.from(checkoutStartedAt));
                        if (update.executeUpdate() != 1) {
                            connection.rollback();
                            return false;
                        }
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not attach Stripe Checkout session", failure);
        }
    }

    @Override
    public Optional<PendingAdoption> find(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(pendingSelect()
                     + " WHERE owner_uuid = ?")) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(readPending(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read pending adoption", failure);
        }
    }

    @Override
    public void cancelPreCheckout(UUID ownerUuid, Instant cancelledAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions
                     SET state = 'CANCELLED', updated_at = ?
                     WHERE owner_uuid = ? AND state = 'PRE_CHECKOUT'
                     """)) {
            update.setTimestamp(1, Timestamp.from(cancelledAt));
            update.setString(2, ownerUuid.toString());
            update.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not cancel pre-checkout adoption", failure);
        }
    }

    @Override
    public PendingAdoptionLinkStatus createWithLink(
            UUID ownerUuid, UUID intentId, PetSpecies species, String name, String tokenHash,
            Instant createdAt, Instant expiresAt, Instant generationWindowStart,
            int maximumGenerations) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tokenHash, "tokenHash");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockSubscriptionIdentity(connection, ownerUuid);
                PendingAdoption existing = selectPendingForUpdate(connection, ownerUuid).orElse(null);
                if (blocksReplacement(existing, createdAt)) {
                    connection.rollback();
                    return PendingAdoptionLinkStatus.CHECKOUT_IN_PROGRESS;
                }
                if (generationCount(connection, ownerUuid, generationWindowStart)
                        >= maximumGenerations) {
                    connection.rollback();
                    return PendingAdoptionLinkStatus.RATE_LIMITED;
                }
                invalidateUnstartedLinks(connection, ownerUuid, createdAt);
                insertAccountLink(connection, ownerUuid, tokenHash, expiresAt, createdAt);
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO pending_adoptions
                            (owner_uuid, intent_id, species, pet_name, state, created_at, expires_at,
                             account_link_hash, updated_at)
                        VALUES (?, ?, ?, ?, 'PRE_CHECKOUT', ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            intent_id = VALUES(intent_id), species = VALUES(species),
                            pet_name = VALUES(pet_name), state = 'PRE_CHECKOUT',
                            created_at = VALUES(created_at), expires_at = VALUES(expires_at),
                            checkout_started_at = NULL, hard_expires_at = NULL,
                            account_link_hash = VALUES(account_link_hash),
                            stripe_checkout_session_id = NULL,
                            checkout_launch_claimed_at = NULL, checkout_completed_at = NULL,
                            completed_pet_id = NULL, completed_at = NULL,
                            notification_pending = FALSE, notification_acknowledged_at = NULL,
                            updated_at = VALUES(updated_at)
                        """)) {
                    upsert.setString(1, ownerUuid.toString());
                    upsert.setString(2, intentId.toString());
                    upsert.setString(3, species.name());
                    upsert.setString(4, name);
                    upsert.setTimestamp(5, Timestamp.from(createdAt));
                    upsert.setTimestamp(6, Timestamp.from(expiresAt));
                    upsert.setString(7, tokenHash);
                    upsert.setTimestamp(8, Timestamp.from(createdAt));
                    upsert.executeUpdate();
                }
                connection.commit();
                return PendingAdoptionLinkStatus.CREATED;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not create pending adoption", failure);
        }
    }

    @Override
    public PendingAdoptionLinkStatus rotatePreCheckoutLink(
            UUID ownerUuid, UUID intentId, String tokenHash, Instant createdAt,
            Instant linkExpiresAt, Instant generationWindowStart, int maximumGenerations) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(intentId, "intentId");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockSubscriptionIdentity(connection, ownerUuid);
                PendingAdoption current = selectPendingForUpdate(connection, ownerUuid).orElse(null);
                if (current == null || !current.intentId().equals(intentId)
                        || current.state() != PendingAdoptionState.PRE_CHECKOUT
                        || !current.expiresAt().isAfter(createdAt)
                        || claimIsFresh(current.checkoutLaunchClaimedAt(), createdAt)) {
                    connection.rollback();
                    return PendingAdoptionLinkStatus.CHECKOUT_IN_PROGRESS;
                }
                if (generationCount(connection, ownerUuid, generationWindowStart)
                        >= maximumGenerations) {
                    connection.rollback();
                    return PendingAdoptionLinkStatus.RATE_LIMITED;
                }
                invalidateUnstartedLinks(connection, ownerUuid, createdAt);
                insertAccountLink(connection, ownerUuid, tokenHash, linkExpiresAt, createdAt);
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE pending_adoptions
                        SET account_link_hash = ?, updated_at = ?
                        WHERE owner_uuid = ? AND intent_id = ? AND state = 'PRE_CHECKOUT'
                          AND expires_at > ?
                        """)) {
                    update.setString(1, tokenHash);
                    update.setTimestamp(2, Timestamp.from(createdAt));
                    update.setString(3, ownerUuid.toString());
                    update.setString(4, intentId.toString());
                    update.setTimestamp(5, Timestamp.from(createdAt));
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return PendingAdoptionLinkStatus.CHECKOUT_IN_PROGRESS;
                    }
                }
                connection.commit();
                return PendingAdoptionLinkStatus.CREATED;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not rotate pending-adoption link", failure);
        }
    }

    @Override
    public CheckoutLaunchClaim claimCheckoutStart(String tokenHash, Instant now, Instant staleClaimBefore) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                UUID ownerUuid;
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT owner_uuid, expires_at, consumed_at FROM account_link_tokens
                        WHERE link_token_hash = ? FOR UPDATE
                        """)) {
                    select.setString(1, tokenHash);
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next() || rows.getTimestamp("consumed_at") != null
                                || !rows.getTimestamp("expires_at").toInstant().isAfter(now)) {
                            connection.rollback();
                            return CheckoutLaunchClaim.INVALID;
                        }
                        ownerUuid = UUID.fromString(rows.getString("owner_uuid"));
                    }
                }
                PendingAdoption pending = selectPendingForUpdate(connection, ownerUuid).orElse(null);
                if (pending == null || !tokenHash.equals(pending.accountLinkHash())) {
                    connection.commit();
                    return CheckoutLaunchClaim.CLAIMED;
                }
                if (pending.state() == PendingAdoptionState.CHECKOUT_STARTED) {
                    connection.commit();
                    return CheckoutLaunchClaim.ALREADY_STARTED;
                }
                if (pending.state() != PendingAdoptionState.PRE_CHECKOUT
                        || !pending.expiresAt().isAfter(now)) {
                    connection.rollback();
                    return CheckoutLaunchClaim.INVALID;
                }
                if (claimIsFresh(pending.checkoutLaunchClaimedAt(), now)
                        && pending.checkoutLaunchClaimedAt().isAfter(staleClaimBefore)) {
                    connection.rollback();
                    return CheckoutLaunchClaim.ALREADY_STARTED;
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE pending_adoptions SET checkout_launch_claimed_at = ?, updated_at = ?
                        WHERE owner_uuid = ? AND intent_id = ? AND state = 'PRE_CHECKOUT'
                        """)) {
                    update.setTimestamp(1, Timestamp.from(now));
                    update.setTimestamp(2, Timestamp.from(now));
                    update.setString(3, ownerUuid.toString());
                    update.setString(4, pending.intentId().toString());
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return CheckoutLaunchClaim.ALREADY_STARTED;
                    }
                }
                connection.commit();
                return CheckoutLaunchClaim.CLAIMED;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not claim Checkout start", failure);
        }
    }

    @Override
    public void releaseCheckoutStart(String tokenHash, Instant claimedAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions SET checkout_launch_claimed_at = NULL
                     WHERE account_link_hash = ? AND state = 'PRE_CHECKOUT'
                       AND checkout_launch_claimed_at IS NOT NULL
                       AND checkout_launch_claimed_at <= ?
                       AND checkout_launch_claimed_at > ?
                     """)) {
            update.setString(1, tokenHash);
            update.setTimestamp(2, Timestamp.from(claimedAt));
            update.setTimestamp(3, Timestamp.from(claimedAt.minusSeconds(60)));
            update.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not release Checkout-start claim", failure);
        }
    }

    @Override
    public void expireUnstarted(Instant now) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions SET state = 'EXPIRED', updated_at = ?
                     WHERE (state = 'PRE_CHECKOUT' AND expires_at <= ?)
                        OR (state = 'CHECKOUT_STARTED' AND hard_expires_at <= ?
                            AND NOT (checkout_completed_at IS NOT NULL
                                AND checkout_completed_at <= hard_expires_at
                                AND EXISTS (
                                    SELECT 1 FROM subscriptions s
                                    WHERE s.owner_uuid = pending_adoptions.owner_uuid
                                      AND s.ai_access_enabled = TRUE)))
                     """)) {
            update.setTimestamp(1, Timestamp.from(now));
            update.setTimestamp(2, Timestamp.from(now));
            update.setTimestamp(3, Timestamp.from(now));
            update.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not expire pending adoptions", failure);
        }
    }

    @Override
    public List<PendingAdoption> findCheckoutCompleted(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit out of range");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(pendingSelect() + """
                     WHERE state = 'CHECKOUT_STARTED' AND checkout_completed_at IS NOT NULL
                       AND hard_expires_at IS NOT NULL AND checkout_completed_at <= hard_expires_at
                     ORDER BY checkout_completed_at, owner_uuid LIMIT ?
                     """)) {
            select.setInt(1, limit);
            try (ResultSet rows = select.executeQuery()) {
                List<PendingAdoption> result = new ArrayList<>();
                while (rows.next()) result.add(readPending(rows));
                return List.copyOf(result);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not scan completed-checkout adoptions", failure);
        }
    }

    @Override
    public boolean complete(UUID ownerUuid, UUID intentId, UUID petId, Instant completedAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions
                     SET state = 'COMPLETED', completed_pet_id = ?, completed_at = ?,
                         notification_pending = TRUE, notification_acknowledged_at = NULL,
                         updated_at = ?
                     WHERE owner_uuid = ? AND intent_id = ? AND state = 'CHECKOUT_STARTED'
                       AND checkout_completed_at IS NOT NULL
                       AND checkout_completed_at <= hard_expires_at
                     """)) {
            update.setString(1, petId.toString());
            update.setTimestamp(2, Timestamp.from(completedAt));
            update.setTimestamp(3, Timestamp.from(completedAt));
            update.setString(4, ownerUuid.toString());
            update.setString(5, intentId.toString());
            return update.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not finalize pending adoption", failure);
        }
    }

    @Override
    public Optional<PendingAdoptionNotice> findNotification(UUID ownerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT intent_id, pet_name FROM pending_adoptions
                     WHERE owner_uuid = ? AND state = 'COMPLETED' AND notification_pending = TRUE
                     """)) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next()
                        ? Optional.of(new PendingAdoptionNotice(
                                UUID.fromString(rows.getString("intent_id")), rows.getString("pet_name")))
                        : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read adoption notification", failure);
        }
    }

    @Override
    public boolean acknowledgeNotification(UUID ownerUuid, UUID intentId, Instant acknowledgedAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions
                     SET notification_pending = FALSE, notification_acknowledged_at = ?, updated_at = ?
                     WHERE owner_uuid = ? AND intent_id = ? AND state = 'COMPLETED'
                       AND notification_pending = TRUE
                     """)) {
            update.setTimestamp(1, Timestamp.from(acknowledgedAt));
            update.setTimestamp(2, Timestamp.from(acknowledgedAt));
            update.setString(3, ownerUuid.toString());
            update.setString(4, intentId.toString());
            return update.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not acknowledge adoption notification", failure);
        }
    }

    private static String pendingSelect() {
        return """
                SELECT owner_uuid, intent_id, species, pet_name, state, created_at, expires_at,
                       checkout_started_at, hard_expires_at, account_link_hash,
                       stripe_checkout_session_id, checkout_launch_claimed_at,
                       checkout_completed_at, completed_pet_id, completed_at, notification_pending
                FROM pending_adoptions
                """;
    }

    private static PendingAdoption readPending(ResultSet rows) throws SQLException {
        String petId = rows.getString("completed_pet_id");
        return new PendingAdoption(
                UUID.fromString(rows.getString("owner_uuid")),
                UUID.fromString(rows.getString("intent_id")),
                PetSpecies.valueOf(rows.getString("species")),
                rows.getString("pet_name"),
                PendingAdoptionState.valueOf(rows.getString("state")),
                rows.getTimestamp("created_at").toInstant(),
                rows.getTimestamp("expires_at").toInstant(),
                instant(rows, "checkout_started_at"),
                instant(rows, "hard_expires_at"),
                rows.getString("account_link_hash"),
                rows.getString("stripe_checkout_session_id"),
                instant(rows, "checkout_launch_claimed_at"),
                instant(rows, "checkout_completed_at"),
                petId == null ? null : UUID.fromString(petId),
                instant(rows, "completed_at"),
                rows.getBoolean("notification_pending"));
    }

    private static Optional<PendingAdoption> selectPendingForUpdate(
            Connection connection, UUID ownerUuid) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                pendingSelect() + " WHERE owner_uuid = ? FOR UPDATE")) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(readPending(rows)) : Optional.empty();
            }
        }
    }

    private static boolean blocksReplacement(PendingAdoption pending, Instant now) {
        if (pending == null) return false;
        if (pending.checkoutStillActive(now)) return true;
        return pending.state() == PendingAdoptionState.PRE_CHECKOUT
                && pending.expiresAt().isAfter(now)
                && claimIsFresh(pending.checkoutLaunchClaimedAt(), now);
    }

    private static boolean claimIsFresh(Instant claimedAt, Instant now) {
        return claimedAt != null && claimedAt.isAfter(now.minusSeconds(60));
    }

    private static void lockSubscriptionIdentity(Connection connection, UUID ownerUuid)
            throws SQLException {
        ensureSubscriptionIdentity(connection, ownerUuid);
        try (PreparedStatement lock = connection.prepareStatement(
                "SELECT owner_uuid FROM subscriptions WHERE owner_uuid = ? FOR UPDATE")) {
            lock.setString(1, ownerUuid.toString());
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) throw new SQLException("Subscription identity row disappeared");
            }
        }
    }

    private static void invalidateUnstartedLinks(
            Connection connection, UUID ownerUuid, Instant invalidatedAt) throws SQLException {
        try (PreparedStatement invalidate = connection.prepareStatement("""
                UPDATE account_link_tokens SET consumed_at = ?
                WHERE owner_uuid = ? AND consumed_at IS NULL
                  AND stripe_checkout_session_id IS NULL
                """)) {
            invalidate.setTimestamp(1, Timestamp.from(invalidatedAt));
            invalidate.setString(2, ownerUuid.toString());
            invalidate.executeUpdate();
        }
    }

    private static void insertAccountLink(
            Connection connection, UUID ownerUuid, String tokenHash,
            Instant expiresAt, Instant createdAt) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO account_link_tokens
                    (link_token_hash, owner_uuid, expires_at, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            insert.setString(1, tokenHash);
            insert.setString(2, ownerUuid.toString());
            insert.setTimestamp(3, Timestamp.from(expiresAt));
            insert.setTimestamp(4, Timestamp.from(createdAt));
            insert.executeUpdate();
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static int generationCount(
            Connection connection, UUID ownerUuid, Instant windowStart) throws SQLException {
        try (PreparedStatement count = connection.prepareStatement("""
                SELECT COUNT(*) FROM account_link_tokens
                WHERE owner_uuid = ? AND created_at >= ?
                """)) {
            count.setString(1, ownerUuid.toString());
            count.setTimestamp(2, Timestamp.from(windowStart));
            try (ResultSet rows = count.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static void ensureSubscriptionIdentity(Connection connection, UUID ownerUuid)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO subscriptions (owner_uuid, status, ai_access_enabled)
                VALUES (?, 'INACTIVE', FALSE)
                ON DUPLICATE KEY UPDATE owner_uuid = VALUES(owner_uuid)
                """)) {
            insert.setString(1, ownerUuid.toString());
            insert.executeUpdate();
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
