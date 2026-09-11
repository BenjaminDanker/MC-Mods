package com.silver.aipets.service.billing;

import com.silver.aipets.service.persistence.PetPersistenceException;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.subscription.SubscriptionAccessDetails;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable usage reader with a small in-process reservation layer. Actual
 * provider usage is persisted in {@code ai_usage}; reservations prevent
 * concurrent calls from all passing the final few cents of a period.
 */
public final class JdbcAiBudgetService implements AiBudgetService {
    private static final String SUM_USAGE = """
            SELECT COALESCE(SUM(estimated_cost), 0)
            FROM ai_usage
            WHERE owner_uuid = ? AND created_at >= ? AND created_at < ?
            """;

    private final DataSource dataSource;
    private final SubscriptionAccess subscriptions;
    private final AiPricing pricing;
    private final Map<UUID, OwnerReservations> reservations = new HashMap<>();

    public JdbcAiBudgetService(
            DataSource dataSource, SubscriptionAccess subscriptions, AiPricing pricing) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
    }

    @Override
    public synchronized Optional<Reservation> tryReserve(
            UUID ownerUuid, BigDecimal maximumCost, Instant now) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(maximumCost, "maximumCost");
        Objects.requireNonNull(now, "now");
        if (maximumCost.signum() < 0) throw new IllegalArgumentException("maximumCost cannot be negative");
        SubscriptionAccessDetails details = subscriptions.details(ownerUuid);
        if (!details.aiAccessEnabled()) return Optional.empty();
        String periodKey = periodKey(details, now);
        BigDecimal budget = pricing.netBudgetUsd();
        BigDecimal consumed = consumed(details, ownerUuid, now);
        OwnerReservations owner = reservations.computeIfAbsent(ownerUuid, ignored -> new OwnerReservations());
        BigDecimal reserved = owner.byPeriod.getOrDefault(periodKey, BigDecimal.ZERO);
        if (consumed.add(reserved).add(maximumCost).compareTo(budget) > 0) return Optional.empty();
        owner.byPeriod.merge(periodKey, maximumCost, BigDecimal::add);
        return Optional.of(new Reservation(ownerUuid, periodKey, maximumCost));
    }

    @Override
    public synchronized void settle(Reservation reservation, BigDecimal actualCost) {
        Objects.requireNonNull(reservation, "reservation");
        Objects.requireNonNull(actualCost, "actualCost");
        if (actualCost.signum() < 0 || actualCost.compareTo(reservation.maximumCost()) > 0) {
            throw new IllegalArgumentException("actualCost must be within the reservation");
        }
        OwnerReservations owner = reservations.get(reservation.ownerUuid());
        if (owner == null) return;
        owner.byPeriod.computeIfPresent(reservation.periodKey(), (ignored, value) -> {
            BigDecimal remaining = value.subtract(reservation.maximumCost());
            return remaining.signum() <= 0 ? null : remaining;
        });
        if (owner.byPeriod.isEmpty()) reservations.remove(reservation.ownerUuid());
    }

    @Override
    public synchronized AiBudgetSnapshot snapshot(UUID ownerUuid, Instant now) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(now, "now");
        SubscriptionAccessDetails details = subscriptions.details(ownerUuid);
        BigDecimal consumed = consumed(details, ownerUuid, now);
        BigDecimal reserved = details.currentPeriodStart() == null
                ? BigDecimal.ZERO
                : reservations.getOrDefault(ownerUuid, new OwnerReservations())
                        .byPeriod.getOrDefault(periodKey(details, now), BigDecimal.ZERO);
        BigDecimal remaining = pricing.netBudgetUsd().subtract(consumed).subtract(reserved);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO;
        return new AiBudgetSnapshot(
                pricing.netBudgetUsd(), consumed, remaining,
                details.currentPeriodStart(), details.currentPeriodEnd());
    }

    private BigDecimal consumed(SubscriptionAccessDetails details, UUID ownerUuid, Instant now) {
        if (details.currentPeriodStart() == null || details.currentPeriodEnd() == null
                || !details.currentPeriodEnd().isAfter(details.currentPeriodStart())) {
            return BigDecimal.ZERO;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SUM_USAGE)) {
            statement.setString(1, ownerUuid.toString());
            statement.setTimestamp(2, Timestamp.from(details.currentPeriodStart()));
            statement.setTimestamp(3, Timestamp.from(details.currentPeriodEnd()));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return BigDecimal.ZERO;
                BigDecimal result = rows.getBigDecimal(1);
                return result == null ? BigDecimal.ZERO : result.max(BigDecimal.ZERO);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read AI budget usage", failure);
        }
    }

    private static String periodKey(SubscriptionAccessDetails details, Instant now) {
        return details.currentPeriodStart() == null
                ? "unbounded:" + now.toEpochMilli()
                : details.currentPeriodStart().toString();
    }

    private static final class OwnerReservations {
        private final Map<String, BigDecimal> byPeriod = new HashMap<>();
    }
}
