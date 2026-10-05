CREATE TABLE pet_relationship_evidence (
    interaction_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pet_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relationship_type ENUM(
        'WARMTH', 'TRUST', 'BOUNDARY', 'REPAIR', 'IRRITATION', 'FEAR', 'DISTANCE', 'CURIOSITY'
    ) NOT NULL,
    strength TINYINT UNSIGNED NOT NULL,
    occurred_at DATETIME(6) NOT NULL,

    PRIMARY KEY (interaction_id),
    UNIQUE KEY uq_pet_relationship_event (event_id),
    KEY ix_pet_relationship_recent (pet_id, occurred_at, event_id),
    CONSTRAINT fk_pet_relationship_source_event FOREIGN KEY (event_id, pet_id)
        REFERENCES pet_events (event_id, pet_id) ON DELETE RESTRICT,
    CONSTRAINT chk_pet_relationship_strength CHECK (strength BETWEEN 1 AND 2),
    CONSTRAINT chk_pet_relationship_interaction_uuid CHECK (
        interaction_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
