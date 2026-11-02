package com.silver.aipets.service.subscription;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves only the Stripe customer already bound by a verified Checkout webhook. */
public final class JdbcSubscriptionCustomerLookup implements SubscriptionCustomerLookup {
    private final DataSource dataSource;

    public JdbcSubscriptionCustomerLookup(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<String> findCustomerId(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT stripe_customer_id FROM subscriptions WHERE owner_uuid = ?
                     """)) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String customerId = rows.getString(1);
                if (rows.next()) {
                    throw new IllegalStateException("Subscription owner is not unique");
                }
                return customerId == null || customerId.isBlank()
                        ? Optional.empty() : Optional.of(customerId);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not resolve Stripe customer", failure);
        }
    }
}
