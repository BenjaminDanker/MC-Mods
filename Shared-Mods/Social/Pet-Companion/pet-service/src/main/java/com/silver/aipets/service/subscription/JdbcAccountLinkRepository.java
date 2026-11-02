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
import java.sql.SQLIntegrityConstraintViolationException;
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
            String checkoutAliasHash,
            Instant createdAt,
            Instant expiresAt,
            Instant generationWindowStart,
            int maximumGenerations) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(checkoutAliasHash, "checkoutAliasHash");
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
                insertAccountLink(connection, ownerUuid, tokenHash, checkoutAliasHash, expiresAt, createdAt);
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
    public Optional<AccountLinkTarget> findValidAlias(String checkoutAliasHash, Instant checkedAt) {
        Objects.requireNonNull(checkoutAliasHash, "checkoutAliasHash");
        Objects.requireNonNull(checkedAt, "checkedAt");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT owner_uuid, link_token_hash
                     FROM account_link_tokens
                     WHERE checkout_alias_hash = ? AND consumed_at IS NULL AND expires_at > ?
                       AND EXISTS (
                           SELECT 1 FROM pending_adoptions current_intent
                           WHERE current_intent.owner_uuid = account_link_tokens.owner_uuid
                             AND current_intent.account_link_hash = account_link_tokens.link_token_hash
                             AND current_intent.state = 'PRE_CHECKOUT'
                             AND account_link_tokens.stripe_checkout_session_id IS NULL
                             AND NOT EXISTS (
                                 SELECT 1 FROM pending_adoptions newer
                                 WHERE newer.owner_uuid = current_intent.owner_uuid
                                   AND (newer.created_at > current_intent.created_at
                                        OR (newer.created_at = current_intent.created_at
                                            AND newer.intent_id > current_intent.intent_id)))
                       )
                     """)) {
            select.setString(1, checkoutAliasHash);
            select.setTimestamp(2, Timestamp.from(checkedAt));
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new AccountLinkTarget(
                        UUID.fromString(rows.getString("owner_uuid")),
                        rows.getString("link_token_hash")));
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not resolve checkout alias", failure);
        }
    }

    @Override
    public Optional<PendingAdoption> find(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(pendingSelect()
                     + " WHERE p.owner_uuid = ? ORDER BY p.created_at DESC, p.intent_id DESC LIMIT 1")) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(readPending(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read pending adoption", failure);
        }
    }

    @Override
    public Optional<PendingAdoption> beginReplacement(UUID ownerUuid, Instant replacementAt) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(replacementAt, "replacementAt");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockSubscriptionIdentity(connection, ownerUuid);
                PendingAdoption current = selectPendingForUpdate(connection, ownerUuid).orElse(null);
                if (current == null || switch (current.state()) {
                    case PRE_CHECKOUT, CHECKOUT_STARTED, REPLACEMENT_PENDING,
                            WAITING_FOR_ENTITLEMENT -> false;
                    default -> true;
                }) {
                    connection.commit();
                    return Optional.empty();
                }
                if (current.state() == PendingAdoptionState.PRE_CHECKOUT) {
                    invalidateUnstartedLinks(connection, ownerUuid, replacementAt);
                }
                String nextState = current.state() == PendingAdoptionState.PRE_CHECKOUT
                        ? "CANCELLED" : "REPLACEMENT_PENDING";
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE pending_adoptions
                        SET state = ?, updated_at = ?
                        WHERE owner_uuid = ? AND intent_id = ?
                        """)) {
                    update.setString(1, nextState);
                    update.setTimestamp(2, Timestamp.from(replacementAt));
                    update.setString(3, ownerUuid.toString());
                    update.setString(4, current.intentId().toString());
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                connection.commit();
                return Optional.of(current);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not begin pending-adoption replacement", failure);
        }
    }

    @Override
    public void cancelUnfinished(UUID ownerUuid, Instant cancelledAt) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE pending_adoptions
                     SET state = 'CANCELLED', updated_at = ?
                     WHERE owner_uuid = ? AND state IN (
                         'PRE_CHECKOUT', 'CHECKOUT_STARTED', 'REPLACEMENT_PENDING',
                         'WAITING_FOR_ENTITLEMENT')
                     """)) {
            update.setTimestamp(1, Timestamp.from(cancelledAt));
            update.setString(2, ownerUuid.toString());
            update.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not cancel unfinished adoption", failure);
        }
    }

    @Override
    public PendingAdoptionLinkStatus createWithLink(
            UUID ownerUuid, UUID intentId, PetSpecies species, String name, String tokenHash,
            String checkoutAliasHash,
            Instant createdAt, Instant expiresAt, Instant generationWindowStart,
            int maximumGenerations) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(checkoutAliasHash, "checkoutAliasHash");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockSubscriptionIdentity(connection, ownerUuid);
                if (generationCount(connection, ownerUuid, generationWindowStart)
                        >= maximumGenerations) {
                    connection.rollback();
                    return PendingAdoptionLinkStatus.RATE_LIMITED;
                }
                invalidateUnstartedLinks(connection, ownerUuid, createdAt);
                insertAccountLink(connection, ownerUuid, tokenHash, checkoutAliasHash, expiresAt, createdAt);
                try (PreparedStatement cancel = connection.prepareStatement("""
                        UPDATE pending_adoptions
                        SET state = 'CANCELLED', updated_at = ?
                        WHERE owner_uuid = ? AND state IN (
                            'PRE_CHECKOUT', 'CHECKOUT_STARTED', 'REPLACEMENT_PENDING',
                            'WAITING_FOR_ENTITLEMENT')
                        """)) {
                    cancel.setTimestamp(1, Timestamp.from(createdAt));
                    cancel.setString(2, ownerUuid.toString());
                    cancel.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO pending_adoptions
                            (owner_uuid, intent_id, species, pet_name, state, created_at, expires_at,
                             account_link_hash, updated_at)
                        VALUES (?, ?, ?, ?, 'PRE_CHECKOUT', ?, ?, ?, ?)
                        """)) {
                    insert.setString(1, ownerUuid.toString());
                    insert.setString(2, intentId.toString());
                    insert.setString(3, species.name());
                    insert.setString(4, name);
                    insert.setTimestamp(5, Timestamp.from(createdAt));
                    insert.setTimestamp(6, Timestamp.from(expiresAt));
                    insert.setString(7, tokenHash);
                    insert.setTimestamp(8, Timestamp.from(createdAt));
                    insert.executeUpdate();
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
    public PendingAdoptionLinkStatus createWaitingForEntitlement(
            UUID ownerUuid, UUID intentId, PetSpecies species, String name,
            Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(name, "name");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockSubscriptionIdentity(connection, ownerUuid);
                cancelCurrentIntent(connection, ownerUuid, createdAt);
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO pending_adoptions
                            (owner_uuid, intent_id, species, pet_name, state, created_at, expires_at, updated_at)
                        VALUES (?, ?, ?, ?, 'WAITING_FOR_ENTITLEMENT', ?, ?, ?)
                        """)) {
                    insert.setString(1, ownerUuid.toString());
                    insert.setString(2, intentId.toString());
                    insert.setString(3, species.name());
                    insert.setString(4, name);
                    insert.setTimestamp(5, Timestamp.from(createdAt));
                    insert.setTimestamp(6, Timestamp.from(expiresAt));
                    insert.setTimestamp(7, Timestamp.from(createdAt));
                    insert.executeUpdate();
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
            throw new PetPersistenceException("Could not create entitlement-waiting adoption", failure);
        }
    }

    @Override
    public PendingAdoptionLinkStatus rotatePreCheckoutLink(
            UUID ownerUuid, UUID intentId, String tokenHash, String checkoutAliasHash, Instant createdAt,
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
                insertAccountLink(connection, ownerUuid, tokenHash, checkoutAliasHash, linkExpiresAt, createdAt);
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
                        OR (state = 'WAITING_FOR_ENTITLEMENT' AND expires_at <= ?)
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
            update.setTimestamp(4, Timestamp.from(now));
            update.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not expire pending adoptions", failure);
        }
    }

    @Override
    public List<PendingAdoption> findEligibleForCompletion(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit out of range");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(pendingSelect() + """
                     JOIN subscriptions s ON s.owner_uuid = p.owner_uuid
                     WHERE ((p.state = 'CHECKOUT_STARTED' AND p.checkout_completed_at IS NOT NULL
                                AND p.hard_expires_at IS NOT NULL
                                AND p.checkout_completed_at <= p.hard_expires_at)
                        OR (p.state IN ('PRE_CHECKOUT', 'WAITING_FOR_ENTITLEMENT')
                            AND s.ai_access_enabled = TRUE))
                       AND NOT EXISTS (
                            SELECT 1 FROM pending_adoptions newer
                            WHERE newer.owner_uuid = p.owner_uuid
                              AND (newer.created_at > p.created_at
                                   OR (newer.created_at = p.created_at
                                       AND newer.intent_id > p.intent_id)))
                     ORDER BY p.checkout_completed_at IS NULL, p.checkout_completed_at, p.created_at
                     LIMIT ?
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
                     WHERE owner_uuid = ? AND intent_id = ?
                       AND ((state = 'CHECKOUT_STARTED'
                             AND checkout_completed_at IS NOT NULL
                             AND checkout_completed_at <= hard_expires_at)
                            OR state IN ('PRE_CHECKOUT', 'WAITING_FOR_ENTITLEMENT'))
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
                SELECT p.owner_uuid, p.intent_id, p.species, p.pet_name, p.state, p.created_at, p.expires_at,
                       p.checkout_started_at, p.hard_expires_at, p.account_link_hash,
                       p.stripe_checkout_session_id, p.checkout_launch_claimed_at,
                       p.checkout_completed_at, p.completed_pet_id, p.completed_at, p.notification_pending
                FROM pending_adoptions p
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
                pendingSelect() + " WHERE p.owner_uuid = ? ORDER BY p.created_at DESC, p.intent_id DESC LIMIT 1 FOR UPDATE")) {
            select.setString(1, ownerUuid.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(readPending(rows)) : Optional.empty();
            }
        }
    }

    private static boolean claimIsFresh(Instant claimedAt, Instant now) {
        return claimedAt != null && claimedAt.isAfter(now.minusSeconds(60));
    }

    private static void cancelCurrentIntent(
            Connection connection, UUID ownerUuid, Instant cancelledAt) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE pending_adoptions
                SET state = 'CANCELLED', updated_at = ?
                WHERE owner_uuid = ? AND state IN (
                    'PRE_CHECKOUT', 'CHECKOUT_STARTED', 'REPLACEMENT_PENDING',
                    'WAITING_FOR_ENTITLEMENT')
                """)) {
            update.setTimestamp(1, Timestamp.from(cancelledAt));
            update.setString(2, ownerUuid.toString());
            update.executeUpdate();
        }
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
            Connection connection, UUID ownerUuid, String tokenHash, String checkoutAliasHash,
            Instant expiresAt, Instant createdAt) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO account_link_tokens
                    (link_token_hash, checkout_alias_hash, owner_uuid, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, tokenHash);
            insert.setString(2, checkoutAliasHash);
            insert.setString(3, ownerUuid.toString());
            insert.setTimestamp(4, Timestamp.from(expiresAt));
            insert.setTimestamp(5, Timestamp.from(createdAt));
            try {
                insert.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException collision) {
                if (collision.getMessage() != null
                        && collision.getMessage().toLowerCase(java.util.Locale.ROOT)
                        .contains("uq_account_link_checkout_alias")) {
                    throw new CheckoutAliasCollisionException(collision);
                }
                throw collision;
            }
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
