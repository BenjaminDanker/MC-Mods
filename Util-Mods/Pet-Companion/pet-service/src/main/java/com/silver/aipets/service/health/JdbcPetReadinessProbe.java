package com.silver.aipets.service.health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Readiness requires a responsive DB and the current V001-V003 schema; optional systems may degrade. */
public final class JdbcPetReadinessProbe implements PetReadinessProbe {
    public static final int EXPECTED_V001_TABLE_COUNT = 16;
    public static final int EXPECTED_V002_RECALL_COLUMN_COUNT = 3;
    public static final int EXPECTED_V003_CHECKOUT_COLUMN_COUNT = 2;

    private static final String SCHEMA_COUNTS = """
            SELECT
              (SELECT COUNT(*)
               FROM information_schema.tables
               WHERE table_schema = DATABASE()
                 AND engine = 'InnoDB'
                 AND table_name IN (
                   'pets', 'pet_traits', 'pet_mood', 'pet_sleep_state', 'pet_events',
                   'long_term_memories', 'long_term_memory_source_events',
                   'long_term_memory_revisions', 'trait_change_audit', 'subscriptions',
                   'stripe_webhook_events', 'pet_recall_usage', 'ai_usage', 'jobs',
                   'account_link_tokens', 'idempotency_requests')) AS table_count,
              (SELECT COUNT(*)
               FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'pet_recall_usage'
                 AND column_name IN (
                   'request_fingerprint', 'compensation_fingerprint', 'response_json'))
                 AS recall_column_count,
              (SELECT COUNT(*)
               FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'account_link_tokens'
                 AND column_name IN ('stripe_checkout_session_id', 'checkout_started_at'))
                 AS checkout_column_count
            """;

    private final DataSource dataSource;
    private final int validationTimeoutSeconds;
    private final boolean stripeConfigured;

    public JdbcPetReadinessProbe(DataSource dataSource, long validationTimeoutMs) {
        this(dataSource, validationTimeoutMs, false);
    }

    public JdbcPetReadinessProbe(
            DataSource dataSource, long validationTimeoutMs, boolean stripeConfigured) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (validationTimeoutMs < 1) {
            throw new IllegalArgumentException("validationTimeoutMs must be positive");
        }
        this.validationTimeoutSeconds = Math.max(1, Math.toIntExact(
                Math.ceilDiv(validationTimeoutMs, 1_000L)));
        this.stripeConfigured = stripeConfigured;
    }

    @Override
    public PetReadinessSnapshot probe() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(validationTimeoutSeconds)) {
                return PetReadinessSnapshot.databaseDown(stripeConfigured);
            }
            try (PreparedStatement statement = connection.prepareStatement(SCHEMA_COUNTS);
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return PetReadinessSnapshot.migrationsOutdated(stripeConfigured);
                }
                int tableCount = rows.getInt(1);
                int recallColumnCount = rows.getInt(2);
                int checkoutColumnCount = rows.getInt(3);
                return tableCount == EXPECTED_V001_TABLE_COUNT
                                && recallColumnCount == EXPECTED_V002_RECALL_COLUMN_COUNT
                                && checkoutColumnCount == EXPECTED_V003_CHECKOUT_COLUMN_COUNT
                        ? PetReadinessSnapshot.fullyReady(stripeConfigured)
                        : PetReadinessSnapshot.migrationsOutdated(stripeConfigured);
            }
        } catch (SQLException | RuntimeException failure) {
            return PetReadinessSnapshot.databaseDown(stripeConfigured);
        }
    }
}
