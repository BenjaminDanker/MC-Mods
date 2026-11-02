-- Exact replay metadata for the two-step monthly recall workflow.
ALTER TABLE pet_recall_usage
    ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER operation_id,
    ADD COLUMN compensation_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER request_fingerprint,
    ADD COLUMN response_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
        AFTER status;

-- V001 may have been staged before recall execution was enabled. Give any legacy audit rows a
-- deterministic non-matching fingerprint; operators can inspect/recover them explicitly.
UPDATE pet_recall_usage
SET request_fingerprint = SHA2(CONCAT('legacy:', operation_id), 256)
WHERE request_fingerprint IS NULL;

ALTER TABLE pet_recall_usage
    MODIFY COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT chk_pet_recall_fingerprint CHECK (
        request_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT chk_pet_recall_compensation_fingerprint CHECK (
        compensation_fingerprint IS NULL
        OR compensation_fingerprint REGEXP '^[0-9a-f]{64}$'
    );
