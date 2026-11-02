package com.silver.chronicle.velocity;

import com.silver.chronicle.common.ChronicleEvent;
import com.silver.chronicle.common.PrivacyMode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ChronicleDatabase implements AutoCloseable {
    private final HikariDataSource dataSource;

    ChronicleDatabase(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.jdbcUrl());
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setPoolName("Chronicle-MariaDB");
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setInitializationFailTimeout(10_000);
        dataSource = new HikariDataSource(config);
    }

    void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS chronicle_schema_migrations (version INT NOT NULL PRIMARY KEY, applied_at_ms BIGINT NOT NULL)");
            applyMigration(connection, statement, 1, "/db/migration/V1__chronicle_schema.sql");
            applyMigration(connection, statement, 2, "/db/migration/V2__chronicle_admin.sql");
        }
        try (Connection connection = dataSource.getConnection(); PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chronicle_events(event_id, display_text, enabled) VALUES(?,?,TRUE) ON DUPLICATE KEY UPDATE display_text=VALUES(display_text), enabled=TRUE")) {
            for (ChronicleEvent event : ChronicleEvent.values()) {
                insert.setString(1, event.id());
                insert.setString(2, event.display());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void applyMigration(Connection connection, Statement statement, int version, String resource) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM chronicle_schema_migrations WHERE version=?")) {
            query.setInt(1, version);
            try (ResultSet rs = query.executeQuery()) { if (rs.next()) return; }
        }
        try (var input = ChronicleDatabase.class.getResourceAsStream(resource)) {
            if (input == null) throw new SQLException("Chronicle migration resource is missing: " + resource);
            String migration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String sql : migration.split(";")) if (!sql.isBlank()) statement.executeUpdate(sql.strip());
        } catch (java.io.IOException failure) {
            throw new SQLException("Could not read Chronicle migration " + resource, failure);
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chronicle_schema_migrations(version,applied_at_ms) VALUES(?,CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS UNSIGNED))")) {
            insert.setInt(1, version);
            insert.executeUpdate();
        }
    }

    CompletionResult complete(String eventId, UUID playerId, String username) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                if (isExcluded(connection, playerId)) {
                    connection.commit();
                    return new CompletionResult(false, null, false, true);
                }
                ensureSettings(connection, playerId, now);
                lockSettings(connection, playerId);
                long deadline = now + 60_000;
                int personalInserted;
                try (PreparedStatement completion = connection.prepareStatement(
                        "INSERT IGNORE INTO chronicle_completions(event_id,player_uuid,username_snapshot,completed_at_ms) VALUES(?,?,?,?)")) {
                    completion.setString(1, eventId);
                    completion.setBytes(2, uuidBytes(playerId));
                    completion.setString(3, username);
                    completion.setLong(4, now);
                    personalInserted = completion.executeUpdate();
                }
                int claimed;
                try (PreparedStatement first = connection.prepareStatement(
                        "INSERT IGNORE INTO chronicle_firsts(event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state) VALUES(?,?,?,?,?,?,?,?)")) {
                    first.setString(1, eventId);
                    first.setBytes(2, uuidBytes(playerId));
                    first.setString(3, username);
                    first.setLong(4, now);
                    first.setNull(5, java.sql.Types.VARCHAR);
                    first.setLong(6, now + 86_400_000L);
                    first.setLong(7, deadline);
                    first.setString(8, "AWAITING_PRIVACY");
                    claimed = first.executeUpdate();
                }
                if (claimed > 0) refreshPromptDeadline(connection, playerId, now);
                connection.commit();
                if (claimed == 0) return new CompletionResult(personalInserted > 0, null, false, false);
                return new CompletionResult(personalInserted > 0, deadline, true, false);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    AdminResult exclude(UUID targetId, String usernameHint, String reason, UUID actorId, String actorName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                boolean alreadyExcluded = isExcluded(connection, targetId);
                if (!alreadyExcluded) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO chronicle_excluded_players(player_uuid,username_hint,excluded_at_ms,actor_uuid,actor_name,reason) VALUES(?,?,?,?,?,?)")) {
                        insert.setBytes(1, uuidBytes(targetId));
                        setNullableString(insert, 2, usernameHint);
                        insert.setLong(3, now);
                        insert.setBytes(4, uuidBytes(actorId));
                        insert.setString(5, actorName);
                        setNullableString(insert, 6, reason);
                        insert.executeUpdate();
                    }
                }
                insertAudit(connection, actorId, actorName, "EXCLUDE", targetId, usernameHint, null, reason, 0, 0, now);
                connection.commit();
                String target = usernameHint == null || usernameHint.isBlank() ? targetId.toString() : usernameHint + " (" + targetId + ")";
                return new AdminResult(List.of(alreadyExcluded ? target + " was already excluded from Chronicle." : target + " is now excluded from Chronicle."), null);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    AdminResult include(UUID targetId, UUID actorId, String actorName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                int removed;
                String usernameHint = null;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT username_hint FROM chronicle_excluded_players WHERE player_uuid=? FOR UPDATE")) {
                    select.setBytes(1, uuidBytes(targetId));
                    try (ResultSet rs = select.executeQuery()) { if (rs.next()) usernameHint = rs.getString(1); }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chronicle_excluded_players WHERE player_uuid=?")) {
                    delete.setBytes(1, uuidBytes(targetId));
                    removed = delete.executeUpdate();
                }
                insertAudit(connection, actorId, actorName, "INCLUDE", targetId, usernameHint, null, null, 0, 0, now);
                connection.commit();
                String target = usernameHint == null || usernameHint.isBlank() ? targetId.toString() : usernameHint + " (" + targetId + ")";
                return new AdminResult(List.of(removed == 0 ? target + " was not excluded." : target + " is no longer excluded."), null);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    List<ExclusionRecord> exclusions() throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT player_uuid,username_hint,excluded_at_ms,actor_name,reason FROM chronicle_excluded_players ORDER BY excluded_at_ms,player_uuid");
             ResultSet rs = query.executeQuery()) {
            List<ExclusionRecord> records = new ArrayList<>();
            while (rs.next()) records.add(new ExclusionRecord(uuidFromBytes(rs.getBytes(1)), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5)));
            return records;
        }
    }

    AdminResult resetEvent(String eventId, UUID actorId, String actorName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                UUID owner = null;
                String username = null;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT player_uuid,username_snapshot FROM chronicle_firsts WHERE event_id=? FOR UPDATE")) {
                    select.setString(1, eventId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) { owner = uuidFromBytes(rs.getBytes(1)); username = rs.getString(2); }
                    }
                }
                int removed = 0;
                if (owner != null) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chronicle_firsts WHERE event_id=?")) {
                        delete.setString(1, eventId);
                        removed = delete.executeUpdate();
                    }
                    refreshPromptDeadline(connection, owner, now);
                }
                insertAudit(connection, actorId, actorName, "RESET_EVENT", owner, username, eventId, null, 0, removed, now);
                connection.commit();
                String message = removed == 0 ? "No historical first existed for " + eventId + "; nothing was removed."
                        : "Reset " + eventId + ": removed 1 historical first owned by " + username
                        + " and reopened the claim. Personal completions were left untouched.";
                return new AdminResult(List.of(message), owner);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    AdminResult removePlayer(UUID targetId, String eventId, UUID actorId, String actorName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                List<String> reopened = new ArrayList<>();
                if (eventId == null) {
                    try (PreparedStatement select = connection.prepareStatement("SELECT event_id FROM chronicle_firsts WHERE player_uuid=? FOR UPDATE")) {
                        select.setBytes(1, uuidBytes(targetId));
                        try (ResultSet rs = select.executeQuery()) { while (rs.next()) reopened.add(rs.getString(1)); }
                    }
                } else {
                    try (PreparedStatement select = connection.prepareStatement("SELECT event_id FROM chronicle_firsts WHERE player_uuid=? AND event_id=? FOR UPDATE")) {
                        select.setBytes(1, uuidBytes(targetId));
                        select.setString(2, eventId);
                        try (ResultSet rs = select.executeQuery()) { if (rs.next()) reopened.add(rs.getString(1)); }
                    }
                }
                String completionSql = eventId == null
                        ? "DELETE FROM chronicle_completions WHERE player_uuid=?"
                        : "DELETE FROM chronicle_completions WHERE player_uuid=? AND event_id=?";
                int completions;
                try (PreparedStatement delete = connection.prepareStatement(completionSql)) {
                    delete.setBytes(1, uuidBytes(targetId));
                    if (eventId != null) delete.setString(2, eventId);
                    completions = delete.executeUpdate();
                }
                String firstSql = eventId == null
                        ? "DELETE FROM chronicle_firsts WHERE player_uuid=?"
                        : "DELETE FROM chronicle_firsts WHERE player_uuid=? AND event_id=?";
                int firsts;
                try (PreparedStatement delete = connection.prepareStatement(firstSql)) {
                    delete.setBytes(1, uuidBytes(targetId));
                    if (eventId != null) delete.setString(2, eventId);
                    firsts = delete.executeUpdate();
                }
                if (eventId == null) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chronicle_player_settings WHERE player_uuid=?")) {
                        delete.setBytes(1, uuidBytes(targetId));
                        delete.executeUpdate();
                    }
                } else if (firsts > 0) {
                    refreshPromptDeadline(connection, targetId, now);
                }
                insertAudit(connection, actorId, actorName, eventId == null ? "REMOVE_PLAYER" : "REMOVE_PLAYER_EVENT",
                        targetId, null, eventId, null, completions, firsts, now);
                connection.commit();
                StringBuilder message = new StringBuilder("Removed ").append(completions).append(" personal completion(s) and ")
                        .append(firsts).append(" historical first(s) for ").append(targetId).append('.');
                if (!reopened.isEmpty()) message.append(" Reopened: ").append(String.join(", ", reopened)).append('.');
                if (eventId == null) message.append(" Player privacy and pending prompt state were cleared.");
                return new AdminResult(List.of(message.toString()), eventId == null || firsts > 0 ? targetId : null);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static boolean isExcluded(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM chronicle_excluded_players WHERE player_uuid=? FOR UPDATE")) {
            query.setBytes(1, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) { return rs.next(); }
        }
    }

    private static void refreshPromptDeadline(Connection connection, UUID playerId, long now) throws SQLException {
        Long deadline = null;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT MIN(prompt_deadline_ms) FROM chronicle_firsts WHERE player_uuid=? AND announcement_state='AWAITING_PRIVACY'")) {
            query.setBytes(1, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next()) { long value = rs.getLong(1); if (!rs.wasNull()) deadline = value; }
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE chronicle_player_settings SET prompt_deadline_ms=?,updated_at_ms=? WHERE player_uuid=?")) {
            if (deadline == null) update.setNull(1, java.sql.Types.BIGINT); else update.setLong(1, deadline);
            update.setLong(2, now);
            update.setBytes(3, uuidBytes(playerId));
            update.executeUpdate();
        }
    }

    private static void insertAudit(Connection connection, UUID actorId, String actorName, String action,
                                    UUID targetId, String targetHint, String eventId, String reason,
                                    int removedCompletions, int removedFirsts, long now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chronicle_admin_audit(actor_uuid,actor_name,action_name,target_uuid,target_hint,event_id,reason,removed_completions,removed_firsts,created_at_ms) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            insert.setBytes(1, uuidBytes(actorId));
            insert.setString(2, actorName);
            insert.setString(3, action);
            if (targetId == null) insert.setNull(4, java.sql.Types.BINARY); else insert.setBytes(4, uuidBytes(targetId));
            setNullableString(insert, 5, targetHint);
            setNullableString(insert, 6, eventId);
            setNullableString(insert, 7, reason);
            insert.setInt(8, removedCompletions);
            insert.setInt(9, removedFirsts);
            insert.setLong(10, now);
            insert.executeUpdate();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value.length() > 255 ? value.substring(0, 255) : value);
    }

    PreferenceResult setPreference(UUID playerId, PrivacyMode mode) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                ensureSettings(connection, playerId, now);
                lockSettings(connection, playerId);
                updateDefaultMode(connection, playerId, mode, now);
                connection.commit();
                return new PreferenceResult(mode, List.of());
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    PreferenceResult choosePrompt(UUID playerId, String eventId, PrivacyMode mode, long acceptedResponseAtMs) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement("UPDATE chronicle_firsts SET privacy_mode=?, announcement_state='READY', prompt_deadline_ms=NULL WHERE event_id=? AND player_uuid=? AND announcement_state='AWAITING_PRIVACY' AND prompt_deadline_ms>?")) {
                    update.setString(1, mode.id());
                    update.setString(2, eventId);
                    update.setBytes(3, uuidBytes(playerId));
                    update.setLong(4, acceptedResponseAtMs);
                    update.executeUpdate();
                }
                List<FirstRecord> ready = readyForEvent(connection, playerId, eventId);
                connection.commit();
                return new PreferenceResult(mode, ready);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    List<FirstRecord> expirePrompt(UUID playerId, String eventId, long expectedDeadline) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long now = dbNow(connection);
                ensureSettings(connection, playerId, now);
                SettingsRow settings = lockSettings(connection, playerId);
                FirstRecord due = null;
                try (PreparedStatement select = connection.prepareStatement("SELECT event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state FROM chronicle_firsts WHERE event_id=? AND player_uuid=? AND announcement_state='AWAITING_PRIVACY' AND prompt_deadline_ms=? AND prompt_deadline_ms<=? FOR UPDATE")) {
                    select.setString(1, eventId);
                    select.setBytes(2, uuidBytes(playerId));
                    select.setLong(3, expectedDeadline);
                    select.setLong(4, now);
                    try (ResultSet rs = select.executeQuery()) { if (rs.next()) due = readFirst(rs); }
                }
                if (due == null) { connection.commit(); return List.of(); }
                PrivacyMode fallback = settings.mode() == null ? PrivacyMode.PUBLIC : settings.mode();
                try (PreparedStatement update = connection.prepareStatement("UPDATE chronicle_firsts SET privacy_mode=?, announcement_state='READY', prompt_deadline_ms=NULL WHERE event_id=? AND player_uuid=? AND announcement_state='AWAITING_PRIVACY' AND prompt_deadline_ms=?")) {
                    update.setString(1, fallback.id());
                    update.setString(2, eventId);
                    update.setBytes(3, uuidBytes(playerId));
                    update.setLong(4, expectedDeadline);
                    update.executeUpdate();
                }
                refreshPromptDeadline(connection, playerId, now);
                List<FirstRecord> ready = readyForEvent(connection, playerId, eventId);
                connection.commit();
                return ready;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    List<PendingPrompt> pendingPrompts(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,prompt_deadline_ms FROM chronicle_firsts WHERE player_uuid=? AND announcement_state='AWAITING_PRIVACY' ORDER BY prompt_deadline_ms,event_id")) {
            query.setBytes(1, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) {
                List<PendingPrompt> result = new ArrayList<>();
                while (rs.next()) result.add(new PendingPrompt(playerId, rs.getString(1), rs.getLong(2)));
                return result;
            }
        }
    }

    List<PendingPrompt> allPendingPrompts() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT player_uuid,event_id,prompt_deadline_ms FROM chronicle_firsts WHERE announcement_state='AWAITING_PRIVACY' ORDER BY prompt_deadline_ms,event_id")) {
            List<PendingPrompt> result = new ArrayList<>();
            while (rs.next()) result.add(new PendingPrompt(uuidFromBytes(rs.getBytes(1)), rs.getString(2), rs.getLong(3)));
            return result;
        }
    }

    PendingPrompt pendingPrompt(UUID playerId, String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT prompt_deadline_ms FROM chronicle_firsts WHERE player_uuid=? AND event_id=? AND announcement_state='AWAITING_PRIVACY'")) {
            query.setBytes(1, uuidBytes(playerId));
            query.setString(2, eventId);
            try (ResultSet rs = query.executeQuery()) {
                return rs.next() ? new PendingPrompt(playerId, eventId, rs.getLong(1)) : null;
            }
        }
    }

    List<FirstRecord> readyAnnouncements() throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state FROM chronicle_firsts WHERE announcement_state='READY' ORDER BY completed_at_ms,event_id");
             ResultSet rs = query.executeQuery()) { return readFirsts(rs); }
    }

    void markAnnounced(String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE chronicle_firsts SET announcement_state='SENT', announced_at_ms=CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS UNSIGNED) WHERE event_id=? AND announcement_state='ANNOUNCING'")) {
            update.setString(1, eventId);
            update.executeUpdate();
        }
    }

    boolean claimAnnouncement(String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE chronicle_firsts SET announcement_state='ANNOUNCING' WHERE event_id=? AND announcement_state='READY'")) {
            update.setString(1, eventId);
            return update.executeUpdate() == 1;
        }
    }

    void recoverAnnouncementClaims() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE chronicle_firsts SET announcement_state='READY' WHERE announcement_state='ANNOUNCING'");
        }
    }

    List<HistoryRecord> history(long now) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms FROM chronicle_firsts ORDER BY completed_at_ms,event_id");
             ResultSet rs = query.executeQuery()) {
            List<HistoryRecord> result = new ArrayList<>();
            while (rs.next()) result.add(new HistoryRecord(rs.getString(1), rs.getString(2), rs.getLong(3),
                    parseMode(rs.getString(4)), rs.getLong(5)));
            return result;
        }
    }

    List<PersonalRecord> mine(UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,username_snapshot,completed_at_ms FROM chronicle_completions WHERE player_uuid=? ORDER BY completed_at_ms,event_id")) {
            query.setBytes(1, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) {
                List<PersonalRecord> result = new ArrayList<>();
                while (rs.next()) result.add(new PersonalRecord(rs.getString(1), rs.getString(2), rs.getLong(3)));
                return result;
            }
        }
    }

    List<FirstRecord> concealed() throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state FROM chronicle_firsts WHERE conceal_until_ms>CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS UNSIGNED) AND (privacy_mode IS NULL OR privacy_mode<>'public') ORDER BY completed_at_ms,event_id");
             ResultSet rs = query.executeQuery()) { return readFirsts(rs); }
    }

    FirstRecord ready(String eventId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state FROM chronicle_firsts WHERE event_id=?")) {
            query.setString(1, eventId);
            try (ResultSet rs = query.executeQuery()) { return rs.next() ? readFirst(rs) : null; }
        }
    }

    private static void ensureSettings(Connection connection, UUID playerId, long now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT IGNORE INTO chronicle_player_settings(player_uuid,default_mode,prompt_deadline_ms,created_at_ms,updated_at_ms) VALUES(?,NULL,NULL,?,?)")) {
            insert.setBytes(1, uuidBytes(playerId));
            insert.setLong(2, now);
            insert.setLong(3, now);
            insert.executeUpdate();
        }
    }

    private static SettingsRow lockSettings(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT default_mode,prompt_deadline_ms FROM chronicle_player_settings WHERE player_uuid=? FOR UPDATE")) {
            query.setBytes(1, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) {
                if (!rs.next()) throw new SQLException("Chronicle settings row disappeared");
                long deadline = rs.getLong(2);
                return new SettingsRow(parseMode(rs.getString(1)), rs.wasNull() ? null : deadline);
            }
        }
    }

    private static void updateDefaultMode(Connection connection, UUID playerId, PrivacyMode mode, long now) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE chronicle_player_settings SET default_mode=?, updated_at_ms=? WHERE player_uuid=?")) {
            update.setString(1, mode.id());
            update.setLong(2, now);
            update.setBytes(3, uuidBytes(playerId));
            update.executeUpdate();
        }
    }

    private List<FirstRecord> readyForEvent(Connection connection, UUID playerId, String eventId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT event_id,player_uuid,username_snapshot,completed_at_ms,privacy_mode,conceal_until_ms,prompt_deadline_ms,announcement_state FROM chronicle_firsts WHERE event_id=? AND player_uuid=? AND announcement_state='READY'")) {
            query.setString(1, eventId);
            query.setBytes(2, uuidBytes(playerId));
            try (ResultSet rs = query.executeQuery()) { return readFirsts(rs); }
        }
    }

    private static long dbNow(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT CAST(UNIX_TIMESTAMP(NOW(3))*1000 AS UNSIGNED)")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<FirstRecord> readFirsts(ResultSet rs) throws SQLException {
        List<FirstRecord> result = new ArrayList<>();
        while (rs.next()) result.add(readFirst(rs));
        return result;
    }

    private static FirstRecord readFirst(ResultSet rs) throws SQLException {
        long deadline = rs.getLong(7);
        Long deadlineValue = rs.wasNull() ? null : deadline;
        return new FirstRecord(rs.getString(1), uuidFromBytes(rs.getBytes(2)), rs.getString(3), rs.getLong(4),
                parseMode(rs.getString(5)), rs.getLong(6), deadlineValue, rs.getString(8));
    }

    private static PrivacyMode parseMode(String value) { return value == null ? null : PrivacyMode.parse(value); }
    private static byte[] uuidBytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static UUID uuidFromBytes(byte[] value) { ByteBuffer buffer = ByteBuffer.wrap(value); return new UUID(buffer.getLong(), buffer.getLong()); }

    @Override public void close() { dataSource.close(); }

    record CompletionResult(boolean personalInserted, Long deadlineMs, boolean firstClaimed, boolean excluded) { }
    record AdminResult(List<String> lines, UUID promptPlayerId) { AdminResult { lines = List.copyOf(lines); } }
    record ExclusionRecord(UUID playerId, String usernameHint, long excludedAtMs, String actorName, String reason) { }
    record PreferenceResult(PrivacyMode mode, List<FirstRecord> readyRecords) { }
    record PendingPrompt(UUID playerId, String eventId, long deadlineMs) { }
    record FirstRecord(String eventId, UUID playerId, String username, long completedAtMs, PrivacyMode privacyMode,
                       long concealUntilMs, Long promptDeadlineMs, String announcementState) { }
    record HistoryRecord(String eventId, String username, long completedAtMs, PrivacyMode privacyMode, long concealUntilMs) { }
    record PersonalRecord(String eventId, String username, long completedAtMs) { }
    private record SettingsRow(PrivacyMode mode, Long deadline) { }
}
