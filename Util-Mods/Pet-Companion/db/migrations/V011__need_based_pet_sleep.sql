-- SQL remains the clock and authority for fatigue and sleep, including held/unloaded pets.
ALTER TABLE pet_sleep_state
    ADD COLUMN fatigue DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN fatigue_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN awake_since DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE pet_sleep_state s JOIN pets p ON p.pet_id = s.pet_id
SET s.awake_since = COALESCE(s.last_sleep_completed_at, p.created_at),
    s.fatigue_updated_at = CURRENT_TIMESTAMP(6),
    s.forced_sleep_due_at = LEAST(s.forced_sleep_due_at,
        DATE_ADD(COALESCE(s.last_sleep_completed_at, p.created_at), INTERVAL 16 HOUR));
