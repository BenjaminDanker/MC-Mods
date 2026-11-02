CREATE TABLE IF NOT EXISTS chronicle_excluded_players (
    player_uuid BINARY(16) NOT NULL PRIMARY KEY,
    username_hint VARCHAR(64) NULL,
    excluded_at_ms BIGINT NOT NULL,
    actor_uuid BINARY(16) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NULL
)
;

CREATE TABLE IF NOT EXISTS chronicle_admin_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_uuid BINARY(16) NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_name VARCHAR(48) NOT NULL,
    target_uuid BINARY(16) NULL,
    target_hint VARCHAR(64) NULL,
    event_id VARCHAR(80) NULL,
    reason VARCHAR(255) NULL,
    removed_completions INT NOT NULL DEFAULT 0,
    removed_firsts INT NOT NULL DEFAULT 0,
    created_at_ms BIGINT NOT NULL,
    INDEX chronicle_admin_audit_target_idx (target_uuid, created_at_ms),
    INDEX chronicle_admin_audit_actor_idx (actor_uuid, created_at_ms)
)
;
