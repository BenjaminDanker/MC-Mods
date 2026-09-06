package com.silver.aipets.service.subscription;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Authoritative adoption entitlement from the Stripe-converged subscription row. */
public final class JdbcSubscriptionAccess implements SubscriptionAccess {
    private static final String SELECT_ACCESS = """
            SELECT status, ai_access_enabled, cancel_at_period_end,
                   current_period_end, grace_ends_at
            FROM subscriptions
            WHERE owner_uuid = ?
            """;

    private final DataSource dataSource;

    public JdbcSubscriptionAccess(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean canAdopt(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACCESS)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
                String status = rows.getString("status");
                java.time.Instant now = java.time.Instant.now();
                java.sql.Timestamp periodEnd = rows.getTimestamp("current_period_end");
                java.sql.Timestamp graceEnd = rows.getTimestamp("grace_ends_at");
                boolean withinPaidPeriod = !rows.getBoolean("cancel_at_period_end")
                        || periodEnd == null || periodEnd.toInstant().isAfter(now);
                boolean lifecycleAllows = ("ACTIVE".equals(status) || "TRIALING".equals(status))
                        ? withinPaidPeriod
                        : "PAST_DUE".equals(status)
                                && graceEnd != null && graceEnd.toInstant().isAfter(now);
                boolean allowed = rows.getBoolean("ai_access_enabled") && lifecycleAllows;
                if (rows.next()) {
                    throw new PetPersistenceException(
                            "Subscription uniqueness invariant is violated", null);
                }
                return allowed;
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read subscription access", failure);
        }
    }
}
