-- AI Pet System baseline schema for MariaDB.
--
-- Forward-only migration. Apply this file exactly once with an external migration
-- runner or an operator-controlled MariaDB client; the application must not apply
-- schema changes during startup.
--
-- UUIDs use lowercase, hyphenated CHAR(36) values with an ASCII binary collation.
-- JSON documents use LONGTEXT so this migration does not depend on MariaDB's JSON
-- alias/version. The service must validate canonical UUIDs and JSON at its boundary
-- even on MariaDB releases that parse but do not enforce CHECK constraints.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE pets (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(64) NOT NULL,
    mob_type ENUM('minecraft:cat', 'minecraft:wolf') NOT NULL,
    variant_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scale DECIMAL(5,3) UNSIGNED NOT NULL,
    appearance_seed BIGINT NULL,

    placement_state ENUM('HELD', 'PLACED', 'TRANSFERRING') NOT NULL,
    placed_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    placed_dimension VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    placed_x DOUBLE NULL,
    placed_y DOUBLE NULL,
    placed_z DOUBLE NULL,
    entity_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,

    transfer_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    transfer_source_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    transfer_source_entity_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    transfer_destination_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    transfer_started_at DATETIME(6) NULL,
    transfer_expires_at DATETIME(6) NULL,

    record_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_materialized_at DATETIME(6) NULL,
    last_authority_heartbeat_at DATETIME(6) NULL,

    PRIMARY KEY (pet_id),
    UNIQUE KEY uq_pets_owner_uuid (owner_uuid),
    UNIQUE KEY uq_pets_pet_owner (pet_id, owner_uuid),
    UNIQUE KEY uq_pets_entity_uuid (entity_uuid),
    UNIQUE KEY uq_pets_transfer_id (transfer_id),
    KEY ix_pets_placement_location (placement_state, placed_server, placed_dimension),
    KEY ix_pets_transfer_expiry (placement_state, transfer_expires_at),
    KEY ix_pets_authority_heartbeat (placement_state, last_authority_heartbeat_at),

    CONSTRAINT chk_pets_pet_uuid CHECK (
        pet_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pets_owner_uuid CHECK (
        owner_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pets_entity_uuid CHECK (
        entity_uuid IS NULL OR entity_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pets_transfer_uuid CHECK (
        transfer_id IS NULL OR transfer_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pets_transfer_entity_uuid CHECK (
        transfer_source_entity_uuid IS NULL OR transfer_source_entity_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pets_name_nonempty CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 64),
    -- Configured cat/wolf ranges are narrower; this is a defensive storage bound.
    CONSTRAINT chk_pets_scale CHECK (scale BETWEEN 0.100 AND 2.000),
    CONSTRAINT chk_pets_record_version CHECK (record_version >= 0),
    CONSTRAINT chk_pets_placement_fields CHECK (
        (
            placement_state = 'HELD'
            AND placed_server IS NULL
            AND placed_dimension IS NULL
            AND placed_x IS NULL
            AND placed_y IS NULL
            AND placed_z IS NULL
            AND entity_uuid IS NULL
            AND transfer_id IS NULL
            AND transfer_source_server IS NULL
            AND transfer_source_entity_uuid IS NULL
            AND transfer_destination_server IS NULL
            AND transfer_started_at IS NULL
            AND transfer_expires_at IS NULL
        )
        OR
        (
            placement_state = 'PLACED'
            AND placed_server IS NOT NULL
            AND placed_dimension IS NOT NULL
            AND placed_x IS NOT NULL
            AND placed_y IS NOT NULL
            AND placed_z IS NOT NULL
            AND transfer_id IS NULL
            AND transfer_source_server IS NULL
            AND transfer_source_entity_uuid IS NULL
            AND transfer_destination_server IS NULL
            AND transfer_started_at IS NULL
            AND transfer_expires_at IS NULL
        )
        OR
        (
            placement_state = 'TRANSFERRING'
            AND placed_server IS NULL
            AND placed_dimension IS NULL
            AND placed_x IS NULL
            AND placed_y IS NULL
            AND placed_z IS NULL
            AND entity_uuid IS NULL
            AND transfer_id IS NOT NULL
            AND transfer_source_server IS NOT NULL
            AND transfer_source_entity_uuid IS NOT NULL
            AND transfer_destination_server IS NOT NULL
            AND transfer_started_at IS NOT NULL
            AND transfer_expires_at IS NOT NULL
            AND transfer_expires_at > transfer_started_at
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pet_traits (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    curiosity TINYINT UNSIGNED NOT NULL,
    boldness TINYINT UNSIGNED NOT NULL,
    playfulness TINYINT UNSIGNED NOT NULL,
    expressiveness TINYINT UNSIGNED NOT NULL,
    independence TINYINT UNSIGNED NOT NULL,
    attachment TINYINT UNSIGNED NOT NULL,
    trust TINYINT UNSIGNED NOT NULL,
    security TINYINT UNSIGNED NOT NULL,
    relationship_summary VARCHAR(2000) NOT NULL,
    summary_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (pet_id),
    CONSTRAINT fk_pet_traits_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_traits_curiosity CHECK (curiosity <= 100),
    CONSTRAINT chk_pet_traits_boldness CHECK (boldness <= 100),
    CONSTRAINT chk_pet_traits_playfulness CHECK (playfulness <= 100),
    CONSTRAINT chk_pet_traits_expressive CHECK (expressiveness <= 100),
    CONSTRAINT chk_pet_traits_independence CHECK (independence <= 100),
    CONSTRAINT chk_pet_traits_attachment CHECK (attachment <= 100),
    CONSTRAINT chk_pet_traits_trust CHECK (trust <= 100),
    CONSTRAINT chk_pet_traits_security CHECK (security <= 100),
    CONSTRAINT chk_pet_traits_summary_version CHECK (summary_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pet_mood (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content TINYINT UNSIGNED NOT NULL,
    excited TINYINT UNSIGNED NOT NULL,
    anxious TINYINT UNSIGNED NOT NULL,
    tired TINYINT UNSIGNED NOT NULL,
    last_decay_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (pet_id),
    CONSTRAINT fk_pet_mood_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_mood_content CHECK (content <= 100),
    CONSTRAINT chk_pet_mood_excited CHECK (excited <= 100),
    CONSTRAINT chk_pet_mood_anxious CHECK (anxious <= 100),
    CONSTRAINT chk_pet_mood_tired CHECK (tired <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pet_sleep_state (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sleeping BOOLEAN NOT NULL DEFAULT FALSE,
    sleep_started_at DATETIME(6) NULL,
    sleep_ends_at DATETIME(6) NULL,
    last_sleep_completed_at DATETIME(6) NULL,
    forced_sleep_due_at DATETIME(6) NOT NULL,
    owner_network_online BOOLEAN NOT NULL DEFAULT FALSE,
    owner_last_logout_at DATETIME(6) NULL,
    owner_absence_session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    absence_sleep_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    last_presence_update_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (pet_id),
    KEY ix_pet_sleep_sleep_due (sleeping, sleep_ends_at),
    KEY ix_pet_sleep_forced_due (sleeping, forced_sleep_due_at),
    KEY ix_pet_sleep_absence_due (owner_network_online, absence_sleep_triggered, owner_last_logout_at),
    CONSTRAINT fk_pet_sleep_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_sleep_sleeping CHECK (sleeping IN (0, 1)),
    CONSTRAINT chk_pet_sleep_online CHECK (owner_network_online IN (0, 1)),
    CONSTRAINT chk_pet_sleep_triggered CHECK (absence_sleep_triggered IN (0, 1)),
    CONSTRAINT chk_pet_sleep_session_uuid CHECK (
        owner_absence_session_id IS NULL OR owner_absence_session_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pet_sleep_window CHECK (
        (sleeping = FALSE AND sleep_started_at IS NULL AND sleep_ends_at IS NULL)
        OR
        (sleeping = TRUE AND sleep_started_at IS NOT NULL AND sleep_ends_at IS NOT NULL AND sleep_ends_at > sleep_started_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE jobs (
    job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    job_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    idempotency_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status ENUM('PENDING', 'RUNNING', 'RETRY', 'SUCCEEDED', 'FAILED', 'CANCELED') NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    not_before DATETIME(6) NOT NULL,
    locked_by VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    locked_until DATETIME(6) NULL,
    last_error_sanitized VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,

    PRIMARY KEY (job_id),
    UNIQUE KEY uq_jobs_job_pet (job_id, pet_id),
    UNIQUE KEY uq_jobs_idempotency_key (idempotency_key),
    KEY ix_jobs_claim (status, not_before, locked_until),
    KEY ix_jobs_pet_type_created (pet_id, job_type, created_at),
    CONSTRAINT fk_jobs_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_jobs_job_uuid CHECK (
        job_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_jobs_lock_pair CHECK (
        (locked_by IS NULL AND locked_until IS NULL)
        OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)
    ),
    CONSTRAINT chk_jobs_completion CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'CANCELED') AND completed_at IS NOT NULL)
        OR (status IN ('PENDING', 'RUNNING', 'RETRY') AND completed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE pet_events (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    source_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_dimension VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    importance ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    raw_player_text VARCHAR(2000) NULL,
    raw_pet_reply VARCHAR(2000) NULL,
    metadata_json LONGTEXT NULL,
    short_term_expires_at DATETIME(6) NULL,
    prompt_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    consolidation_status ENUM('PENDING', 'SELECTED', 'CONSOLIDATED', 'DISCARDED') NOT NULL DEFAULT 'PENDING',
    consolidation_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (event_id),
    UNIQUE KEY uq_pet_events_event_pet (event_id, pet_id),
    KEY ix_pet_events_pet_occurred (pet_id, occurred_at),
    KEY ix_pet_events_prompt_expiry (pet_id, prompt_eligible, short_term_expires_at),
    KEY ix_pet_events_consolidation (pet_id, consolidation_status, occurred_at),
    KEY ix_pet_events_owner_occurred (owner_uuid, occurred_at),
    KEY ix_pet_events_job (consolidation_job_id),
    CONSTRAINT fk_pet_events_pet_owner FOREIGN KEY (pet_id, owner_uuid)
        REFERENCES pets (pet_id, owner_uuid) ON DELETE RESTRICT,
    CONSTRAINT fk_pet_events_job FOREIGN KEY (consolidation_job_id, pet_id)
        REFERENCES jobs (job_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_events_event_uuid CHECK (
        event_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pet_events_prompt CHECK (prompt_eligible IN (0, 1)),
    CONSTRAINT chk_pet_events_expiry CHECK (
        short_term_expires_at IS NULL OR short_term_expires_at >= occurred_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE long_term_memories (
    memory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    memory_text VARCHAR(4000) NOT NULL,
    memory_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    importance ENUM('MEDIUM', 'HIGH') NOT NULL,
    emotional_valence DECIMAL(5,4) NULL,
    emotion_tags LONGTEXT NULL,
    entity_tags LONGTEXT NULL,
    location_tags LONGTEXT NULL,
    embedding_status ENUM('PENDING', 'READY', 'FAILED') NOT NULL DEFAULT 'PENDING',
    embedding_model VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    embedding_reference VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_reinforced_at DATETIME(6) NULL,
    last_recalled_at DATETIME(6) NULL,
    recall_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (memory_id),
    UNIQUE KEY uq_memories_memory_pet (memory_id, pet_id),
    KEY ix_memories_pet_active_created (pet_id, active, created_at),
    KEY ix_memories_embedding (embedding_status, updated_at),
    KEY ix_memories_pet_recalled (pet_id, last_recalled_at),
    CONSTRAINT fk_memories_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_memories_memory_uuid CHECK (
        memory_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_memories_text_nonempty CHECK (CHAR_LENGTH(TRIM(memory_text)) BETWEEN 1 AND 4000),
    CONSTRAINT chk_memories_version CHECK (memory_version >= 1),
    CONSTRAINT chk_memories_valence CHECK (
        emotional_valence IS NULL OR emotional_valence BETWEEN -1.0000 AND 1.0000
    ),
    CONSTRAINT chk_memories_active CHECK (active IN (0, 1)),
    CONSTRAINT chk_memories_embedding_ready CHECK (
        embedding_status <> 'READY'
        OR (embedding_model IS NOT NULL AND embedding_reference IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Relational source references remain pet-scoped; event text can be redacted in
-- place after retention without deleting the grounding relationship.
CREATE TABLE long_term_memory_source_events (
    memory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (memory_id, pet_id, source_event_id),
    KEY ix_memory_sources_event (source_event_id, pet_id),
    CONSTRAINT fk_memory_sources_memory FOREIGN KEY (memory_id, pet_id)
        REFERENCES long_term_memories (memory_id, pet_id) ON DELETE CASCADE,
    CONSTRAINT fk_memory_sources_event FOREIGN KEY (source_event_id, pet_id)
        REFERENCES pet_events (event_id, pet_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Required audit trail when a consolidated card is reinforced or rewritten.
CREATE TABLE long_term_memory_revisions (
    revision_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    memory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prior_version BIGINT UNSIGNED NOT NULL,
    prior_memory_text VARCHAR(4000) NOT NULL,
    replacement_memory_text VARCHAR(4000) NOT NULL,
    changed_by_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    changed_by_job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (revision_id),
    UNIQUE KEY uq_memory_revisions_version (memory_id, pet_id, prior_version),
    KEY ix_memory_revisions_event (changed_by_event_id, pet_id),
    KEY ix_memory_revisions_job (changed_by_job_id, pet_id),
    CONSTRAINT fk_memory_revisions_memory FOREIGN KEY (memory_id, pet_id)
        REFERENCES long_term_memories (memory_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_memory_revisions_event FOREIGN KEY (changed_by_event_id, pet_id)
        REFERENCES pet_events (event_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_memory_revisions_job FOREIGN KEY (changed_by_job_id, pet_id)
        REFERENCES jobs (job_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_memory_revisions_version CHECK (prior_version >= 1),
    CONSTRAINT chk_memory_revisions_source CHECK (
        changed_by_event_id IS NOT NULL OR changed_by_job_id IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trait_change_audit (
    change_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    job_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    trait_name ENUM(
        'curiosity',
        'boldness',
        'playfulness',
        'expressiveness',
        'independence',
        'attachment',
        'trust',
        'security'
    ) NOT NULL,
    old_value TINYINT UNSIGNED NOT NULL,
    proposed_delta SMALLINT NOT NULL,
    applied_delta SMALLINT NOT NULL,
    new_value TINYINT UNSIGNED NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (change_id),
    UNIQUE KEY uq_trait_audit_event (pet_id, event_id, trait_name),
    UNIQUE KEY uq_trait_audit_job (pet_id, job_id, trait_name),
    KEY ix_trait_audit_pet_created (pet_id, created_at),
    KEY ix_trait_audit_event (event_id),
    KEY ix_trait_audit_job (job_id),
    CONSTRAINT fk_trait_audit_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_trait_audit_event FOREIGN KEY (event_id, pet_id)
        REFERENCES pet_events (event_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_trait_audit_job FOREIGN KEY (job_id, pet_id)
        REFERENCES jobs (job_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_trait_audit_change_uuid CHECK (
        change_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_trait_audit_source CHECK (event_id IS NOT NULL OR job_id IS NOT NULL),
    CONSTRAINT chk_trait_audit_old CHECK (old_value <= 100),
    CONSTRAINT chk_trait_audit_new CHECK (new_value <= 100),
    CONSTRAINT chk_trait_audit_delta CHECK (
        proposed_delta BETWEEN -100 AND 100
        AND applied_delta BETWEEN -100 AND 100
        AND CAST(new_value AS SIGNED) = CAST(old_value AS SIGNED) + applied_delta
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE subscriptions (
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stripe_customer_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    stripe_subscription_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    stripe_price_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status ENUM('ACTIVE', 'TRIALING', 'PAST_DUE', 'CANCELED', 'INACTIVE') NOT NULL DEFAULT 'INACTIVE',
    ai_access_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    current_period_start DATETIME(6) NULL,
    current_period_end DATETIME(6) NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    grace_ends_at DATETIME(6) NULL,
    last_stripe_event_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (owner_uuid),
    UNIQUE KEY uq_subscriptions_customer (stripe_customer_id),
    UNIQUE KEY uq_subscriptions_subscription (stripe_subscription_id),
    KEY ix_subscriptions_access_status (ai_access_enabled, status),
    KEY ix_subscriptions_period_end (current_period_end),
    CONSTRAINT chk_subscriptions_owner_uuid CHECK (
        owner_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_subscriptions_access CHECK (ai_access_enabled IN (0, 1)),
    CONSTRAINT chk_subscriptions_cancel CHECK (cancel_at_period_end IN (0, 1)),
    CONSTRAINT chk_subscriptions_period CHECK (
        current_period_start IS NULL
        OR current_period_end IS NULL
        OR current_period_end >= current_period_start
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stripe_webhook_events (
    stripe_event_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stripe_created_at DATETIME(6) NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    status ENUM('RECEIVED', 'PROCESSED', 'FAILED') NOT NULL DEFAULT 'RECEIVED',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    last_error_sanitized VARCHAR(1000) NULL,

    PRIMARY KEY (stripe_event_id),
    KEY ix_stripe_events_status_received (status, received_at),
    KEY ix_stripe_events_created (stripe_created_at),
    CONSTRAINT chk_stripe_events_digest CHECK (
        payload_sha256 IS NULL OR payload_sha256 REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_stripe_events_processed CHECK (
        status <> 'PROCESSED' OR processed_at IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Operations are retained for audit and safe replay. Only SUCCEEDED rows populate
-- consumed_period_key; MariaDB unique indexes permit multiple NULL values, so
-- STARTED/FAILED attempts neither consume nor block the monthly entitlement.
CREATE TABLE pet_recall_usage (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    period_key CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    consumed_period_key CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    source_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    source_dimension VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    destination_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    destination_dimension VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status ENUM('STARTED', 'SUCCEEDED', 'FAILED') NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (operation_id),
    UNIQUE KEY uq_pet_recall_consumed_period (pet_id, consumed_period_key),
    KEY ix_pet_recall_period_status (pet_id, period_key, status),
    KEY ix_pet_recall_used_at (used_at),
    CONSTRAINT fk_pet_recall_pet FOREIGN KEY (pet_id)
        REFERENCES pets (pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_recall_period CHECK (
        period_key REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
    ),
    CONSTRAINT chk_pet_recall_consumed_period CHECK (
        consumed_period_key IS NULL
        OR consumed_period_key REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'
    ),
    CONSTRAINT chk_pet_recall_operation_uuid CHECK (
        operation_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_pet_recall_consumption CHECK (
        (
            status = 'STARTED'
            AND consumed_period_key IS NULL
            AND used_at IS NULL
            AND completed_at IS NULL
        )
        OR
        (
            status = 'FAILED'
            AND consumed_period_key IS NULL
            AND used_at IS NULL
            AND completed_at IS NOT NULL
        )
        OR
        (
            status = 'SUCCEEDED'
            AND consumed_period_key = period_key
            AND used_at IS NOT NULL
            AND completed_at IS NOT NULL
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_usage (
    call_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation ENUM('DIALOGUE', 'CONSOLIDATION', 'EMBEDDING', 'MODERATION') NOT NULL,
    model VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    input_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    cached_input_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    output_tokens INT UNSIGNED NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(18,8) UNSIGNED NOT NULL DEFAULT 0,
    latency_ms BIGINT UNSIGNED NULL,
    status ENUM('STARTED', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED') NOT NULL,
    error_category VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (call_id),
    UNIQUE KEY uq_ai_usage_request (request_id),
    UNIQUE KEY uq_ai_usage_provider (provider_id),
    KEY ix_ai_usage_pet_created (pet_id, created_at),
    KEY ix_ai_usage_owner_created (owner_uuid, created_at),
    KEY ix_ai_usage_operation_status (operation, status, created_at),
    CONSTRAINT fk_ai_usage_pet_owner FOREIGN KEY (pet_id, owner_uuid)
        REFERENCES pets (pet_id, owner_uuid) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_usage_call_uuid CHECK (
        call_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_ai_usage_request_uuid CHECK (
        request_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_ai_usage_cached_tokens CHECK (cached_input_tokens <= input_tokens)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_link_tokens (
    link_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (link_token_hash),
    KEY ix_account_link_owner_created (owner_uuid, created_at),
    KEY ix_account_link_expiry (expires_at, consumed_at),
    CONSTRAINT chk_account_link_hash CHECK (
        link_token_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_account_link_owner_uuid CHECK (
        owner_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_account_link_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_account_link_consumed CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Durable idempotency ledger for authenticated mutation endpoints. The request
-- fingerprint prevents reuse of one key for a different payload. Response data is
-- deliberately bounded by service policy and expires via a cleanup job; raw model
-- prompts and secrets must never be stored here.
CREATE TABLE idempotency_requests (
    scope VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    owner_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status ENUM('IN_PROGRESS', 'SUCCEEDED', 'FAILED') NOT NULL,
    http_status SMALLINT UNSIGNED NULL,
    result_resource_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    response_json LONGTEXT NULL,
    error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    locked_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,

    PRIMARY KEY (scope, idempotency_key),
    KEY ix_idempotency_expiry (expires_at),
    KEY ix_idempotency_owner_created (owner_uuid, created_at),
    KEY ix_idempotency_pet_created (pet_id, created_at),
    CONSTRAINT fk_idempotency_pet_owner FOREIGN KEY (pet_id, owner_uuid)
        REFERENCES pets (pet_id, owner_uuid) ON DELETE RESTRICT,
    CONSTRAINT chk_idempotency_fingerprint CHECK (
        request_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_idempotency_pet_owner CHECK (pet_id IS NULL OR owner_uuid IS NOT NULL),
    CONSTRAINT chk_idempotency_pet_uuid CHECK (
        pet_id IS NULL OR pet_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_idempotency_owner_uuid CHECK (
        owner_uuid IS NULL OR owner_uuid REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ),
    CONSTRAINT chk_idempotency_result CHECK (
        status = 'IN_PROGRESS' OR http_status IS NOT NULL
    ),
    CONSTRAINT chk_idempotency_http_status CHECK (
        http_status IS NULL OR http_status BETWEEN 100 AND 599
    ),
    CONSTRAINT chk_idempotency_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_idempotency_lock CHECK (
        locked_until IS NULL OR locked_until > created_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
