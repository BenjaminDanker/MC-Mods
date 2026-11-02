-- SQL remains authoritative; the source-event link table stores exact provenance IDs.
ALTER TABLE long_term_memories
    ADD COLUMN provenance ENUM('VERIFIED_OBSERVATION', 'OWNER_STATEMENT', 'INFERENCE')
        NOT NULL DEFAULT 'INFERENCE',
    ADD COLUMN fact_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD KEY ix_memories_current_fact (pet_id, fact_key, active);

-- Indexed recent observations for the bounded dialogue context query.
CREATE INDEX ix_pet_events_recent_context
    ON pet_events (pet_id, prompt_eligible, occurred_at);
CREATE INDEX ix_pet_mood_decay_due
    ON pet_mood (last_decay_at, pet_id);
