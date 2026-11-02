-- Persist the cited memory behind a sparse intention and reject older asynchronous plans.
-- Physical targets stay with the loaded Fabric entity and are never authority placement data.
ALTER TABLE pet_decisions
    ADD COLUMN motivation_memory_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN entity_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN decision_version BIGINT UNSIGNED NOT NULL DEFAULT 1;

CREATE INDEX ix_decisions_motivation ON pet_decisions (motivation_memory_id);
