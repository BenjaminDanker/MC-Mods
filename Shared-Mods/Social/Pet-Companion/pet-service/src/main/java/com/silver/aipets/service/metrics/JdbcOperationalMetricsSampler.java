package com.silver.aipets.service.metrics;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Reads low-cardinality operational gauges from MariaDB without exposing owner or pet IDs. */
public final class JdbcOperationalMetricsSampler {
    private static final String SNAPSHOT = """
            SELECT
              (SELECT COUNT(*) FROM subscriptions WHERE ai_access_enabled=TRUE AND
                 ((status IN ('ACTIVE','TRIALING') AND
                   (cancel_at_period_end=FALSE OR current_period_end IS NULL OR current_period_end>UTC_TIMESTAMP(6)))
                  OR (status='PAST_DUE' AND grace_ends_at IS NOT NULL AND grace_ends_at>UTC_TIMESTAMP(6)))) AS active_subscriptions,
              (SELECT COUNT(*) FROM subscriptions WHERE NOT (ai_access_enabled=TRUE AND
                 ((status IN ('ACTIVE','TRIALING') AND
                   (cancel_at_period_end=FALSE OR current_period_end IS NULL OR current_period_end>UTC_TIMESTAMP(6)))
                  OR (status='PAST_DUE' AND grace_ends_at IS NOT NULL AND grace_ends_at>UTC_TIMESTAMP(6))))) AS inactive_subscriptions,
              (SELECT COUNT(*) FROM pets WHERE placement_state='HELD') AS held_pets,
              (SELECT COUNT(*) FROM pets WHERE placement_state='PLACED') AS placed_pets,
              (SELECT COUNT(*) FROM pet_sleep_state WHERE sleeping=TRUE) AS sleeping_pets,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:cat' AND scale<0.633) AS cat_small,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:cat' AND scale>=0.633 AND scale<0.717) AS cat_medium,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:cat' AND scale>=0.717) AS cat_large,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:wolf' AND scale<0.593) AS dog_small,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:wolf' AND scale>=0.593 AND scale<0.687) AS dog_medium,
              (SELECT COUNT(*) FROM pets WHERE mob_type='minecraft:wolf' AND scale>=0.687) AS dog_large,
              (SELECT COUNT(*) FROM (SELECT owner_uuid,COUNT(*) AS replies FROM ai_usage
                 WHERE operation='DIALOGUE' AND status='SUCCEEDED'
                   AND created_at>=UTC_DATE() GROUP BY owner_uuid) usage WHERE replies BETWEEN 0 AND 10) AS usage_0_10,
              (SELECT COUNT(*) FROM (SELECT owner_uuid,COUNT(*) AS replies FROM ai_usage
                 WHERE operation='DIALOGUE' AND status='SUCCEEDED'
                   AND created_at>=UTC_DATE() GROUP BY owner_uuid) usage WHERE replies BETWEEN 11 AND 25) AS usage_11_25,
              (SELECT COUNT(*) FROM (SELECT owner_uuid,COUNT(*) AS replies FROM ai_usage
                 WHERE operation='DIALOGUE' AND status='SUCCEEDED'
                   AND created_at>=UTC_DATE() GROUP BY owner_uuid) usage WHERE replies BETWEEN 26 AND 50) AS usage_26_50,
              (SELECT COUNT(*) FROM (SELECT owner_uuid,COUNT(*) AS replies FROM ai_usage
                 WHERE operation='DIALOGUE' AND status='SUCCEEDED'
                   AND created_at>=UTC_DATE() GROUP BY owner_uuid) usage WHERE replies BETWEEN 51 AND 100) AS usage_51_100,
              (SELECT COUNT(*) FROM (SELECT owner_uuid,COUNT(*) AS replies FROM ai_usage
                 WHERE operation='DIALOGUE' AND status='SUCCEEDED'
                   AND created_at>=UTC_DATE() GROUP BY owner_uuid) usage WHERE replies>100) AS usage_over_100,
              (SELECT COUNT(*) FROM jobs WHERE job_type='MEMORY_EMBEDDING'
                 AND status IN ('PENDING','RUNNING','RETRY')) AS embedding_queue_depth
            """;

    private final DataSource dataSource;
    private final PetOperationalMetrics metrics;

    public JdbcOperationalMetricsSampler(DataSource dataSource, PetOperationalMetrics metrics) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Best-effort refresh; a transient DB error leaves the last known gauges intact. */
    public void sample() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SNAPSHOT);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) return;
            set(rows, 1, PetOperationalMetrics.Gauge.SUBSCRIPTIONS_ACTIVE);
            set(rows, 2, PetOperationalMetrics.Gauge.SUBSCRIPTIONS_INACTIVE);
            set(rows, 3, PetOperationalMetrics.Gauge.PETS_HELD);
            set(rows, 4, PetOperationalMetrics.Gauge.PETS_PLACED);
            set(rows, 5, PetOperationalMetrics.Gauge.PETS_SLEEPING);
            set(rows, 6, PetOperationalMetrics.Gauge.CAT_SCALE_SMALL);
            set(rows, 7, PetOperationalMetrics.Gauge.CAT_SCALE_MEDIUM);
            set(rows, 8, PetOperationalMetrics.Gauge.CAT_SCALE_LARGE);
            set(rows, 9, PetOperationalMetrics.Gauge.DOG_SCALE_SMALL);
            set(rows, 10, PetOperationalMetrics.Gauge.DOG_SCALE_MEDIUM);
            set(rows, 11, PetOperationalMetrics.Gauge.DOG_SCALE_LARGE);
            set(rows, 12, PetOperationalMetrics.Gauge.SUBSCRIBERS_USAGE_0_10);
            set(rows, 13, PetOperationalMetrics.Gauge.SUBSCRIBERS_USAGE_11_25);
            set(rows, 14, PetOperationalMetrics.Gauge.SUBSCRIBERS_USAGE_26_50);
            set(rows, 15, PetOperationalMetrics.Gauge.SUBSCRIBERS_USAGE_51_100);
            set(rows, 16, PetOperationalMetrics.Gauge.SUBSCRIBERS_USAGE_OVER_100);
            set(rows, 17, PetOperationalMetrics.Gauge.EMBEDDING_QUEUE_DEPTH);
        } catch (SQLException | RuntimeException failure) {
            System.getLogger(JdbcOperationalMetricsSampler.class.getName()).log(
                    System.Logger.Level.WARNING, "Operational metrics refresh unavailable", failure);
        }
    }

    private void set(ResultSet rows, int column, PetOperationalMetrics.Gauge gauge) throws SQLException {
        metrics.set(gauge, Math.max(0L, rows.getLong(column)));
    }
}
