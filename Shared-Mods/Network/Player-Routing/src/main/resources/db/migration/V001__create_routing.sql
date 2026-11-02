CREATE TABLE IF NOT EXISTS routing_player_state (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    preferred_server VARCHAR(64) NULL,
    last_successful_server VARCHAR(64) NULL,
    last_listed_server VARCHAR(64) NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB
;
CREATE TABLE IF NOT EXISTS routing_player_visits (
    player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    server_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_visited_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_visited_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (player_uuid, server_name),
    CONSTRAINT fk_routing_visits_player FOREIGN KEY (player_uuid)
        REFERENCES routing_player_state(player_uuid) ON DELETE CASCADE
) ENGINE=InnoDB
;
