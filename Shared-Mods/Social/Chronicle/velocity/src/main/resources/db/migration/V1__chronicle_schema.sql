CREATE TABLE IF NOT EXISTS chronicle_events (
    event_id VARCHAR(80) NOT NULL PRIMARY KEY,
    display_text VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
)
;

CREATE TABLE IF NOT EXISTS chronicle_player_settings (
    player_uuid BINARY(16) NOT NULL PRIMARY KEY,
    default_mode VARCHAR(16) NULL,
    prompt_deadline_ms BIGINT NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
)
;

CREATE TABLE IF NOT EXISTS chronicle_firsts (
    event_id VARCHAR(80) NOT NULL PRIMARY KEY,
    player_uuid BINARY(16) NOT NULL,
    username_snapshot VARCHAR(64) NOT NULL,
    completed_at_ms BIGINT NOT NULL,
    privacy_mode VARCHAR(16) NULL,
    conceal_until_ms BIGINT NOT NULL,
    prompt_deadline_ms BIGINT NULL,
    announcement_state VARCHAR(24) NOT NULL,
    announced_at_ms BIGINT NULL,
    INDEX chronicle_first_player_idx (player_uuid, completed_at_ms),
    CONSTRAINT chronicle_first_event_fk FOREIGN KEY (event_id) REFERENCES chronicle_events(event_id)
)
;

CREATE TABLE IF NOT EXISTS chronicle_completions (
    event_id VARCHAR(80) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    username_snapshot VARCHAR(64) NOT NULL,
    completed_at_ms BIGINT NOT NULL,
    PRIMARY KEY (event_id, player_uuid),
    INDEX chronicle_completion_player_idx (player_uuid, completed_at_ms),
    CONSTRAINT chronicle_completion_event_fk FOREIGN KEY (event_id) REFERENCES chronicle_events(event_id)
)
;
