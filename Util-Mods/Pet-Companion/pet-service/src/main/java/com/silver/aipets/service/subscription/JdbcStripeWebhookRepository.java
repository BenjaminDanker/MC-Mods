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

/** Transactional Stripe event ledger and monotonic subscription-state convergence. */
public final class JdbcStripeWebhookRepository implements StripeWebhookRepository {
    private final DataSource dataSource;

    public JdbcStripeWebhookRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public StripeWebhookApplyStatus apply(
            StripeWebhookEvent event,
            Instant processedAt,
            String configuredPriceId,
            int paymentGraceDays) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(processedAt, "processedAt");
        Objects.requireNonNull(configuredPriceId, "configuredPriceId");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                StripeWebhookApplyStatus existing = beginEvent(connection, event, processedAt);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }
                if (event.kind() == StripeWebhookKind.UNSUPPORTED) {
                    markProcessed(connection, event.eventId(), processedAt);
                    connection.commit();
                    return StripeWebhookApplyStatus.IGNORED;
                }
                Optional<UUID> checkoutOwner = Optional.empty();
                if (event.kind() == StripeWebhookKind.CHECKOUT_COMPLETED) {
                    checkoutOwner = resolveCheckoutOwner(connection, event);
                    if (checkoutOwner.isEmpty()
                            || (event.ownerUuid().isPresent()
                                && !event.ownerUuid().orElseThrow()
                                        .equals(checkoutOwner.orElseThrow()))) {
                        markProcessed(connection, event.eventId(), processedAt);
                        connection.commit();
                        return StripeWebhookApplyStatus.REJECTED;
                    }
                }
                SubscriptionState row = (checkoutOwner.isPresent()
                        ? selectBy(connection, "owner_uuid", checkoutOwner.orElseThrow().toString())
                        : resolveSubscription(connection, event)).orElse(null);
                if (row == null) {
                    markFailed(connection, event.eventId(), "UNRESOLVED_SUBSCRIPTION_IDENTITY");
                    connection.commit();
                    return StripeWebhookApplyStatus.RETRY_NEEDED;
                }
                if (identityConflict(connection, row, event)) {
                    markProcessed(connection, event.eventId(), processedAt);
                    connection.commit();
                    return StripeWebhookApplyStatus.REJECTED;
                }
                StripeWebhookApplyStatus result;
                if (row.lastStripeEventAt() != null
                        && event.createdAt().isBefore(row.lastStripeEventAt())) {
                    result = StripeWebhookApplyStatus.STALE;
                } else if (event.kind() == StripeWebhookKind.CHECKOUT_COMPLETED) {
                    bindCheckoutIdentity(connection, row, event, processedAt);
                    consumeCheckoutLink(connection, event, processedAt);
                    result = StripeWebhookApplyStatus.APPLIED;
                } else {
                    updateEntitlement(
                            connection, row, event, processedAt,
                            configuredPriceId, paymentGraceDays);
                    result = StripeWebhookApplyStatus.APPLIED;
                }
                markProcessed(connection, event.eventId(), processedAt);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not apply Stripe webhook", failure);
        }
    }

    /** Returns DUPLICATE for a completed replay; null means this attempt owns processing. */
    private static StripeWebhookApplyStatus beginEvent(
            Connection connection, StripeWebhookEvent event, Instant receivedAt)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT status, payload_sha256
                FROM stripe_webhook_events
                WHERE stripe_event_id = ?
                FOR UPDATE
                """)) {
            select.setString(1, event.eventId());
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    String digest = rows.getString("payload_sha256");
                    if (!event.payloadSha256().equals(digest)) {
                        throw new IllegalStateException(
                                "Stripe event ID was reused with a different payload");
                    }
                    if ("PROCESSED".equals(rows.getString("status"))) {
                        return StripeWebhookApplyStatus.DUPLICATE;
                    }
                    try (PreparedStatement retry = connection.prepareStatement("""
                            UPDATE stripe_webhook_events
                            SET status = 'RECEIVED', attempt_count = attempt_count + 1,
                                last_error_sanitized = NULL
                            WHERE stripe_event_id = ?
                            """)) {
                        retry.setString(1, event.eventId());
                        retry.executeUpdate();
                    }
                    return null;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO stripe_webhook_events
                    (stripe_event_id, event_type, stripe_created_at, payload_sha256,
                     received_at, status, attempt_count)
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', 1)
                """)) {
            insert.setString(1, event.eventId());
            insert.setString(2, event.eventType());
            insert.setTimestamp(3, Timestamp.from(event.createdAt()));
            insert.setString(4, event.payloadSha256());
            insert.setTimestamp(5, Timestamp.from(receivedAt));
            insert.executeUpdate();
        }
        return null;
    }

    private static Optional<SubscriptionState> resolveSubscription(
            Connection connection, StripeWebhookEvent event) throws SQLException {
        if (event.ownerUuid().isPresent()) {
            return selectBy(connection, "owner_uuid", event.ownerUuid().orElseThrow().toString());
        }
        if (event.subscriptionId().isPresent()) {
            Optional<SubscriptionState> row = selectBy(
                    connection, "stripe_subscription_id", event.subscriptionId().orElseThrow());
            if (row.isPresent()) return row;
        }
        if (event.customerId().isPresent()) {
            return selectBy(
                    connection, "stripe_customer_id", event.customerId().orElseThrow());
        }
        return Optional.empty();
    }

    private static Optional<UUID> resolveCheckoutOwner(
            Connection connection, StripeWebhookEvent event) throws SQLException {
        if (event.accountLinkHash().isEmpty() || event.checkoutSessionId().isEmpty()) {
            return Optional.empty();
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_uuid
                FROM account_link_tokens
                WHERE link_token_hash = ? AND stripe_checkout_session_id = ?
                  AND checkout_started_at IS NOT NULL AND consumed_at IS NULL
                FOR UPDATE
                """)) {
            select.setString(1, event.accountLinkHash().orElseThrow());
            select.setString(2, event.checkoutSessionId().orElseThrow());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                UUID owner = UUID.fromString(rows.getString(1));
                if (rows.next()) throw new IllegalStateException("Checkout link is not unique");
                return Optional.of(owner);
            }
        }
    }

    private static void consumeCheckoutLink(
            Connection connection, StripeWebhookEvent event, Instant consumedAt)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE account_link_tokens
                SET consumed_at = ?
                WHERE link_token_hash = ? AND stripe_checkout_session_id = ?
                  AND consumed_at IS NULL
                """)) {
            update.setTimestamp(1, Timestamp.from(consumedAt));
            update.setString(2, event.accountLinkHash().orElseThrow());
            update.setString(3, event.checkoutSessionId().orElseThrow());
            requireSingleUpdate(update);
        }
    }

    private static Optional<SubscriptionState> selectBy(
            Connection connection, String column, String value) throws SQLException {
        if (!column.equals("owner_uuid")
                && !column.equals("stripe_subscription_id")
                && !column.equals("stripe_customer_id")) {
            throw new IllegalArgumentException("Unsupported subscription lookup column");
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_uuid, stripe_customer_id, stripe_subscription_id,
                       stripe_price_id, status, ai_access_enabled,
                       current_period_start, current_period_end,
                       cancel_at_period_end, grace_ends_at, last_stripe_event_at
                FROM subscriptions
                WHERE """ + column + " = ? FOR UPDATE")) {
            select.setString(1, value);
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                SubscriptionState result = new SubscriptionState(
                        UUID.fromString(rows.getString("owner_uuid")),
                        rows.getString("stripe_customer_id"),
                        rows.getString("stripe_subscription_id"),
                        rows.getString("stripe_price_id"),
                        SubscriptionStatus.valueOf(rows.getString("status")),
                        rows.getBoolean("ai_access_enabled"),
                        instant(rows, "current_period_start"),
                        instant(rows, "current_period_end"),
                        rows.getBoolean("cancel_at_period_end"),
                        instant(rows, "grace_ends_at"),
                        instant(rows, "last_stripe_event_at"));
                if (rows.next()) {
                    throw new IllegalStateException("Subscription identity is not unique");
                }
                return Optional.of(result);
            }
        }
    }

    private static boolean identityConflict(
            Connection connection, SubscriptionState selected, StripeWebhookEvent event)
            throws SQLException {
        UUID selectedOwner = selected.ownerUuid();
        if (selected.customerId() != null && event.customerId().isPresent()
                && !selected.customerId().equals(event.customerId().orElseThrow())) {
            return true;
        }
        if (event.customerId().isPresent()) {
            Optional<SubscriptionState> byCustomer = selectBy(
                    connection, "stripe_customer_id", event.customerId().orElseThrow());
            if (byCustomer.isPresent()
                    && !selectedOwner.equals(byCustomer.orElseThrow().ownerUuid())) return true;
        }
        if (event.subscriptionId().isPresent()) {
            Optional<SubscriptionState> bySubscription = selectBy(
                    connection, "stripe_subscription_id", event.subscriptionId().orElseThrow());
            return bySubscription.isPresent()
                    && !selectedOwner.equals(bySubscription.orElseThrow().ownerUuid());
        }
        return false;
    }

    private static void bindCheckoutIdentity(
            Connection connection,
            SubscriptionState row,
            StripeWebhookEvent event,
            Instant processedAt) throws SQLException {
        SubscriptionState bound = SubscriptionStateReducer.bindCheckout(row, event);
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE subscriptions
                SET stripe_customer_id = ?, stripe_subscription_id = ?,
                    stripe_price_id = ?, updated_at = ?
                WHERE owner_uuid = ?
                """)) {
            nullableString(update, 1, bound.customerId());
            nullableString(update, 2, bound.subscriptionId());
            nullableString(update, 3, bound.priceId());
            update.setTimestamp(4, Timestamp.from(processedAt));
            update.setString(5, row.ownerUuid().toString());
            requireSingleUpdate(update);
        }
    }

    private static void updateEntitlement(
            Connection connection,
            SubscriptionState row,
            StripeWebhookEvent event,
            Instant processedAt,
            String configuredPriceId,
            int paymentGraceDays) throws SQLException {
        SubscriptionState next = SubscriptionStateReducer.applyEntitlement(
                row, event, processedAt, configuredPriceId, paymentGraceDays);
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE subscriptions
                SET stripe_customer_id = ?, stripe_subscription_id = ?, stripe_price_id = ?,
                    status = ?, ai_access_enabled = ?, current_period_start = ?,
                    current_period_end = ?, cancel_at_period_end = ?, grace_ends_at = ?,
                    last_stripe_event_at = ?, updated_at = ?
                WHERE owner_uuid = ?
                """)) {
            nullableString(update, 1, next.customerId());
            nullableString(update, 2, next.subscriptionId());
            nullableString(update, 3, next.priceId());
            update.setString(4, next.status().name());
            update.setBoolean(5, next.aiAccessEnabled());
            nullableInstant(update, 6, next.currentPeriodStart());
            nullableInstant(update, 7, next.currentPeriodEnd());
            update.setBoolean(8, next.cancelAtPeriodEnd());
            nullableInstant(update, 9, next.graceEndsAt());
            update.setTimestamp(10, Timestamp.from(event.createdAt()));
            update.setTimestamp(11, Timestamp.from(processedAt));
            update.setString(12, row.ownerUuid().toString());
            requireSingleUpdate(update);
        }
    }

    private static void markProcessed(
            Connection connection, String eventId, Instant processedAt) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE stripe_webhook_events
                SET status = 'PROCESSED', processed_at = ?, last_error_sanitized = NULL
                WHERE stripe_event_id = ?
                """)) {
            update.setTimestamp(1, Timestamp.from(processedAt));
            update.setString(2, eventId);
            requireSingleUpdate(update);
        }
    }

    private static void markFailed(Connection connection, String eventId, String error)
            throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE stripe_webhook_events
                SET status = 'FAILED', last_error_sanitized = ?
                WHERE stripe_event_id = ?
                """)) {
            update.setString(1, error);
            update.setString(2, eventId);
            requireSingleUpdate(update);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void nullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void nullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    private static void requireSingleUpdate(PreparedStatement statement) throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw new IllegalStateException("Expected exactly one billing row update");
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
