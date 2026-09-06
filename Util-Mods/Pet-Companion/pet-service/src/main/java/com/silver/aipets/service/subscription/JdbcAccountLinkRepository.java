package com.silver.aipets.service.subscription;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** MariaDB persistence enforcing generation limits and one active UUID-bound Checkout link. */
public final class JdbcAccountLinkRepository implements AccountLinkRepository {
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("""
                     UPDATE account_link_tokens
                     SET stripe_checkout_session_id = ?, checkout_started_at = ?
                     WHERE link_token_hash = ? AND consumed_at IS NULL AND expires_at > ?
                       AND (stripe_checkout_session_id IS NULL
                            OR stripe_checkout_session_id = ?)
                     """)) {
            update.setString(1, checkoutSessionId);
            update.setTimestamp(2, Timestamp.from(checkoutStartedAt));
            update.setString(3, tokenHash);
            update.setTimestamp(4, Timestamp.from(checkoutStartedAt));
            update.setString(5, checkoutSessionId);
            return update.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not attach Stripe Checkout session", failure);
        }
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
