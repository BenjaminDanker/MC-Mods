package com.silver.aipets.service.persistence;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetPlacement;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.PlacementState;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.authority.TransitionResult;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetRecallWireCodec;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.service.placement.PetMutationResult;
import com.silver.aipets.service.placement.PetMutationResultCodec;
import com.silver.aipets.service.placement.PetMutationStatus;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import com.silver.aipets.service.recall.RecallCompensationRequest;
import com.silver.aipets.service.recall.RecallOperationResult;
import com.silver.aipets.service.recall.RecallPersistenceRequest;

/** MariaDB-compatible transactional adapter for the V001 pets/traits/mood tables. */
public final class JdbcPetRepository implements PetRepository {
    private static final String SELECT_AGGREGATE = """
            SELECT
                p.pet_id, p.owner_uuid, p.name, p.mob_type, p.variant_id, p.scale,
                p.appearance_seed, p.placement_state, p.placed_server, p.placed_dimension,
                p.placed_x, p.placed_y, p.placed_z, p.entity_uuid, p.transfer_id,
                p.transfer_source_server, p.transfer_source_entity_uuid,
                p.transfer_destination_server, p.transfer_started_at, p.transfer_expires_at,
                p.record_version, p.created_at, p.updated_at,
                t.curiosity, t.boldness, t.playfulness, t.expressiveness, t.independence,
                t.attachment, t.trust, t.security, t.relationship_summary,
                t.summary_version, t.updated_at AS traits_updated_at,
                m.content, m.excited, m.anxious, m.tired,
                m.last_decay_at, m.updated_at AS mood_updated_at
            FROM pets p
            INNER JOIN pet_traits t ON t.pet_id = p.pet_id
            INNER JOIN pet_mood m ON m.pet_id = p.pet_id
            """;
    private static final String INSERT_PET = """
            INSERT INTO pets (
                pet_id, owner_uuid, name, mob_type, variant_id, scale, appearance_seed,
                placement_state, placed_server, placed_dimension, placed_x, placed_y, placed_z,
                entity_uuid, transfer_id, transfer_source_server, transfer_source_entity_uuid,
                transfer_destination_server, transfer_started_at, transfer_expires_at,
                record_version, updated_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_TRAITS = """
            INSERT INTO pet_traits (
                pet_id, curiosity, boldness, playfulness, expressiveness, independence,
                attachment, trust, security, relationship_summary, summary_version, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_MOOD = """
            INSERT INTO pet_mood (
                pet_id, content, excited, anxious, tired, last_decay_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_SLEEP_STATE = """
            INSERT INTO pet_sleep_state (
                pet_id, sleeping, forced_sleep_due_at, owner_network_online,
                absence_sleep_triggered, last_presence_update_at, updated_at
            ) VALUES (?, FALSE, ?, FALSE, FALSE, ?, ?)
            """;
    private static final String UPDATE_PET = """
            UPDATE pets SET
                name = ?, mob_type = ?, variant_id = ?, scale = ?, appearance_seed = ?,
                placement_state = ?, placed_server = ?, placed_dimension = ?, placed_x = ?,
                placed_y = ?, placed_z = ?, entity_uuid = ?, transfer_id = ?,
                transfer_source_server = ?, transfer_source_entity_uuid = ?,
                transfer_destination_server = ?, transfer_started_at = ?, transfer_expires_at = ?,
                record_version = ?, updated_at = ?
            WHERE pet_id = ? AND record_version = ?
            """;
    private static final String UPDATE_TRAITS = """
            UPDATE pet_traits SET curiosity = ?, boldness = ?, playfulness = ?,
                expressiveness = ?, independence = ?, attachment = ?, trust = ?, security = ?,
                relationship_summary = ?, summary_version = ?, updated_at = ?
            WHERE pet_id = ?
            """;
    private static final String UPDATE_MOOD = """
            UPDATE pet_mood SET content = ?, excited = ?, anxious = ?, tired = ?,
                last_decay_at = ?, updated_at = ?
            WHERE pet_id = ?
            """;
    private static final String INSERT_IDEMPOTENCY = """
            INSERT INTO idempotency_requests (
                scope, idempotency_key, request_fingerprint, owner_uuid, status,
                locked_until, created_at, updated_at, expires_at
            ) VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?)
            """;
    private static final String SELECT_IDEMPOTENCY = """
            SELECT request_fingerprint, status, response_json
            FROM idempotency_requests
            WHERE scope = ? AND idempotency_key = ?
            FOR UPDATE
            """;
    private static final String COMPLETE_IDEMPOTENCY = """
            UPDATE idempotency_requests SET
                pet_id = ?, owner_uuid = ?, status = 'SUCCEEDED', http_status = ?,
                result_resource_id = ?, response_json = ?, error_code = ?,
                locked_until = NULL, updated_at = ?
            WHERE scope = ? AND idempotency_key = ? AND status = 'IN_PROGRESS'
            """;
    private static final String SELECT_RECALL_OPERATION = """
            SELECT request_fingerprint, compensation_fingerprint, period_key, status, response_json
            FROM pet_recall_usage WHERE operation_id = ? FOR UPDATE
            """;
    private static final String SELECT_RECALL_OPERATION_READ = """
            SELECT request_fingerprint, compensation_fingerprint, period_key, status, response_json
            FROM pet_recall_usage WHERE operation_id = ?
            """;
    private static final String SELECT_CONSUMED_RECALL = """
            SELECT operation_id FROM pet_recall_usage
            WHERE pet_id = ? AND consumed_period_key = ? FOR UPDATE
            """;
    private static final String INSERT_RECALL = """
            INSERT INTO pet_recall_usage (
                pet_id, period_key, consumed_period_key, operation_id, request_fingerprint,
                started_at, used_at, completed_at, source_server, source_dimension,
                destination_server, destination_dimension, status, response_json,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String COMPLETE_RECALL_SUCCESS = """
            UPDATE pet_recall_usage SET consumed_period_key = period_key, used_at = ?,
                completed_at = ?, status = 'SUCCEEDED', response_json = ?, updated_at = ?
            WHERE operation_id = ? AND status = 'STARTED'
            """;
    private static final String FAIL_RECALL = """
            UPDATE pet_recall_usage SET consumed_period_key = NULL, used_at = NULL,
                completed_at = ?, status = 'FAILED', response_json = ?,
                compensation_fingerprint = ?, updated_at = ?
            WHERE operation_id = ? AND status = ?
            """;

    private final DataSource dataSource;
    private final AppearanceRules appearanceRules;
    private final PetMutationResultCodec mutationCodec;
    private final PetRecallWireCodec recallCodec;

    public JdbcPetRepository(DataSource dataSource, AppearanceRules appearanceRules) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.appearanceRules = Objects.requireNonNull(appearanceRules, "appearanceRules");
        this.mutationCodec = new PetMutationResultCodec(new PetWireCodec(appearanceRules));
        this.recallCodec = new PetRecallWireCodec(new PetWireCodec(appearanceRules));
    }

    @Override
    public Optional<Pet> findById(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        return findOne("p.pet_id = ?", petId.toString());
    }

    @Override
    public Optional<Pet> findByOwner(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return findOne("p.owner_uuid = ?", ownerUuid.toString());
    }

    @Override
    public List<Pet> findExpiredTransfers(Instant dueAtInclusive, int limit) {
        Objects.requireNonNull(dueAtInclusive, "dueAtInclusive");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        String sql = SELECT_AGGREGATE + """
                 WHERE p.placement_state = 'TRANSFERRING'
                   AND p.transfer_expires_at <= ?
                 ORDER BY p.transfer_expires_at, p.pet_id
                 LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, utc(dueAtInclusive));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<Pet> due = new ArrayList<>();
                while (rows.next()) due.add(readPet(rows));
                return List.copyOf(due);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not scan expired pet transfers", failure);
        }
    }

    @Override
    public CreatePetResult createIfOwnerAbsent(Pet proposedPet) {
        Objects.requireNonNull(proposedPet, "proposedPet");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertAggregate(connection, proposedPet);
                connection.commit();
                return CreatePetResult.created(proposedPet);
            } catch (SQLException failure) {
                rollbackQuietly(connection, failure);
                if (isConstraintViolation(failure)) {
                    Optional<Pet> existing = findOne(
                            connection,
                            "p.owner_uuid = ?",
                            proposedPet.ownerUuid().toString(),
                            false);
                    if (existing.isPresent()) {
                        return CreatePetResult.existing(existing.orElseThrow());
                    }
                    if (findOne(
                            connection,
                            "p.pet_id = ?",
                            proposedPet.petId().toString(),
                            false).isPresent()) {
                        throw new PetPersistenceException(
                                "Pet ID collision for a different owner", failure);
                    }
                }
                throw persistenceFailure("Could not atomically create pet", failure);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not obtain pet persistence connection", failure);
        }
    }

    @Override
    public CompareAndSetResult compareAndSet(UUID petId, long expectedVersion, Pet replacement) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(replacement, "replacement");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Pet> found = findOne(connection, "p.pet_id = ?", petId.toString(), true);
                if (found.isEmpty()) {
                    connection.rollback();
                    return CompareAndSetResult.notFound();
                }
                Pet current = found.orElseThrow();
                if (current.recordVersion() != expectedVersion) {
                    connection.rollback();
                    return CompareAndSetResult.versionMismatch(current);
                }
                validateReplacement(current, replacement);
                if (updatePet(connection, petId, expectedVersion, replacement) != 1) {
                    connection.rollback();
                    return findOne(
                                    connection,
                                    "p.pet_id = ?",
                                    petId.toString(),
                                    false)
                            .map(CompareAndSetResult::versionMismatch)
                            .orElseGet(CompareAndSetResult::notFound);
                }
                updateTraits(connection, replacement);
                updateMood(connection, replacement);
                connection.commit();
                return CompareAndSetResult.updated(replacement);
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof PetPersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw persistenceFailure("Could not compare-and-set pet", failure);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not obtain pet persistence connection", failure);
        }
    }

    @Override
    public IdempotentMutationResult mutateIdempotently(
            IdempotencyRequest request,
            Function<Pet, TransitionResult> transition) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(transition, "transition");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                IdempotentMutationResult replay = reserveOrReplay(connection, request);
                if (replay != null) {
                    connection.rollback();
                    return replay;
                }

                Optional<Pet> found = findOne(
                        connection, "p.pet_id = ?", request.petId().toString(), true);
                PetMutationResult result;
                if (found.isEmpty()) {
                    result = PetMutationResult.notFound();
                } else {
                    Pet current = found.orElseThrow();
                    TransitionResult proposed = transition.apply(current);
                    if (!proposed.applied()) {
                        result = PetMutationResult.rejected(current, proposed.failureOrThrow());
                    } else {
                        Pet replacement = proposed.pet();
                        validateReplacement(current, replacement);
                        requireOne(
                                updatePet(connection, request.petId(), current.recordVersion(), replacement),
                                "idempotent pets update");
                        updateTraits(connection, replacement);
                        updateMood(connection, replacement);
                        result = PetMutationResult.applied(replacement);
                    }
                }
                completeIdempotency(connection, request, result);
                connection.commit();
                return IdempotentMutationResult.executed(result);
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof PetPersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw persistenceFailure("Could not execute idempotent pet mutation", failure);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not obtain pet persistence connection", failure);
        }
    }

    @Override
    public RecallOperationResult recall(RecallPersistenceRequest request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<RecallRow> replay = findRecallRead(connection, request.operationId());
                if (replay.isPresent()) {
                    connection.rollback();
                    RecallRow row = replay.orElseThrow();
                    if (!row.fingerprint().equals(request.requestFingerprint())) {
                        return RecallOperationResult.keyConflict();
                    }
                    if (row.responseJson() == null) {
                        throw new PetPersistenceException("Recall operation is still STARTED", null);
                    }
                    return RecallOperationResult.replayed(recallCodec.decodeResult(row.responseJson()));
                }

                Optional<Pet> found = findOne(
                        connection, "p.pet_id = ?", request.petId().toString(), true);
                if (found.isEmpty()) {
                    connection.rollback();
                    return RecallOperationResult.executed(new PetRecallWireResult(
                            PetRecallWireStatus.NOT_FOUND,
                            Optional.empty(), Optional.empty(), Optional.empty()));
                }
                Pet current = found.orElseThrow();
                replay = findRecall(connection, request.operationId());
                if (replay.isPresent()) {
                    connection.rollback();
                    RecallRow row = replay.orElseThrow();
                    if (!row.fingerprint().equals(request.requestFingerprint())) {
                        return RecallOperationResult.keyConflict();
                    }
                    if (row.responseJson() == null) {
                        throw new PetPersistenceException("Recall operation is still STARTED", null);
                    }
                    return RecallOperationResult.replayed(recallCodec.decodeResult(row.responseJson()));
                }
                if (hasConsumedRecall(connection, request.petId(), request.periodKey())) {
                    PetRecallWireResult unavailable = new PetRecallWireResult(
                            PetRecallWireStatus.UNAVAILABLE,
                            Optional.of(current), Optional.empty(),
                            Optional.of(request.nextAvailableAt()));
                    insertRecall(connection, request, current, "FAILED", recallCodec.encodeResult(unavailable));
                    connection.commit();
                    return RecallOperationResult.executed(unavailable);
                }

                TransitionResult proposed = com.silver.aipets.common.authority.PetTransitions.recall(
                        current, request.command());
                if (!proposed.applied()) {
                    PetRecallWireResult rejected = new PetRecallWireResult(
                            PetRecallWireStatus.REJECTED,
                            Optional.of(current), Optional.of(proposed.failureOrThrow()), Optional.empty());
                    insertRecall(connection, request, current, "FAILED", recallCodec.encodeResult(rejected));
                    connection.commit();
                    return RecallOperationResult.executed(rejected);
                }

                insertRecall(connection, request, current, "STARTED", null);
                Pet replacement = proposed.pet();
                validateReplacement(current, replacement);
                requireOne(updatePet(connection, request.petId(), current.recordVersion(), replacement),
                        "recall pets update");
                updateTraits(connection, replacement);
                updateMood(connection, replacement);
                PetRecallWireResult applied = new PetRecallWireResult(
                        PetRecallWireStatus.APPLIED,
                        Optional.of(replacement), Optional.empty(),
                        Optional.of(request.nextAvailableAt()));
                Instant now = request.command().occurredAt();
                try (PreparedStatement statement = connection.prepareStatement(COMPLETE_RECALL_SUCCESS)) {
                    statement.setObject(1, utc(now));
                    statement.setObject(2, utc(now));
                    statement.setString(3, recallCodec.encodeResult(applied));
                    statement.setObject(4, utc(now));
                    statement.setString(5, request.operationId().toString());
                    requireOne(statement.executeUpdate(), "recall success completion");
                }
                connection.commit();
                return RecallOperationResult.executed(applied);
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof PetPersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw persistenceFailure("Could not execute monthly recall", failure);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not obtain recall persistence connection", failure);
        }
    }

    @Override
    public RecallOperationResult compensateRecallFailure(RecallCompensationRequest request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Pet> found = findOne(
                        connection, "p.pet_id = ?", request.petId().toString(), true);
                Optional<RecallRow> stored = findRecall(connection, request.recallOperationId());
                if (found.isEmpty() || stored.isEmpty()) {
                    connection.rollback();
                    return RecallOperationResult.executed(new PetRecallWireResult(
                            PetRecallWireStatus.NOT_FOUND,
                            Optional.empty(), Optional.empty(), Optional.empty()));
                }
                RecallRow row = stored.orElseThrow();
                if (!row.status().equals("SUCCEEDED")) {
                    connection.rollback();
                    if (row.compensationFingerprint() != null
                            && !row.compensationFingerprint().equals(request.requestFingerprint())) {
                        return RecallOperationResult.keyConflict();
                    }
                    return RecallOperationResult.replayed(recallCodec.decodeResult(row.responseJson()));
                }
                Pet current = found.orElseThrow();
                TransitionResult proposed =
                        com.silver.aipets.common.authority.PetTransitions.compensateRecallFailure(
                                current, request.command());
                if (!proposed.applied()) {
                    connection.rollback();
                    return RecallOperationResult.executed(new PetRecallWireResult(
                            PetRecallWireStatus.REJECTED,
                            Optional.of(current), Optional.of(proposed.failureOrThrow()), Optional.empty()));
                }
                Pet replacement = proposed.pet();
                validateReplacement(current, replacement);
                requireOne(updatePet(connection, request.petId(), current.recordVersion(), replacement),
                        "recall compensation pets update");
                updateTraits(connection, replacement);
                updateMood(connection, replacement);
                PetRecallWireResult compensated = new PetRecallWireResult(
                        PetRecallWireStatus.COMPENSATED,
                        Optional.of(replacement), Optional.empty(), Optional.empty());
                Instant now = request.command().occurredAt();
                try (PreparedStatement statement = connection.prepareStatement(FAIL_RECALL)) {
                    statement.setObject(1, utc(now));
                    statement.setString(2, recallCodec.encodeResult(compensated));
                    statement.setString(3, request.requestFingerprint());
                    statement.setObject(4, utc(now));
                    statement.setString(5, request.recallOperationId().toString());
                    statement.setString(6, "SUCCEEDED");
                    requireOne(statement.executeUpdate(), "recall compensation completion");
                }
                connection.commit();
                return RecallOperationResult.executed(compensated);
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof PetPersistenceException persistenceException) {
                    throw persistenceException;
                }
                throw persistenceFailure("Could not compensate monthly recall", failure);
            }
        } catch (SQLException failure) {
            throw persistenceFailure("Could not obtain recall persistence connection", failure);
        }
    }

    private Optional<RecallRow> findRecall(Connection connection, UUID operationId)
            throws SQLException {
        return findRecall(connection, operationId, SELECT_RECALL_OPERATION);
    }

    private Optional<RecallRow> findRecallRead(Connection connection, UUID operationId)
            throws SQLException {
        return findRecall(connection, operationId, SELECT_RECALL_OPERATION_READ);
    }

    private Optional<RecallRow> findRecall(
            Connection connection, UUID operationId, String query) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, operationId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                RecallRow row = new RecallRow(
                        rows.getString("request_fingerprint"),
                        rows.getString("compensation_fingerprint"),
                        rows.getString("period_key"),
                        rows.getString("status"),
                        rows.getString("response_json"));
                if (rows.next()) throw new PetPersistenceException("Recall operation is not unique", null);
                return Optional.of(row);
            }
        }
    }

    private static boolean hasConsumedRecall(
            Connection connection, UUID petId, String periodKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CONSUMED_RECALL)) {
            statement.setString(1, petId.toString());
            statement.setString(2, periodKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void insertRecall(
            Connection connection,
            RecallPersistenceRequest request,
            Pet source,
            String status,
            String responseJson) throws SQLException {
        boolean succeeded = status.equals("SUCCEEDED");
        boolean failed = status.equals("FAILED");
        String sourceServer = null;
        String sourceDimension = null;
        if (source.placement() instanceof PlacedPlacement placed) {
            sourceServer = placed.backendId().value();
            sourceDimension = placed.dimensionId().toString();
        }
        Instant now = request.command().occurredAt();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RECALL)) {
            statement.setString(1, request.petId().toString());
            statement.setString(2, request.periodKey());
            if (succeeded) statement.setString(3, request.periodKey()); else statement.setNull(3, Types.CHAR);
            statement.setString(4, request.operationId().toString());
            statement.setString(5, request.requestFingerprint());
            statement.setObject(6, utc(now));
            if (succeeded) statement.setObject(7, utc(now)); else statement.setNull(7, Types.TIMESTAMP);
            if (failed || succeeded) statement.setObject(8, utc(now)); else statement.setNull(8, Types.TIMESTAMP);
            if (sourceServer == null) statement.setNull(9, Types.VARCHAR); else statement.setString(9, sourceServer);
            if (sourceDimension == null) statement.setNull(10, Types.VARCHAR); else statement.setString(10, sourceDimension);
            statement.setString(11, request.command().destinationBackendId().value());
            statement.setString(12, request.command().destinationDimensionId().toString());
            statement.setString(13, status);
            if (responseJson == null) statement.setNull(14, Types.LONGVARCHAR); else statement.setString(14, responseJson);
            statement.setObject(15, utc(now));
            statement.setObject(16, utc(now));
            requireOne(statement.executeUpdate(), "recall operation insert");
        }
    }

    private record RecallRow(
            String fingerprint,
            String compensationFingerprint,
            String periodKey,
            String status,
            String responseJson) {
    }

    /** Returns null only when this transaction successfully reserved a new key. */
    private IdempotentMutationResult reserveOrReplay(
            Connection connection,
            IdempotencyRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_IDEMPOTENCY)) {
            statement.setString(1, request.scope());
            statement.setString(2, request.operationId().toString());
            statement.setString(3, request.requestFingerprint());
            if (request.ownerUuid().isPresent()) {
                statement.setString(4, request.ownerUuid().orElseThrow().toString());
            } else {
                statement.setNull(4, Types.CHAR);
            }
            statement.setObject(5, utc(request.lockedUntil()));
            statement.setObject(6, utc(request.createdAt()));
            statement.setObject(7, utc(request.createdAt()));
            statement.setObject(8, utc(request.expiresAt()));
            requireOne(statement.executeUpdate(), "idempotency reservation");
            return null;
        } catch (SQLException failure) {
            if (!isConstraintViolation(failure)) {
                throw failure;
            }
            Optional<LedgerRow> existing = findLedger(connection, request);
            if (existing.isEmpty()) {
                throw failure;
            }
            LedgerRow row = existing.orElseThrow();
            if (!row.fingerprint().equals(request.requestFingerprint())) {
                return IdempotentMutationResult.keyConflict();
            }
            if (row.status().equals("IN_PROGRESS")) {
                return IdempotentMutationResult.inProgress();
            }
            if (row.responseJson() == null) {
                throw new PetPersistenceException(
                        "Completed idempotency record has no response", null);
            }
            return IdempotentMutationResult.replayed(mutationCodec.decode(row.responseJson()));
        }
    }

    private Optional<LedgerRow> findLedger(
            Connection connection,
            IdempotencyRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_IDEMPOTENCY)) {
            statement.setString(1, request.scope());
            statement.setString(2, request.operationId().toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                LedgerRow row = new LedgerRow(
                        rows.getString("request_fingerprint"),
                        rows.getString("status"),
                        rows.getString("response_json"));
                if (rows.next()) {
                    throw new PetPersistenceException(
                            "Idempotency uniqueness invariant is violated", null);
                }
                return Optional.of(row);
            }
        }
    }

    private void completeIdempotency(
            Connection connection,
            IdempotencyRequest request,
            PetMutationResult result) throws SQLException {
        Pet resultPet = result.pet().orElse(null);
        UUID ownerUuid = resultPet == null
                ? request.ownerUuid().orElse(null)
                : resultPet.ownerUuid();
        try (PreparedStatement statement = connection.prepareStatement(COMPLETE_IDEMPOTENCY)) {
            if (resultPet == null) {
                statement.setNull(1, Types.CHAR);
            } else {
                statement.setString(1, resultPet.petId().toString());
            }
            if (ownerUuid == null) {
                statement.setNull(2, Types.CHAR);
            } else {
                statement.setString(2, ownerUuid.toString());
            }
            statement.setInt(3, httpStatus(result.status()));
            if (resultPet == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, resultPet.petId().toString());
            }
            statement.setString(5, mutationCodec.encode(result));
            String errorCode = errorCode(result);
            if (errorCode == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, errorCode);
            }
            statement.setObject(7, utc(request.createdAt()));
            statement.setString(8, request.scope());
            statement.setString(9, request.operationId().toString());
            requireOne(statement.executeUpdate(), "idempotency completion");
        }
    }

    private static int httpStatus(PetMutationStatus status) {
        return switch (status) {
            case APPLIED -> 200;
            case NOT_FOUND -> 404;
            case REJECTED, CONCURRENT_MODIFICATION -> 409;
        };
    }

    private static String errorCode(PetMutationResult result) {
        if (result.failure().isPresent()) {
            return result.failure().orElseThrow().name();
        }
        return switch (result.status()) {
            case APPLIED -> null;
            case NOT_FOUND -> "PET_NOT_FOUND";
            case CONCURRENT_MODIFICATION -> "CONCURRENT_MODIFICATION";
            case REJECTED -> throw new IllegalStateException("Rejected mutation has no failure");
        };
    }

    private Optional<Pet> findOne(String predicate, String value) {
        try (Connection connection = dataSource.getConnection()) {
            return findOne(connection, predicate, value, false);
        } catch (SQLException failure) {
            throw persistenceFailure("Could not read pet", failure);
        }
    }

    private Optional<Pet> findOne(
            Connection connection,
            String predicate,
            String value,
            boolean forUpdate) throws SQLException {
        String sql = SELECT_AGGREGATE + " WHERE " + predicate + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                Pet pet = readPet(rows);
                if (rows.next()) {
                    throw new PetPersistenceException("Pet uniqueness invariant is violated", null);
                }
                return Optional.of(pet);
            }
        }
    }

    private void insertAggregate(Connection connection, Pet pet) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PET)) {
            int index = 1;
            statement.setString(index++, pet.petId().toString());
            statement.setString(index++, pet.ownerUuid().toString());
            index = bindMutablePet(statement, index, pet);
            statement.setObject(index, utc(pet.createdAt()));
            requireOne(statement.executeUpdate(), "pets insert");
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRAITS)) {
            statement.setString(1, pet.petId().toString());
            bindTraits(statement, 2, pet.traits());
            requireOne(statement.executeUpdate(), "pet_traits insert");
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MOOD)) {
            statement.setString(1, pet.petId().toString());
            bindMood(statement, 2, pet.mood());
            requireOne(statement.executeUpdate(), "pet_mood insert");
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SLEEP_STATE)) {
            statement.setString(1, pet.petId().toString());
            statement.setObject(2, utc(pet.createdAt().plus(Duration.ofHours(23))));
            statement.setObject(3, utc(pet.createdAt()));
            statement.setObject(4, utc(pet.createdAt()));
            requireOne(statement.executeUpdate(), "pet_sleep_state insert");
        }
    }

    private int updatePet(
            Connection connection,
            UUID petId,
            long expectedVersion,
            Pet replacement) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_PET)) {
            int index = bindMutablePet(statement, 1, replacement);
            statement.setString(index++, petId.toString());
            statement.setLong(index, expectedVersion);
            return statement.executeUpdate();
        }
    }

    private void updateTraits(Connection connection, Pet pet) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TRAITS)) {
            int index = bindTraits(statement, 1, pet.traits());
            statement.setString(index, pet.petId().toString());
            requireOne(statement.executeUpdate(), "pet_traits update");
        }
    }

    private void updateMood(Connection connection, Pet pet) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_MOOD)) {
            int index = bindMood(statement, 1, pet.mood());
            statement.setString(index, pet.petId().toString());
            requireOne(statement.executeUpdate(), "pet_mood update");
        }
    }

    private static int bindMutablePet(PreparedStatement statement, int index, Pet pet)
            throws SQLException {
        statement.setString(index++, pet.name());
        statement.setString(index++, pet.appearance().entityTypeId().value());
        statement.setString(index++, pet.appearance().variantId().value());
        statement.setBigDecimal(index++, BigDecimal.valueOf(pet.appearance().scale()));
        if (pet.appearance().appearanceSeed().isPresent()) {
            statement.setLong(index++, pet.appearance().appearanceSeed().getAsLong());
        } else {
            statement.setNull(index++, Types.BIGINT);
        }
        index = bindPlacement(statement, index, pet.placement());
        statement.setLong(index++, pet.recordVersion());
        statement.setObject(index++, utc(pet.updatedAt()));
        return index;
    }

    private static int bindPlacement(
            PreparedStatement statement,
            int index,
            PetPlacement placement) throws SQLException {
        statement.setString(index++, placement.state().name());
        if (placement instanceof PlacedPlacement placed) {
            statement.setString(index++, placed.backendId().value());
            statement.setString(index++, placed.dimensionId().toString());
            statement.setDouble(index++, placed.position().x());
            statement.setDouble(index++, placed.position().y());
            statement.setDouble(index++, placed.position().z());
            if (placed.entityUuid().isPresent()) {
                statement.setString(index++, placed.entityUuid().orElseThrow().toString());
            } else {
                statement.setNull(index++, Types.CHAR);
            }
        } else {
            index = setNulls(statement, index, Types.VARCHAR, Types.VARCHAR,
                    Types.DOUBLE, Types.DOUBLE, Types.DOUBLE, Types.CHAR);
        }

        if (placement instanceof TransferringPlacement transferring) {
            TransferMetadata transfer = transferring.transfer();
            statement.setString(index++, transfer.transferId().toString());
            statement.setString(index++, transfer.sourceBackendId().value());
            statement.setString(index++, transfer.sourceEntityUuid().toString());
            statement.setString(index++, transfer.destinationBackendId().value());
            statement.setObject(index++, utc(transfer.startedAt()));
            statement.setObject(index++, utc(transfer.expiresAt()));
        } else {
            index = setNulls(statement, index, Types.CHAR, Types.VARCHAR, Types.CHAR,
                    Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP);
        }
        return index;
    }

    private static int bindTraits(PreparedStatement statement, int index, PetTraits traits)
            throws SQLException {
        statement.setInt(index++, traits.curiosity());
        statement.setInt(index++, traits.boldness());
        statement.setInt(index++, traits.playfulness());
        statement.setInt(index++, traits.expressiveness());
        statement.setInt(index++, traits.independence());
        statement.setInt(index++, traits.attachment());
        statement.setInt(index++, traits.trust());
        statement.setInt(index++, traits.security());
        statement.setString(index++, traits.relationshipSummary());
        statement.setLong(index++, traits.summaryVersion());
        statement.setObject(index++, utc(traits.updatedAt()));
        return index;
    }

    private static int bindMood(PreparedStatement statement, int index, PetMood mood)
            throws SQLException {
        statement.setInt(index++, mood.content());
        statement.setInt(index++, mood.excited());
        statement.setInt(index++, mood.anxious());
        statement.setInt(index++, mood.tired());
        statement.setObject(index++, utc(mood.lastDecayAt()));
        statement.setObject(index++, utc(mood.updatedAt()));
        return index;
    }

    private Pet readPet(ResultSet rows) throws SQLException {
        UUID petId = uuid(rows, "pet_id");
        PetAppearance appearance = PetAppearance.create(
                PetSpecies.fromEntityTypeId(rows.getString("mob_type")),
                rows.getString("variant_id"),
                rows.getDouble("scale"),
                nullableLong(rows, "appearance_seed"),
                appearanceRules);
        PetTraits traits = new PetTraits(
                rows.getInt("curiosity"),
                rows.getInt("boldness"),
                rows.getInt("playfulness"),
                rows.getInt("expressiveness"),
                rows.getInt("independence"),
                rows.getInt("attachment"),
                rows.getInt("trust"),
                rows.getInt("security"),
                rows.getString("relationship_summary"),
                rows.getLong("summary_version"),
                instant(rows, "traits_updated_at"));
        PetMood mood = new PetMood(
                rows.getInt("content"),
                rows.getInt("excited"),
                rows.getInt("anxious"),
                rows.getInt("tired"),
                instant(rows, "last_decay_at"),
                instant(rows, "mood_updated_at"));
        return new Pet(
                petId,
                uuid(rows, "owner_uuid"),
                rows.getString("name"),
                appearance,
                traits,
                mood,
                readPlacement(rows),
                rows.getLong("record_version"),
                instant(rows, "created_at"),
                instant(rows, "updated_at"));
    }

    private static PetPlacement readPlacement(ResultSet rows) throws SQLException {
        PlacementState state = PlacementState.valueOf(rows.getString("placement_state"));
        return switch (state) {
            case HELD -> HeldPlacement.INSTANCE;
            case PLACED -> {
                BackendId backend = new BackendId(rows.getString("placed_server"));
                DimensionId dimension = DimensionId.parse(rows.getString("placed_dimension"));
                WorldPosition position = new WorldPosition(
                        rows.getDouble("placed_x"),
                        rows.getDouble("placed_y"),
                        rows.getDouble("placed_z"));
                String entityUuid = rows.getString("entity_uuid");
                yield entityUuid == null
                        ? PlacedPlacement.virtualized(backend, dimension, position)
                        : PlacedPlacement.materialized(
                                backend, dimension, position, UUID.fromString(entityUuid));
            }
            case TRANSFERRING -> new TransferringPlacement(new TransferMetadata(
                    uuid(rows, "transfer_id"),
                    new BackendId(rows.getString("transfer_source_server")),
                    uuid(rows, "transfer_source_entity_uuid"),
                    new BackendId(rows.getString("transfer_destination_server")),
                    instant(rows, "transfer_started_at"),
                    instant(rows, "transfer_expires_at")));
        };
    }

    private static UUID uuid(ResultSet rows, String column) throws SQLException {
        return UUID.fromString(rows.getString(column));
    }

    private static Long nullableLong(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        if (value == null) {
            throw new PetPersistenceException("Required UTC timestamp is null: " + column, null);
        }
        return value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static int setNulls(PreparedStatement statement, int index, int... types)
            throws SQLException {
        for (int type : types) {
            statement.setNull(index++, type);
        }
        return index;
    }

    private static void requireOne(int changed, String operation) {
        if (changed != 1) {
            throw new PetPersistenceException(operation + " changed " + changed + " rows", null);
        }
    }

    private static void validateReplacement(Pet current, Pet replacement) {
        if (!current.petId().equals(replacement.petId())
                || !current.ownerUuid().equals(replacement.ownerUuid())) {
            throw new IllegalArgumentException("CAS cannot replace pet or owner identity");
        }
        if (!current.createdAt().equals(replacement.createdAt())) {
            throw new IllegalArgumentException("CAS cannot replace creation time");
        }
        if (replacement.recordVersion() != current.recordVersion() + 1) {
            throw new IllegalArgumentException("CAS replacement must increment recordVersion exactly once");
        }
    }

    private static boolean isConstraintViolation(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if (current.getSQLState() != null && current.getSQLState().startsWith("23")) {
                return true;
            }
        }
        return false;
    }

    private static void rollbackQuietly(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static PetPersistenceException persistenceFailure(String operation, Throwable cause) {
        return new PetPersistenceException(operation, cause);
    }

    private record LedgerRow(String fingerprint, String status, String responseJson) {
    }
}
