package com.silver.aipets.service.retention;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** MariaDB V001 bounded raw-redaction/prompt-expiry job adapter. */
public final class JdbcRetentionCleanupRepository implements RetentionCleanupRepository {
    private static final String TYPE = "EVENT_RETENTION_CLEANUP";
    private static final Set<String> PAYLOAD = Set.of("scheduledAt", "rawCutoff");
    private static final String COLUMNS = """
            job_id,idempotency_key,payload_json,status,attempt_count,not_before,
            locked_by,locked_until,last_error_sanitized
            """;
    private static final String INSERT = """
            INSERT INTO jobs (job_id,job_type,pet_id,idempotency_key,payload_json,status,
                attempt_count,not_before,created_at,updated_at)
            VALUES (?,?,NULL,?,?,'PENDING',0,?,?,?)
            """;
    private static final String FIND = "SELECT " + COLUMNS + """
             FROM jobs WHERE job_type=? AND idempotency_key=?
            """;
    private static final String DUE = "SELECT " + COLUMNS + """
             FROM jobs WHERE job_type=? AND ((status IN ('PENDING','RETRY') AND not_before<=?)
                OR (status='RUNNING' AND locked_until<=?))
             ORDER BY not_before,job_id LIMIT ? FOR UPDATE
            """;
    private static final String CLAIM = """
            UPDATE jobs SET status='RUNNING',attempt_count=attempt_count+1,locked_by=?,
                locked_until=?,updated_at=?,completed_at=NULL WHERE job_id=?
            """;
    private static final String LOCK = """
            SELECT status,locked_by,attempt_count FROM jobs
            WHERE job_id=? AND job_type=? FOR UPDATE
            """;
    private static final String EXPIRE = """
            UPDATE pet_events SET prompt_eligible=FALSE
            WHERE prompt_eligible=TRUE AND short_term_expires_at IS NOT NULL
                AND short_term_expires_at<=? ORDER BY short_term_expires_at,event_id LIMIT ?
            """;
    private static final String REDACT = """
            UPDATE pet_events SET raw_player_text=NULL,raw_pet_reply=NULL
            WHERE occurred_at<=? AND (raw_player_text IS NOT NULL OR raw_pet_reply IS NOT NULL)
            ORDER BY occurred_at,event_id LIMIT ?
            """;
    private static final String SUCCESS = """
            UPDATE jobs SET status='SUCCEEDED',locked_by=NULL,locked_until=NULL,
                last_error_sanitized=NULL,updated_at=?,completed_at=?
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String RETRY = """
            UPDATE jobs SET status='RETRY',not_before=?,locked_by=NULL,locked_until=NULL,
                last_error_sanitized=?,updated_at=?,completed_at=NULL
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String FAILED = """
            UPDATE jobs SET status='FAILED',locked_by=NULL,locked_until=NULL,
                last_error_sanitized=?,updated_at=?,completed_at=?
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;

    private final DataSource dataSource;

    public JdbcRetentionCleanupRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override public EnqueueResult enqueue(RetentionCleanupJob proposed) {
        Objects.requireNonNull(proposed, "proposed");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, proposed.jobId().toString()); statement.setString(2, TYPE);
            statement.setString(3, proposed.idempotencyKey()); statement.setString(4, payload(proposed));
            statement.setObject(5, utc(proposed.notBefore())); statement.setObject(6, utc(proposed.scheduledAt()));
            statement.setObject(7, utc(proposed.scheduledAt())); requireOne(statement.executeUpdate(), "retention enqueue");
            return new EnqueueResult(proposed, true);
        } catch (SQLException failure) {
            if (!constraint(failure)) throw persistence("Could not enqueue retention cleanup", failure);
            RetentionCleanupJob existing = findByKey(proposed.idempotencyKey()).orElseThrow(() ->
                    persistence("Retention key collision without row", failure));
            if (!existing.rawCutoff().equals(proposed.rawCutoff())) {
                // Same UTC day may be observed at different instants; first cutoff remains authoritative.
                return new EnqueueResult(existing, false);
            }
            return new EnqueueResult(existing, false);
        }
    }

    @Override public List<RetentionCleanupJob> claimDue(
            String workerId, Instant now, Duration lease, int limit) {
        Objects.requireNonNull(workerId, "workerId"); Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (workerId.isBlank() || workerId.length() > 191 || lease.isZero()
                || lease.isNegative() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid retention claim bounds");
        }
        return transaction("claim retention cleanup", connection -> {
            List<RetentionCleanupJob> due = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(DUE)) {
                statement.setString(1, TYPE); statement.setObject(2, utc(now));
                statement.setObject(3, utc(now)); statement.setInt(4, limit);
                try (ResultSet rows = statement.executeQuery()) { while (rows.next()) due.add(map(rows)); }
            }
            List<RetentionCleanupJob> result = new ArrayList<>();
            for (RetentionCleanupJob job : due) {
                Instant until = now.plus(lease);
                try (PreparedStatement statement = connection.prepareStatement(CLAIM)) {
                    statement.setString(1, workerId); statement.setObject(2, utc(until));
                    statement.setObject(3, utc(now)); statement.setString(4, job.jobId().toString());
                    requireOne(statement.executeUpdate(), "retention claim");
                }
                result.add(copy(job, RetentionCleanupJob.Status.RUNNING, job.attemptCount() + 1,
                        job.notBefore(), Optional.of(workerId), Optional.of(until), job.lastErrorCategory()));
            }
            return List.copyOf(result);
        });
    }

    @Override public RetentionCleanupResult cleanup(
            RetentionCleanupJob job, String workerId, Instant now, int maximumRows) {
        return transaction("apply retention cleanup", connection -> {
            requireLock(connection, job, workerId);
            int expired;
            try (PreparedStatement statement = connection.prepareStatement(EXPIRE)) {
                statement.setObject(1, utc(now)); statement.setInt(2, maximumRows);
                expired = statement.executeUpdate();
            }
            int redacted;
            try (PreparedStatement statement = connection.prepareStatement(REDACT)) {
                statement.setObject(1, utc(job.rawCutoff())); statement.setInt(2, maximumRows);
                redacted = statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(SUCCESS)) {
                statement.setObject(1, utc(now)); statement.setObject(2, utc(now));
                statement.setString(3, job.jobId().toString()); statement.setString(4, workerId);
                requireOne(statement.executeUpdate(), "retention success");
            }
            return new RetentionCleanupResult(expired, redacted);
        });
    }

    @Override public void retry(
            RetentionCleanupJob job, String workerId, Instant now, Instant notBefore, String category) {
        execute(RETRY, statement -> {
            statement.setObject(1, utc(notBefore)); statement.setString(2, bounded(category));
            statement.setObject(3, utc(now)); statement.setString(4, job.jobId().toString());
            statement.setString(5, workerId);
        }, "retry retention cleanup");
    }

    @Override public void failed(
            RetentionCleanupJob job, String workerId, Instant now, String category) {
        execute(FAILED, statement -> {
            statement.setString(1, bounded(category)); statement.setObject(2, utc(now));
            statement.setObject(3, utc(now)); statement.setString(4, job.jobId().toString());
            statement.setString(5, workerId);
        }, "fail retention cleanup");
    }

    @Override public Optional<RetentionCleanupJob> findByKey(String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, TYPE); statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                RetentionCleanupJob result = map(rows);
                if (rows.next()) throw new SQLException("Retention key is not unique");
                return Optional.of(result);
            }
        } catch (SQLException failure) { throw persistence("Could not find retention cleanup", failure); }
    }

    private static void requireLock(
            Connection connection, RetentionCleanupJob job, String workerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK)) {
            statement.setString(1, job.jobId().toString()); statement.setString(2, TYPE);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !"RUNNING".equals(rows.getString("status"))
                        || !workerId.equals(rows.getString("locked_by"))
                        || rows.getInt("attempt_count") != job.attemptCount()) {
                    throw new SQLException("Retention lease not owned");
                }
            }
        }
    }

    private void execute(String sql, Binder binder, String operation) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement); requireOne(statement.executeUpdate(), operation);
        } catch (SQLException failure) { throw persistence("Could not " + operation, failure); }
    }

    private <T> T transaction(String operation, Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean auto = connection.getAutoCommit(); connection.setAutoCommit(false);
            try { T result = work.apply(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException failure) { connection.rollback(); throw failure; }
            finally { connection.setAutoCommit(auto); }
        } catch (SQLException failure) { throw persistence("Could not " + operation, failure); }
    }

    private static RetentionCleanupJob map(ResultSet rows) throws SQLException {
        Times times = parse(rows.getString("payload_json"));
        return new RetentionCleanupJob(
                UUID.fromString(rows.getString("job_id")), rows.getString("idempotency_key"),
                RetentionCleanupJob.Status.valueOf(rows.getString("status")), rows.getInt("attempt_count"),
                times.scheduledAt(), times.rawCutoff(), instant(rows, "not_before"),
                Optional.ofNullable(rows.getString("locked_by")), nullableInstant(rows, "locked_until"),
                Optional.ofNullable(rows.getString("last_error_sanitized")));
    }

    private static String payload(RetentionCleanupJob job) {
        JsonObject root = new JsonObject(); root.addProperty("scheduledAt", job.scheduledAt().toString());
        root.addProperty("rawCutoff", job.rawCutoff().toString()); return root.toString();
    }
    private static Times parse(String json) throws SQLException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(PAYLOAD)) {
                throw new IllegalArgumentException("unexpected fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            return new Times(Instant.parse(root.get("scheduledAt").getAsString()),
                    Instant.parse(root.get("rawCutoff").getAsString()));
        } catch (RuntimeException failure) { throw new SQLException("Invalid retention payload", failure); }
    }
    private static RetentionCleanupJob copy(
            RetentionCleanupJob job, RetentionCleanupJob.Status status, int attempts, Instant notBefore,
            Optional<String> by, Optional<Instant> until, Optional<String> error) {
        return new RetentionCleanupJob(job.jobId(), job.idempotencyKey(), status, attempts,
                job.scheduledAt(), job.rawCutoff(), notBefore, by, until, error);
    }
    private static Optional<Instant> nullableInstant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant(ZoneOffset.UTC));
    }
    private static Instant instant(ResultSet rows, String column) throws SQLException {
        return nullableInstant(rows, column).orElseThrow(() -> new SQLException(column + " is null"));
    }
    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static String bounded(String value) {
        String safe = value == null || value.isBlank() ? "RuntimeException" : value;
        return safe.substring(0, Math.min(safe.length(), 128));
    }
    private static boolean constraint(SQLException failure) {
        return failure.getSQLState() != null && failure.getSQLState().startsWith("23");
    }
    private static void requireOne(int rows, String operation) throws SQLException {
        if (rows != 1) throw new SQLException(operation + " did not affect one row");
    }
    private static PetPersistenceException persistence(String message, Throwable failure) {
        return new PetPersistenceException(message, failure);
    }
    private record Times(Instant scheduledAt, Instant rawCutoff) { }
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface Work<T> { T apply(Connection connection) throws SQLException; }
}
