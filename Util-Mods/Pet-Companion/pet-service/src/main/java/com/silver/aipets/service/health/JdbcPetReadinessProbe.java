package com.silver.aipets.service.health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Readiness requires a responsive DB and the current V001-V016 schema. */
public final class JdbcPetReadinessProbe implements PetReadinessProbe {
    public static final int EXPECTED_CURRENT_TABLE_COUNT = 20;
    public static final int EXPECTED_V002_RECALL_COLUMN_COUNT = 3;
    public static final int EXPECTED_V003_CHECKOUT_COLUMN_COUNT = 2;
    public static final int EXPECTED_V004_CONTEXT_COLUMN_COUNT = 1;
    public static final int EXPECTED_V005_PENDING_ADOPTION_COLUMN_COUNT = 17;
    public static final int EXPECTED_V007_PENDING_INTENT_PRIMARY_COUNT = 1;
    public static final int EXPECTED_V008_REPLACEMENT_STATE_COLUMN_COUNT = 1;
    public static final int EXPECTED_V009_MEMORY_COLUMN_COUNT = 2;
    public static final int EXPECTED_V010_PLANNING_OPERATION_COUNT = 1;
    public static final int EXPECTED_V011_SLEEP_COLUMN_COUNT = 3;
    public static final int EXPECTED_V012_INTENTION_COLUMN_COUNT = 3;
    public static final int EXPECTED_V013_RELATIONSHIP_COLUMN_COUNT = 6;
    public static final int EXPECTED_V014_ORGANIC_COLUMN_COUNT = 32;
    public static final int EXPECTED_V014_THREAD_COLUMN_COUNT = 16;

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
                   'account_link_tokens', 'idempotency_requests', 'pending_adoptions',
                   'pet_decisions', 'pet_relationship_evidence','pet_open_threads')) AS table_count,
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
                 AS checkout_column_count,
              (SELECT COUNT(*)
               FROM information_schema.columns
               WHERE table_schema = DATABASE()
               AND table_name = 'ai_usage'
               AND column_name = 'context_json') AS context_column_count,
              (SELECT COUNT(*)
               FROM information_schema.columns
               WHERE table_schema = DATABASE()
                 AND table_name = 'pending_adoptions'
                 AND column_name IN (
                   'owner_uuid', 'intent_id', 'species', 'pet_name', 'state', 'created_at',
                   'expires_at', 'checkout_started_at', 'hard_expires_at', 'account_link_hash',
                   'stripe_checkout_session_id', 'checkout_launch_claimed_at',
                   'checkout_completed_at', 'completed_pet_id', 'completed_at',
                   'notification_pending', 'notification_acknowledged_at'))
                 AS pending_adoption_column_count,
              (SELECT COUNT(*)
               FROM information_schema.statistics
               WHERE table_schema = DATABASE()
                 AND table_name = 'pending_adoptions'
                 AND index_name = 'PRIMARY'
                 AND column_name = 'intent_id') AS pending_intent_primary_count
              ,(SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'pending_adoptions'
                  AND column_name = 'state'
                  AND LOCATE('REPLACEMENT_PENDING', UPPER(column_type)) > 0
                  AND LOCATE('WAITING_FOR_ENTITLEMENT', UPPER(column_type)) > 0)
                 AS replacement_state_column_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='long_term_memories'
                 AND column_name IN ('provenance','fact_key')) AS memory_column_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='ai_usage'
                 AND column_name='operation'
                 AND LOCATE('PLANNING', UPPER(column_type)) > 0)
                 AS planning_operation_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='pet_sleep_state'
                 AND column_name IN ('fatigue','fatigue_updated_at','awake_since'))
                 AS sleep_column_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='pet_decisions'
                 AND column_name IN ('motivation_memory_id','entity_uuid','decision_version'))
                 AS intention_column_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND table_name='pet_relationship_evidence'
                 AND column_name IN ('interaction_id','event_id','pet_id','relationship_type','strength','occurred_at'))
                 AS relationship_evidence_column_count,
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema=DATABASE() AND
                 ((table_name='pet_traits' AND column_name IN ('self_model_json','self_model_version','self_model_updated_at'))
                  OR (table_name='pet_mood' AND column_name IN ('affect_valence','affect_valence_intensity','affect_alertness',
                    'affect_alertness_intensity','affect_social_stance','affect_social_intensity','affect_trigger',
                    'affect_target_uuid','affect_updated_at','affect_event_id','owner_intent_guess','owner_emotion_guess',
                    'owner_interpretation_confidence','owner_interpretation_event_id','owner_interpretation_updated_at'))
                  OR (table_name='pet_events' AND column_name IN ('memory_kind','source_provenance','confidence','salience',
                    'emotional_significance','participants_json','location_json','memory_candidate_text',
                    'memory_candidate_confidence'))
                  OR (table_name='long_term_memories' AND column_name IN ('memory_kind','confidence','salience',
                    'emotional_significance','supersedes_memory_id')))) AS organic_column_count
              ,(SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='pet_open_threads'
                  AND column_name IN ('thread_id','pet_id','summary','source_kind','source_event_id','thread_key',
                    'location_server','location_dimension','location_cue','state','created_at',
                    'last_referenced_at','next_cue','expires_at','version','updated_at')) AS thread_column_count
            """;

    private final DataSource dataSource;
    private final int validationTimeoutSeconds;
    private final boolean stripeConfigured;
    private final boolean vectorConfigured;
    private final boolean modelConfigured;

    public JdbcPetReadinessProbe(DataSource dataSource, long validationTimeoutMs) {
        this(dataSource, validationTimeoutMs, false);
    }

    public JdbcPetReadinessProbe(
            DataSource dataSource, long validationTimeoutMs, boolean stripeConfigured) {
        this(dataSource, validationTimeoutMs, stripeConfigured, false, false);
    }

    public JdbcPetReadinessProbe(
            DataSource dataSource,
            long validationTimeoutMs,
            boolean stripeConfigured,
            boolean vectorConfigured,
            boolean modelConfigured) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        if (validationTimeoutMs < 1) {
            throw new IllegalArgumentException("validationTimeoutMs must be positive");
        }
        this.validationTimeoutSeconds = Math.max(1, Math.toIntExact(
                Math.ceilDiv(validationTimeoutMs, 1_000L)));
        this.stripeConfigured = stripeConfigured;
        this.vectorConfigured = vectorConfigured;
        this.modelConfigured = modelConfigured;
    }

    @Override
    public PetReadinessSnapshot probe() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(validationTimeoutSeconds)) {
                return PetReadinessSnapshot.databaseDown(
                        stripeConfigured, vectorConfigured, modelConfigured);
            }
            try (PreparedStatement statement = connection.prepareStatement(SCHEMA_COUNTS);
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return PetReadinessSnapshot.migrationsOutdated(
                            stripeConfigured, vectorConfigured, modelConfigured);
                }
                int tableCount = rows.getInt(1);
                int recallColumnCount = rows.getInt(2);
                int checkoutColumnCount = rows.getInt(3);
                int contextColumnCount = rows.getInt(4);
                int pendingAdoptionColumnCount = rows.getInt(5);
                int pendingIntentPrimaryCount = rows.getInt(6);
                int replacementStateColumnCount = rows.getInt(7);
                int memoryColumnCount = rows.getInt(8);
                int planningOperationCount = rows.getInt(9);
                int sleepColumnCount = rows.getInt(10);
                int intentionColumnCount = rows.getInt(11);
                int relationshipEvidenceColumnCount = rows.getInt(12);
                int organicColumnCount = rows.getInt(13);
                int threadColumnCount = rows.getInt(14);
                return tableCount == EXPECTED_CURRENT_TABLE_COUNT
                                && recallColumnCount == EXPECTED_V002_RECALL_COLUMN_COUNT
                                && checkoutColumnCount == EXPECTED_V003_CHECKOUT_COLUMN_COUNT
                                && contextColumnCount == EXPECTED_V004_CONTEXT_COLUMN_COUNT
                                && pendingAdoptionColumnCount == EXPECTED_V005_PENDING_ADOPTION_COLUMN_COUNT
                                && pendingIntentPrimaryCount == EXPECTED_V007_PENDING_INTENT_PRIMARY_COUNT
                                && replacementStateColumnCount
                                    == EXPECTED_V008_REPLACEMENT_STATE_COLUMN_COUNT
                                && memoryColumnCount == EXPECTED_V009_MEMORY_COLUMN_COUNT
                                && planningOperationCount == EXPECTED_V010_PLANNING_OPERATION_COUNT
                                && sleepColumnCount == EXPECTED_V011_SLEEP_COLUMN_COUNT
                                && intentionColumnCount == EXPECTED_V012_INTENTION_COLUMN_COUNT
                                && relationshipEvidenceColumnCount == EXPECTED_V013_RELATIONSHIP_COLUMN_COUNT
                                && organicColumnCount == EXPECTED_V014_ORGANIC_COLUMN_COUNT
                                && threadColumnCount == EXPECTED_V014_THREAD_COLUMN_COUNT
                        ? PetReadinessSnapshot.fullyReady(
                                stripeConfigured, vectorConfigured, modelConfigured)
                        : PetReadinessSnapshot.migrationsOutdated(
                                stripeConfigured, vectorConfigured, modelConfigured);
            }
        } catch (SQLException | RuntimeException failure) {
            return PetReadinessSnapshot.databaseDown(
                    stripeConfigured, vectorConfigured, modelConfigured);
        }
    }
}
