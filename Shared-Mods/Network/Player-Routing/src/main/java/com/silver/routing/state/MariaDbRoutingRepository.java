package com.silver.routing.state;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** Transactional MariaDB persistence for routing profiles state (portal transfers moved to the Portals plugin). */
public final class MariaDbRoutingRepository implements AutoCloseable {
    private final HikariDataSource dataSource;

    public MariaDbRoutingRepository(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        // Shaded Velocity plugins do not reliably preserve JDBC's service-loader
        // metadata, so identify the bundled driver explicitly.
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setPoolName("WakeUpLobbyRouting");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setAutoCommit(true);
        this.dataSource = new HikariDataSource(config);
    }

    public void migrate() throws SQLException, IOException {
        String routingSql = readMigration("/db/migration/V001__create_wakeup_routing.sql");
        String authorizationSql = readMigration("/db/migration/V002__create_authorization.sql");
        String authorizationSeedSql = readMigration("/db/migration/V003__seed_initial_authorization.sql");
        String defaultPlayerRolesSql = readMigration("/db/migration/V004__data_driven_default_player_roles.sql");
        String firstPartyPolicySql = readMigration("/db/migration/V005__seed_first_party_authorization_policy.sql");
        String commandPolicySql = readMigration("/db/migration/V006__seed_central_command_policy.sql");
        String playerSyncSql = readMigration("/db/migration/V007__rename_player_sync_permissions.sql");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureMigrationTable(connection);
                applyMigration(connection, 1, "create WakeUpLobby routing state", routingSql);
                applyMigration(connection, 2, "create centralized authorization schema", authorizationSql);
                applyMigration(connection, 3, "seed initial centralized authorization", authorizationSeedSql);
                applyMigration(connection, 4, "configure data-driven default player roles", defaultPlayerRolesSql);
                applyMigration(connection, 5, "seed first-party centralized authorization policy", firstPartyPolicySql);
                applyMigration(connection, 6, "seed centralized command administration policy", commandPolicySql);
                applyMigration(connection, 7, "rename the old player-data permissions to Player Sync", playerSyncSql);
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Shared pool for plugin-owned repositories; its lifecycle remains owned here. */
    public DataSource dataSource() {
        return dataSource;
    }

    private static String readMigration(String resource) throws IOException {
        try (InputStream input = MariaDbRoutingRepository.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing migration resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void applyMigration(Connection connection, int version, String description, String sql)
            throws SQLException {
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT 1 FROM wakeup_schema_migrations WHERE version=?")) {
            lookup.setInt(1, version);
            try (ResultSet rows = lookup.executeQuery()) {
                if (rows.next()) return;
            }
        }
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                try (Statement query = connection.createStatement()) {
                    query.execute(statement);
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT IGNORE INTO wakeup_schema_migrations(version, description) VALUES (?, ?)")) {
            insert.setInt(1, version);
            insert.setString(2, description);
            insert.executeUpdate();
        }
    }

    private static void ensureMigrationTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS wakeup_schema_migrations ("
                    + "version INT NOT NULL PRIMARY KEY, "
                    + "description VARCHAR(255) NOT NULL, "
                    + "applied_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)"
                    + ") ENGINE=InnoDB");
        }
    }

    public RoutingSnapshot loadSnapshot(Instant now) throws SQLException {
        Map<UUID, String> preferred = new HashMap<>();
        Map<UUID, String> listed = new HashMap<>();
        Map<UUID, Set<String>> visited = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT player_uuid, preferred_server, last_listed_server FROM wakeup_player_state")) {
                while (rows.next()) {
                    UUID playerId = UUID.fromString(rows.getString(1));
                    String preferredServer = rows.getString(2);
                    String listedServer = rows.getString(3);
                    if (preferredServer != null && !preferredServer.isBlank()) preferred.put(playerId, preferredServer);
                    if (listedServer != null && !listedServer.isBlank()) listed.put(playerId, listedServer);
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT player_uuid, server_name FROM wakeup_player_visits")) {
                while (rows.next()) {
                    UUID playerId = UUID.fromString(rows.getString(1));
                    visited.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(rows.getString(2));
                }
            }
        }
        return new RoutingSnapshot(Map.copyOf(preferred), Map.copyOf(listed), deepCopy(visited));
    }

    public void importLegacy(Map<UUID, String> preferred,
                             Map<UUID, String> listed,
                             Map<UUID, Set<String>> visited) throws SQLException {
        Set<UUID> players = new HashSet<>();
        players.addAll(preferred.keySet());
        players.addAll(listed.keySet());
        players.addAll(visited.keySet());
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement player = connection.prepareStatement(
                        "INSERT INTO wakeup_player_state(player_uuid, preferred_server, last_successful_server, last_listed_server) "
                                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                                + "preferred_server=COALESCE(preferred_server, VALUES(preferred_server)), "
                                + "last_successful_server=COALESCE(last_successful_server, VALUES(last_successful_server)), "
                                + "last_listed_server=COALESCE(last_listed_server, VALUES(last_listed_server))")) {
                    for (UUID playerId : players) {
                        String last = preferred.get(playerId);
                        player.setString(1, playerId.toString());
                        player.setString(2, last);
                        player.setString(3, last);
                        player.setString(4, listed.get(playerId));
                        player.addBatch();
                    }
                    player.executeBatch();
                }
                try (PreparedStatement visit = connection.prepareStatement(
                        "INSERT INTO wakeup_player_visits(player_uuid, server_name) VALUES (?, ?) "
                                + "ON DUPLICATE KEY UPDATE last_visited_at=last_visited_at")) {
                    for (var entry : visited.entrySet()) {
                        for (String server : entry.getValue()) {
                            if (server == null || server.isBlank()) continue;
                            visit.setString(1, entry.getKey().toString());
                            visit.setString(2, server);
                            visit.addBatch();
                        }
                    }
                    visit.executeBatch();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void recordSuccessfulConnection(UUID playerId, String server, String listedServer) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement player = connection.prepareStatement(
                        "INSERT INTO wakeup_player_state(player_uuid, preferred_server, last_successful_server, last_listed_server) "
                                + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE preferred_server=VALUES(preferred_server), "
                                + "last_successful_server=VALUES(last_successful_server), "
                                + "last_listed_server=COALESCE(VALUES(last_listed_server), last_listed_server)")) {
                    player.setString(1, playerId.toString());
                    player.setString(2, server);
                    player.setString(3, server);
                    player.setString(4, listedServer);
                    player.executeUpdate();
                }
                try (PreparedStatement visit = connection.prepareStatement(
                        "INSERT INTO wakeup_player_visits(player_uuid, server_name) VALUES (?, ?) "
                                + "ON DUPLICATE KEY UPDATE last_visited_at=CURRENT_TIMESTAMP(3)")) {
                    visit.setString(1, playerId.toString());
                    visit.setString(2, server);
                    visit.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** The servers this player is recorded as having visited, read straight from the database. */
    public Set<String> loadVisits(UUID playerId) throws SQLException {
        Set<String> servers = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT server_name FROM wakeup_player_visits WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) servers.add(rows.getString(1));
            }
        }
        return servers;
    }

    /**
     * Which story branches the player has finished, from Player Sync's player table ("magic", "tech"). A missing table or
     * columns (a database Player Sync has not migrated yet) reads as nothing finished.
     */
    public Set<String> loadCompletedBranches(UUID playerId) throws SQLException {
        Set<String> done = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT MagicCompleted, TechCompleted FROM player WHERE uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    if (rows.getBoolean(1)) done.add("magic");
                    if (rows.getBoolean(2)) done.add("tech");
                }
            }
        }
        return done;
    }

    public void setPreferredServer(UUID playerId, String server) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO wakeup_player_state(player_uuid, preferred_server) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE preferred_server=VALUES(preferred_server)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, server);
            statement.executeUpdate();
        }
    }

    private static Map<UUID, Set<String>> deepCopy(Map<UUID, Set<String>> input) {
        Map<UUID, Set<String>> result = new HashMap<>();
        input.forEach((key, value) -> result.put(key, Set.copyOf(value)));
        return Map.copyOf(result);
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
