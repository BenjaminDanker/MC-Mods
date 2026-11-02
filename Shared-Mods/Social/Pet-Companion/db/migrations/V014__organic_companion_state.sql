ALTER TABLE pet_traits
    ADD COLUMN self_model_json LONGTEXT NULL,
    ADD COLUMN self_model_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN self_model_updated_at DATETIME(6) NULL;

ALTER TABLE pet_mood
    ADD COLUMN affect_valence SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN affect_valence_intensity TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN affect_alertness SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN affect_alertness_intensity TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN affect_social_stance SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN affect_social_intensity TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN affect_trigger VARCHAR(64) NULL,
    ADD COLUMN affect_target_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN affect_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN owner_intent_guess VARCHAR(80) NULL,
    ADD COLUMN owner_emotion_guess VARCHAR(40) NULL,
    ADD COLUMN owner_interpretation_confidence DECIMAL(4,3) NULL,
    ADD COLUMN owner_interpretation_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN owner_interpretation_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT chk_mood_affect_valence CHECK (affect_valence BETWEEN -100 AND 100),
    ADD CONSTRAINT chk_mood_affect_valence_intensity CHECK (affect_valence_intensity <= 100),
    ADD CONSTRAINT chk_mood_affect_alertness CHECK (affect_alertness BETWEEN -100 AND 100),
    ADD CONSTRAINT chk_mood_affect_alertness_intensity CHECK (affect_alertness_intensity <= 100),
    ADD CONSTRAINT chk_mood_affect_social CHECK (affect_social_stance BETWEEN -100 AND 100),
    ADD CONSTRAINT chk_mood_affect_social_intensity CHECK (affect_social_intensity <= 100),
    ADD CONSTRAINT chk_owner_interpretation_confidence CHECK (
        owner_interpretation_confidence IS NULL OR owner_interpretation_confidence BETWEEN 0 AND 1),
    ADD KEY ix_mood_affect_target (affect_target_uuid);

ALTER TABLE long_term_memories
    ADD COLUMN memory_kind ENUM('FACT','EPISODE','PREFERENCE','RELATIONSHIP_EVENT','OPEN_THREAD','CORRECTION','SUMMARY') NOT NULL DEFAULT 'FACT',
    ADD COLUMN confidence DECIMAL(4,3) NOT NULL DEFAULT 0.500,
    ADD COLUMN salience TINYINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN emotional_significance TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN supersedes_memory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD KEY ix_memory_supersedes (supersedes_memory_id, pet_id),
    ADD CONSTRAINT fk_memory_supersedes FOREIGN KEY (supersedes_memory_id, pet_id)
        REFERENCES long_term_memories (memory_id, pet_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_memory_confidence CHECK (confidence BETWEEN 0 AND 1),
    ADD CONSTRAINT chk_memory_salience CHECK (salience <= 5),
    ADD CONSTRAINT chk_memory_emotional_significance CHECK (emotional_significance <= 5);

ALTER TABLE pet_events
    ADD COLUMN memory_kind ENUM('FACT','EPISODE','PREFERENCE','RELATIONSHIP_EVENT','OPEN_THREAD','CORRECTION','SUMMARY') NOT NULL DEFAULT 'EPISODE',
    ADD COLUMN source_provenance ENUM('VERIFIED_OBSERVATION','OWNER_STATEMENT','INFERENCE','CONSOLIDATED') NOT NULL DEFAULT 'VERIFIED_OBSERVATION',
    ADD COLUMN confidence DECIMAL(4,3) NOT NULL DEFAULT 0.900,
    ADD COLUMN salience TINYINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN emotional_significance TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN participants_json LONGTEXT NULL,
    ADD COLUMN location_json LONGTEXT NULL,
    ADD COLUMN memory_candidate_text VARCHAR(500) NULL,
    ADD COLUMN memory_candidate_confidence DECIMAL(4,3) NULL,
    ADD CONSTRAINT chk_event_confidence CHECK (confidence BETWEEN 0 AND 1),
    ADD CONSTRAINT chk_event_salience CHECK (salience <= 5),
    ADD CONSTRAINT chk_event_emotional_significance CHECK (emotional_significance <= 5),
    ADD CONSTRAINT chk_event_candidate_confidence CHECK (
        memory_candidate_confidence IS NULL OR memory_candidate_confidence BETWEEN 0 AND 1);

UPDATE pet_events SET source_provenance='OWNER_STATEMENT', confidence=1.000
WHERE event_type='DIALOGUE';

CREATE TABLE pet_open_threads (
    thread_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    summary VARCHAR(500) NOT NULL,
    source_kind ENUM('OWNER_REPORTED','PET_WITNESSED','PET_INTEREST','MODEL_INFERRED','CONSOLIDATED') NOT NULL,
    source_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    thread_key VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    location_server VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    location_dimension VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    location_cue VARCHAR(191) NULL,
    state ENUM('OPEN','ACTIVE','PAUSED','COMPLETED','FORGOTTEN') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_referenced_at DATETIME(6) NULL,
    next_cue VARCHAR(191) NULL,
    expires_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (thread_id),
    KEY ix_pet_threads_active (pet_id,state,expires_at),
    KEY ix_pet_threads_cue (pet_id,location_server,location_dimension,state),
    CONSTRAINT fk_pet_threads_pet FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pet_threads_source FOREIGN KEY (source_event_id,pet_id)
        REFERENCES pet_events(event_id,pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_threads_version CHECK (version >= 1),
    CONSTRAINT chk_pet_threads_summary CHECK (CHAR_LENGTH(TRIM(summary)) BETWEEN 1 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
