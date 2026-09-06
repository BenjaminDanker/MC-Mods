package com.silver.aipets.service.vector;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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

/** MariaDB durable embedding queue backed by the V001 generic jobs table. */
public final class JdbcEmbeddingJobStore implements EmbeddingJobStore {
    private static final String JOB_TYPE = "MEMORY_EMBEDDING";
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "memoryId", "memoryVersion", "embeddingModel");
    private static final String COLUMNS = """
            job_id, pet_id, idempotency_key, payload_json, status, attempt_count,
            not_before, locked_by, locked_until, last_error_sanitized
            """;
    private static final String INSERT = """
            INSERT INTO jobs (
                job_id, job_type, pet_id, idempotency_key, payload_json, status,
                attempt_count, not_before, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            """;
    private static final String FIND_KEY = "SELECT " + COLUMNS + """
             FROM jobs WHERE job_type = ? AND idempotency_key = ?
            """;
    private static final String CLAIM_DUE = "SELECT " + COLUMNS + """
             FROM jobs
             WHERE job_type = ? AND (
                 (status IN ('PENDING', 'RETRY') AND not_before <= ?)
                 OR (status = 'RUNNING' AND locked_until <= ?))
             ORDER BY not_before, job_id
             LIMIT ? FOR UPDATE
            """;
    private static final String CLAIM = """
            UPDATE jobs SET status = 'RUNNING', attempt_count = attempt_count + 1,
                locked_by = ?, locked_until = ?, updated_at = ?,
                completed_at = NULL
            WHERE job_id = ?
            """;
    private static final String SUCCEEDED = """
            UPDATE jobs SET status = 'SUCCEEDED', locked_by = NULL, locked_until = NULL,
                last_error_sanitized = NULL, updated_at = ?, completed_at = ?
            WHERE job_id = ? AND status = 'RUNNING' AND locked_by = ?
            """;
    private static final String RETRY = """
            UPDATE jobs SET status = 'RETRY', not_before = ?, locked_by = NULL,
                locked_until = NULL, last_error_sanitized = ?, updated_at = ?, completed_at = NULL
            WHERE job_id = ? AND status = 'RUNNING' AND locked_by = ?
            """;
    private static final String TERMINAL_ERROR = """
            UPDATE jobs SET status = ?, locked_by = NULL, locked_until = NULL,
                last_error_sanitized = ?, updated_at = ?, completed_at = ?
            WHERE job_id = ? AND status = 'RUNNING' AND locked_by = ?
            """;

    private final DataSource dataSource;

    public JdbcEmbeddingJobStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public EnqueueResult enqueue(EmbeddingJob proposed) {
        Objects.requireNonNull(proposed, "proposed");
        if (proposed.status() != EmbeddingJobStatus.PENDING || proposed.attemptCount() != 0) {
            throw new IllegalArgumentException("Only a fresh pending embedding job can be enqueued");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, proposed.jobId().toString());
            statement.setString(2, JOB_TYPE);
            statement.setString(3, proposed.petId().toString());
            statement.setString(4, proposed.idempotencyKey());
            statement.setString(5, payload(proposed));
            statement.setObject(6, utc(proposed.notBefore()));
            statement.setObject(7, utc(proposed.notBefore()));
            statement.setObject(8, utc(proposed.notBefore()));
            statement.executeUpdate();
            return new EnqueueResult(proposed, true);
        } catch (SQLException failure) {
            if (!isConstraintViolation(failure)) {
                throw new PetPersistenceException("Could not enqueue memory embedding", failure);
            }
            EmbeddingJob existing = findByIdempotencyKey(proposed.idempotencyKey())
                    .orElseThrow(() -> new PetPersistenceException(
                            "Embedding enqueue collided without an idempotency row", failure));
            validateSame(existing, proposed);
            return new EnqueueResult(existing, false);
        }
    }

    @Override
    public List<EmbeddingJob> claimDue(
            String workerId, Instant now, Duration leaseDuration, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (workerId.isBlank() || workerId.length() > 191 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid embedding claim bounds");
        }
        return transaction(connection -> {
            List<EmbeddingJob> due = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(CLAIM_DUE)) {
                statement.setString(1, JOB_TYPE);
                statement.setObject(2, utc(now));
                statement.setObject(3, utc(now));
                statement.setInt(4, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        due.add(map(rows));
                    }
                }
            }
            List<EmbeddingJob> claimed = new ArrayList<>(due.size());
            for (EmbeddingJob job : due) {
                Instant lockedUntil = now.plus(leaseDuration);
                try (PreparedStatement statement = connection.prepareStatement(CLAIM)) {
                    statement.setString(1, workerId);
                    statement.setObject(2, utc(lockedUntil));
                    statement.setObject(3, utc(now));
                    statement.setString(4, job.jobId().toString());
                    requireOne(statement.executeUpdate(), "claim embedding job");
                }
                claimed.add(new EmbeddingJob(
                        job.jobId(), job.petId(), job.memoryId(), job.memoryVersion(),
                        job.embeddingModel(), job.idempotencyKey(), EmbeddingJobStatus.RUNNING,
                        job.attemptCount() + 1, job.notBefore(), Optional.of(workerId),
                        Optional.of(lockedUntil), job.lastErrorSanitized()));
            }
            return List.copyOf(claimed);
        });
    }

    @Override
    public void succeeded(UUID jobId, String workerId, Instant completedAt) {
        executeLocked(SUCCEEDED, statement -> {
            statement.setObject(1, utc(completedAt));
            statement.setObject(2, utc(completedAt));
            statement.setString(3, jobId.toString());
            statement.setString(4, workerId);
        }, "complete embedding job");
    }

    @Override
    public void retry(
            UUID jobId, String workerId, Instant attemptedAt,
            Instant notBefore, String sanitizedError) {
        executeLocked(RETRY, statement -> {
            statement.setObject(1, utc(notBefore));
            statement.setString(2, boundedError(sanitizedError));
            statement.setObject(3, utc(attemptedAt));
            statement.setString(4, jobId.toString());
            statement.setString(5, workerId);
        }, "retry embedding job");
    }

    @Override
    public void failed(UUID jobId, String workerId, Instant completedAt, String sanitizedError) {
        terminalError(jobId, workerId, completedAt, sanitizedError, EmbeddingJobStatus.FAILED);
    }

    @Override
    public void canceled(UUID jobId, String workerId, Instant completedAt, String sanitizedReason) {
        terminalError(jobId, workerId, completedAt, sanitizedReason, EmbeddingJobStatus.CANCELED);
    }

    @Override
    public Optional<EmbeddingJob> findByIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_KEY)) {
            statement.setString(1, JOB_TYPE);
            statement.setString(2, idempotencyKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                EmbeddingJob job = map(rows);
                if (rows.next()) {
                    throw new SQLException("Embedding idempotency uniqueness invariant is violated");
                }
                return Optional.of(job);
            }
        } catch (SQLException | RuntimeException failure) {
            throw failure instanceof PetPersistenceException existing
                    ? existing : new PetPersistenceException("Could not read embedding job", failure);
        }
    }

    private void terminalError(
            UUID jobId,
            String workerId,
            Instant completedAt,
            String error,
            EmbeddingJobStatus status) {
        executeLocked(TERMINAL_ERROR, statement -> {
            statement.setString(1, status.name());
            statement.setString(2, boundedError(error));
            statement.setObject(3, utc(completedAt));
            statement.setObject(4, utc(completedAt));
            statement.setString(5, jobId.toString());
            statement.setString(6, workerId);
        }, status == EmbeddingJobStatus.FAILED
                ? "fail embedding job" : "cancel embedding job");
    }

    private void executeLocked(String sql, Binder binder, String operation) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            requireOne(statement.executeUpdate(), operation);
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not " + operation, failure);
        }
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not claim embedding jobs", failure);
        }
    }

    private static EmbeddingJob map(ResultSet rows) throws SQLException {
        Payload payload = parsePayload(rows.getString("payload_json"));
        return new EmbeddingJob(
                UUID.fromString(rows.getString("job_id")),
                UUID.fromString(rows.getString("pet_id")),
                payload.memoryId(), payload.memoryVersion(), payload.embeddingModel(),
                rows.getString("idempotency_key"),
                EmbeddingJobStatus.valueOf(rows.getString("status")),
                rows.getInt("attempt_count"),
                instant(rows, "not_before"),
                Optional.ofNullable(rows.getString("locked_by")),
                nullableInstant(rows, "locked_until"),
                Optional.ofNullable(rows.getString("last_error_sanitized")));
    }

    private static String payload(EmbeddingJob job) {
        JsonObject root = new JsonObject();
        root.addProperty("memoryId", job.memoryId().toString());
        root.addProperty("memoryVersion", job.memoryVersion());
        root.addProperty("embeddingModel", job.embeddingModel());
        return root.toString();
    }

    private static Payload parsePayload(String json) throws SQLException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()
                    || !parsed.getAsJsonObject().keySet().equals(PAYLOAD_FIELDS)) {
                throw new IllegalArgumentException("unexpected fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            return new Payload(
                    UUID.fromString(root.get("memoryId").getAsString()),
                    root.get("memoryVersion").getAsLong(),
                    root.get("embeddingModel").getAsString());
        } catch (RuntimeException malformed) {
            throw new SQLException("Invalid embedding job payload", malformed);
        }
    }

    private static void validateSame(EmbeddingJob existing, EmbeddingJob proposed) {
        if (!existing.petId().equals(proposed.petId())
                || !existing.memoryId().equals(proposed.memoryId())
                || existing.memoryVersion() != proposed.memoryVersion()
                || !existing.embeddingModel().equals(proposed.embeddingModel())) {
            throw new IllegalStateException("Embedding idempotency key has conflicting content");
        }
    }

    private static Optional<Instant> nullableInstant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant(ZoneOffset.UTC));
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        return nullableInstant(rows, column)
                .orElseThrow(() -> new SQLException(column + " cannot be null"));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(Objects.requireNonNull(instant, "instant"), ZoneOffset.UTC);
    }

    private static String boundedError(String error) {
        Objects.requireNonNull(error, "error");
        String normalized = error.strip();
        if (normalized.isEmpty()) {
            return "unspecified failure";
        }
        return normalized.length() <= 1_000 ? normalized : normalized.substring(0, 1_000);
    }

    private static boolean isConstraintViolation(SQLException failure) {
        return failure.getSQLState() != null && failure.getSQLState().startsWith("23");
    }

    private static void requireOne(int rows, String operation) throws SQLException {
        if (rows != 1) {
            throw new SQLException(operation + " did not affect exactly one row");
        }
    }

    private record Payload(UUID memoryId, long memoryVersion, String embeddingModel) {
        private Payload {
            if (memoryVersion < 1 || embeddingModel == null || embeddingModel.isBlank()) {
                throw new IllegalArgumentException("Invalid embedding payload");
            }
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }
}
