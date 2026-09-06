package com.silver.aipets.service.sleep;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** MariaDB sleep-state adapter using row locks for every state transition. */
public final class JdbcPetSleepStateStore implements PetSleepStateStore {
    private static final String COLUMNS = """
            s.pet_id, s.sleeping, s.sleep_started_at, s.sleep_ends_at,
            s.last_sleep_completed_at, s.forced_sleep_due_at, s.owner_network_online,
            s.owner_last_logout_at, s.owner_absence_session_id, s.absence_sleep_triggered,
            s.last_presence_update_at, s.updated_at
            """;
    private static final String SELECT_BY_PET = "SELECT " + COLUMNS + """
             FROM pet_sleep_state s WHERE s.pet_id = ?
            """;
    private static final String SELECT_BY_PET_FOR_UPDATE = SELECT_BY_PET + " FOR UPDATE";
    private static final String SELECT_BY_OWNER_FOR_UPDATE = "SELECT " + COLUMNS + """
             FROM pet_sleep_state s
             JOIN pets p ON p.pet_id = s.pet_id
             WHERE p.owner_uuid = ? FOR UPDATE
            """;
    private static final String SELECT_DUE = """
            SELECT pet_id FROM pet_sleep_state
            WHERE (sleeping = TRUE AND sleep_ends_at <= ?)
               OR (sleeping = FALSE AND forced_sleep_due_at <= ?
                   AND NOT (owner_network_online = FALSE AND absence_sleep_triggered = TRUE))
               OR (sleeping = FALSE AND owner_network_online = FALSE
                   AND absence_sleep_triggered = FALSE AND owner_last_logout_at <= ?)
            ORDER BY updated_at, pet_id
            LIMIT ?
            """;
    private static final String UPDATE = """
            UPDATE pet_sleep_state SET
                sleeping = ?, sleep_started_at = ?, sleep_ends_at = ?,
                last_sleep_completed_at = ?, forced_sleep_due_at = ?,
                owner_network_online = ?, owner_last_logout_at = ?,
                owner_absence_session_id = ?, absence_sleep_triggered = ?,
                last_presence_update_at = ?, updated_at = ?
            WHERE pet_id = ?
            """;

    private final DataSource dataSource;

    public JdbcPetSleepStateStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<PetSleepState> find(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_PET)) {
            statement.setString(1, petId.toString());
            return readUnique(statement);
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read pet sleep state", failure);
        }
    }

    @Override
    public Optional<PetSleepTransition> updateForOwner(
            UUID ownerUuid, Function<PetSleepState, PetSleepTransition> transition) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(transition, "transition");
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_OWNER_FOR_UPDATE)) {
                statement.setString(1, ownerUuid.toString());
                Optional<PetSleepState> current = readUnique(statement);
                if (current.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(apply(connection, current.orElseThrow(), transition));
            }
        });
    }

    @Override
    public PetSleepTransition update(
            UUID petId, Function<PetSleepState, PetSleepTransition> transition) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(transition, "transition");
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_PET_FOR_UPDATE)) {
                statement.setString(1, petId.toString());
                PetSleepState current = readUnique(statement)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown pet sleep state"));
                return apply(connection, current, transition);
            }
        });
    }

    @Override
    public List<UUID> findDuePetIds(Instant now, Instant logoutDueAtOrBefore, int limit) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(logoutDueAtOrBefore, "logoutDueAtOrBefore");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_DUE)) {
            statement.setObject(1, utc(now));
            statement.setObject(2, utc(now));
            statement.setObject(3, utc(logoutDueAtOrBefore));
            statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rows.next()) {
                    ids.add(UUID.fromString(rows.getString(1)));
                }
                return List.copyOf(ids);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not find due sleep states", failure);
        }
    }

    private static PetSleepTransition apply(
            Connection connection,
            PetSleepState current,
            Function<PetSleepState, PetSleepTransition> transition) throws SQLException {
        PetSleepTransition result = Objects.requireNonNull(transition.apply(current), "result");
        if (!result.state().petId().equals(current.petId())) {
            throw new IllegalArgumentException("Sleep transition changed pet identity");
        }
        if (!result.changed()) {
            return result;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            PetSleepState next = result.state();
            statement.setBoolean(1, next.sleeping());
            setInstant(statement, 2, next.sleepStartedAt());
            setInstant(statement, 3, next.sleepEndsAt());
            setInstant(statement, 4, next.lastSleepCompletedAt());
            statement.setObject(5, utc(next.forcedSleepDueAt()));
            statement.setBoolean(6, next.ownerNetworkOnline());
            setInstant(statement, 7, next.ownerLastLogoutAt());
            if (next.ownerAbsenceSessionId().isPresent()) {
                statement.setString(8, next.ownerAbsenceSessionId().orElseThrow().toString());
            } else {
                statement.setNull(8, Types.CHAR);
            }
            statement.setBoolean(9, next.absenceSleepTriggered());
            statement.setObject(10, utc(next.lastPresenceUpdateAt()));
            statement.setObject(11, utc(next.updatedAt()));
            statement.setString(12, next.petId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Sleep-state update did not affect exactly one row");
            }
        }
        return result;
    }

    private static Optional<PetSleepState> readUnique(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                return Optional.empty();
            }
            PetSleepState state = map(rows);
            if (rows.next()) {
                throw new SQLException("Sleep-state uniqueness invariant is violated");
            }
            return Optional.of(state);
        }
    }

    private static PetSleepState map(ResultSet rows) throws SQLException {
        return new PetSleepState(
                UUID.fromString(rows.getString("pet_id")),
                rows.getBoolean("sleeping"),
                nullableInstant(rows, "sleep_started_at"),
                nullableInstant(rows, "sleep_ends_at"),
                nullableInstant(rows, "last_sleep_completed_at"),
                requiredInstant(rows, "forced_sleep_due_at"),
                rows.getBoolean("owner_network_online"),
                nullableInstant(rows, "owner_last_logout_at"),
                Optional.ofNullable(rows.getString("owner_absence_session_id")).map(UUID::fromString),
                rows.getBoolean("absence_sleep_triggered"),
                requiredInstant(rows, "last_presence_update_at"),
                requiredInstant(rows, "updated_at"));
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not update pet sleep state", failure);
        }
    }

    private static Optional<Instant> nullableInstant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant(ZoneOffset.UTC));
    }

    private static Instant requiredInstant(ResultSet rows, String column) throws SQLException {
        return nullableInstant(rows, column)
                .orElseThrow(() -> new SQLException(column + " cannot be null"));
    }

    private static void setInstant(
            PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) {
            statement.setObject(index, utc(value.orElseThrow()));
        } else {
            statement.setNull(index, Types.TIMESTAMP);
        }
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
