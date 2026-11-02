ALTER TABLE ai_usage MODIFY COLUMN operation
    ENUM('DIALOGUE', 'CONSOLIDATION', 'EMBEDDING', 'MODERATION', 'PLANNING') NOT NULL;

CREATE TABLE pet_decisions (
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    source_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    intent VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    utterance VARCHAR(160) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    next_model_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_decisions_pet FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE,
    CONSTRAINT fk_decisions_event FOREIGN KEY (source_event_id, pet_id)
        REFERENCES pet_events(event_id, pet_id) ON DELETE CASCADE,
    KEY ix_decisions_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
