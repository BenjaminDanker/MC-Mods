UPDATE pet_mood
SET affect_alertness = GREATEST(0, LEAST(100, affect_alertness))
WHERE affect_alertness < 0 OR affect_alertness > 100;

ALTER TABLE pet_mood
    DROP CONSTRAINT chk_mood_affect_alertness,
    ADD COLUMN affect_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT chk_mood_affect_alertness CHECK (affect_alertness BETWEEN 0 AND 100);

ALTER TABLE pet_open_threads
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE pet_open_threads
SET updated_at = COALESCE(last_referenced_at, created_at)
WHERE updated_at IS NULL;

ALTER TABLE pet_open_threads
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD KEY ix_pet_threads_updated (pet_id, updated_at);
