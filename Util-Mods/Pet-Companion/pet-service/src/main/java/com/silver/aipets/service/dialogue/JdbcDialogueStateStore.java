package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitCategory;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.DialogueContextUsageWireCodec;
import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** MariaDB atomic event/usage/state/audit adapter using V001 tables and UTC daily budgets. */
public final class JdbcDialogueStateStore implements DialogueStateStore {
    private static final String LOCK_STATE = """
            SELECT t.curiosity, t.boldness, t.playfulness, t.expressiveness,
                   t.independence, t.attachment, t.trust, t.security,
                   t.relationship_summary, t.summary_version, t.updated_at,
                   m.content, m.excited, m.anxious, m.tired,
                   m.last_decay_at, m.updated_at AS mood_updated_at
            FROM pet_traits t JOIN pet_mood m ON m.pet_id = t.pet_id
            WHERE t.pet_id = ? FOR UPDATE
            """;
    private static final String DAILY_CHANGES = """
            SELECT trait_name, COALESCE(SUM(ABS(applied_delta)), 0) AS used
            FROM trait_change_audit
            WHERE pet_id = ? AND created_at >= ? AND created_at < ?
            GROUP BY trait_name
            """;
    private static final String INSERT_EVENT = """
            INSERT INTO pet_events (
                event_id, pet_id, owner_uuid, event_type, occurred_at,
                source_server, source_dimension, importance, summary,
                raw_player_text, raw_pet_reply, short_term_expires_at,
                prompt_eligible, consolidation_status, created_at)
            VALUES (?, ?, ?, 'DIALOGUE', ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 'PENDING', ?)
            """;
    private static final String UPDATE_TRAITS = """
            UPDATE pet_traits SET curiosity=?, boldness=?, playfulness=?, expressiveness=?,
                independence=?, attachment=?, trust=?, security=?, updated_at=?
            WHERE pet_id=?
            """;
    private static final String UPDATE_MOOD = """
            UPDATE pet_mood SET content=?, excited=?, anxious=?, tired=?, updated_at=?
            WHERE pet_id=?
            """;
    private static final String INSERT_AUDIT = """
            INSERT INTO trait_change_audit (
                change_id, pet_id, event_id, job_id, trait_name, old_value,
                proposed_delta, applied_delta, new_value, reason, created_at)
            VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, 'validated dialogue proposal', ?)
            """;
    private static final String INSERT_USAGE = """
            INSERT INTO ai_usage (
                call_id, pet_id, owner_uuid, operation, model, request_id, provider_id,
                input_tokens, cached_input_tokens, output_tokens, estimated_cost,
                latency_ms, status, error_category, context_json, created_at)
            VALUES (?, ?, ?, 'DIALOGUE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final Supplier<UUID> auditIds;

    public JdbcDialogueStateStore(DataSource dataSource, Supplier<UUID> auditIds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditIds = Objects.requireNonNull(auditIds, "auditIds");
    }

    @Override
    public DialoguePersistResult commit(DialoguePersistRequest request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                LockedState locked = lock(connection, request.expectedPet().petId());
                validateExpected(request, locked);
                Map<TraitName, Integer> dailyUsed = dailyChanges(
                        connection, request.expectedPet().petId(), request.occurredAt());
                Applied applied = apply(request, locked, dailyUsed);
                insertEvent(connection, request);
                updateTraits(connection, request, applied.traits());
                updateMood(connection, request, applied.mood());
                insertAudits(connection, request, locked.traits(), applied);
                insertUsage(connection, request.usage());
                connection.commit();
                return new DialoguePersistResult(
                        applied.traits(), applied.mood(), applied.traitDeltas(),
                        applied.moodDeltas(), request.output().importance()
                        .expiresAt(request.occurredAt()));
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not commit dialogue state", failure);
        }
    }

    @Override
    public void recordUsage(DialogueUsage usage) {
        Objects.requireNonNull(usage, "usage");
        try (Connection connection = dataSource.getConnection()) {
            insertUsage(connection, usage);
        } catch (SQLException failure) {
            if (!isConstraintViolation(failure)) {
                throw new PetPersistenceException("Could not persist dialogue usage", failure);
            }
            // Request IDs are idempotent; a retry must not duplicate usage.
        }
    }

    private static LockedState lock(Connection connection, UUID petId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_STATE)) {
            statement.setString(1, petId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Pet trait/mood state does not exist");
                }
                PetTraits traits = new PetTraits(
                        rows.getInt("curiosity"), rows.getInt("boldness"),
                        rows.getInt("playfulness"), rows.getInt("expressiveness"),
                        rows.getInt("independence"), rows.getInt("attachment"),
                        rows.getInt("trust"), rows.getInt("security"),
                        rows.getString("relationship_summary"), rows.getLong("summary_version"),
                        instant(rows, "updated_at"));
                PetMood mood = new PetMood(
                        rows.getInt("content"), rows.getInt("excited"),
                        rows.getInt("anxious"), rows.getInt("tired"),
                        instant(rows, "last_decay_at"), instant(rows, "mood_updated_at"));
                if (rows.next()) {
                    throw new SQLException("Pet trait/mood uniqueness invariant is violated");
                }
                return new LockedState(traits, mood);
            }
        }
    }

    private static void validateExpected(DialoguePersistRequest request, LockedState locked) {
        if (!locked.traits().equals(request.expectedPet().traits())
                || !locked.mood().equals(request.expectedPet().mood())) {
            throw new IllegalStateException("Authoritative pet state changed before dialogue commit");
        }
    }

    private static Map<TraitName, Integer> dailyChanges(
            Connection connection, UUID petId, Instant occurredAt) throws SQLException {
        LocalDate day = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        Instant start = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<TraitName, Integer> used = new EnumMap<>(TraitName.class);
        try (PreparedStatement statement = connection.prepareStatement(DAILY_CHANGES)) {
            statement.setString(1, petId.toString());
            statement.setObject(2, utc(start));
            statement.setObject(3, utc(end));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    used.put(TraitName.valueOf(
                            rows.getString("trait_name").toUpperCase(java.util.Locale.ROOT)),
                            rows.getInt("used"));
                }
            }
        }
        return used;
    }

    private static Applied apply(
            DialoguePersistRequest request,
            LockedState locked,
            Map<TraitName, Integer> dailyUsed) {
        int[] traits = java.util.Arrays.stream(TraitName.values())
                .mapToInt(locked.traits()::value).toArray();
        Map<TraitName, Integer> appliedTraits = new EnumMap<>(TraitName.class);
        for (TraitName trait : TraitName.values()) {
            int perEvent = trait.category() == TraitCategory.TEMPERAMENT ? 1 : 2;
            int daily = trait.category() == TraitCategory.TEMPERAMENT ? 3 : 6;
            int remaining = Math.max(0, daily - dailyUsed.getOrDefault(trait, 0));
            int bounded = clamp(request.output().proposedTraitDeltas().get(trait), -perEvent, perEvent);
            bounded = clamp(bounded, -remaining, remaining);
            int old = traits[trait.ordinal()];
            int next = clamp(old + bounded, 0, 100);
            traits[trait.ordinal()] = next;
            appliedTraits.put(trait, next - old);
        }
        int[] mood = java.util.Arrays.stream(MoodDimension.values())
                .mapToInt(locked.mood()::value).toArray();
        Map<MoodDimension, Integer> appliedMood = new EnumMap<>(MoodDimension.class);
        for (MoodDimension dimension : MoodDimension.values()) {
            int old = mood[dimension.ordinal()];
            int next = clamp(old + clamp(
                    request.output().proposedMoodDeltas().get(dimension), -10, 10), 0, 100);
            mood[dimension.ordinal()] = next;
            appliedMood.put(dimension, next - old);
        }
        PetTraits updatedTraits = new PetTraits(
                traits[0], traits[1], traits[2], traits[3], traits[4], traits[5], traits[6], traits[7],
                locked.traits().relationshipSummary(), locked.traits().summaryVersion(),
                request.occurredAt());
        PetMood updatedMood = new PetMood(
                mood[0], mood[1], mood[2], mood[3], locked.mood().lastDecayAt(),
                request.occurredAt());
        return new Applied(updatedTraits, updatedMood, appliedTraits, appliedMood);
    }

    private static void insertEvent(Connection connection, DialoguePersistRequest request)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
            statement.setString(1, request.eventId().toString());
            statement.setString(2, request.expectedPet().petId().toString());
            statement.setString(3, request.expectedPet().ownerUuid().toString());
            statement.setObject(4, utc(request.occurredAt()));
            statement.setString(5, request.gameContext().backend());
            statement.setString(6, request.gameContext().dimension());
            statement.setString(7, request.output().importance().name());
            statement.setString(8, request.output().memoryCandidate()
                    .orElse(request.output().reply()));
            statement.setString(9, request.normalizedPlayerMessage());
            statement.setString(10, request.output().reply());
            Optional<Instant> expiry = request.output().importance().expiresAt(request.occurredAt());
            if (expiry.isPresent()) {
                statement.setObject(11, utc(expiry.orElseThrow()));
            } else {
                statement.setNull(11, Types.TIMESTAMP);
            }
            statement.setObject(12, utc(request.occurredAt()));
            requireOne(statement.executeUpdate(), "dialogue event insert");
        }
    }

    private static void updateTraits(
            Connection connection, DialoguePersistRequest request, PetTraits traits) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TRAITS)) {
            int index = 1;
            for (TraitName trait : TraitName.values()) {
                statement.setInt(index++, traits.value(trait));
            }
            statement.setObject(index++, utc(request.occurredAt()));
            statement.setString(index, request.expectedPet().petId().toString());
            requireOne(statement.executeUpdate(), "trait update");
        }
    }

    private static void updateMood(
            Connection connection, DialoguePersistRequest request, PetMood mood) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_MOOD)) {
            int index = 1;
            for (MoodDimension dimension : MoodDimension.values()) {
                statement.setInt(index++, mood.value(dimension));
            }
            statement.setObject(index++, utc(request.occurredAt()));
            statement.setString(index, request.expectedPet().petId().toString());
            requireOne(statement.executeUpdate(), "mood update");
        }
    }

    private void insertAudits(
            Connection connection,
            DialoguePersistRequest request,
            PetTraits oldTraits,
            Applied applied) throws SQLException {
        for (TraitName trait : TraitName.values()) {
            int delta = applied.traitDeltas().get(trait);
            if (delta == 0) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT)) {
                statement.setString(1, Objects.requireNonNull(
                        auditIds.get(), "auditIds returned null").toString());
                statement.setString(2, request.expectedPet().petId().toString());
                statement.setString(3, request.eventId().toString());
                statement.setString(4, trait.name().toLowerCase(java.util.Locale.ROOT));
                statement.setInt(5, oldTraits.value(trait));
                statement.setInt(6, request.output().proposedTraitDeltas().get(trait));
                statement.setInt(7, delta);
                statement.setInt(8, applied.traits().value(trait));
                statement.setObject(9, utc(request.occurredAt()));
                requireOne(statement.executeUpdate(), "trait audit insert");
            }
        }
    }

    private static void insertUsage(Connection connection, DialogueUsage usage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USAGE)) {
            statement.setString(1, usage.callId().toString());
            statement.setString(2, usage.petId().toString());
            statement.setString(3, usage.ownerUuid().toString());
            statement.setString(4, usage.model());
            statement.setString(5, usage.requestId().toString());
            if (usage.providerId().isPresent()) statement.setString(6, usage.providerId().orElseThrow());
            else statement.setNull(6, Types.VARCHAR);
            statement.setInt(7, usage.inputTokens());
            statement.setInt(8, usage.cachedInputTokens());
            statement.setInt(9, usage.outputTokens());
            statement.setBigDecimal(10, usage.estimatedCost());
            statement.setLong(11, usage.latencyMillis());
            statement.setString(12, usage.status().name());
            if (usage.errorCategory().isPresent()) statement.setString(13, usage.errorCategory().orElseThrow());
            else statement.setNull(13, Types.VARCHAR);
            if (usage.contextUsage().isPresent()) {
                statement.setString(14, new DialogueContextUsageWireCodec().encode(
                        usage.contextUsage().orElseThrow()));
            } else {
                statement.setNull(14, Types.LONGVARCHAR);
            }
            statement.setObject(15, utc(usage.createdAt()));
            requireOne(statement.executeUpdate(), "AI usage insert");
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        if (value == null) throw new SQLException(column + " cannot be null");
        return value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isConstraintViolation(SQLException failure) {
        return failure.getSQLState() != null && failure.getSQLState().startsWith("23");
    }

    private static void requireOne(int rows, String operation) throws SQLException {
        if (rows != 1) throw new SQLException(operation + " did not affect exactly one row");
    }

    private record LockedState(PetTraits traits, PetMood mood) {
    }

    private record Applied(
            PetTraits traits,
            PetMood mood,
            Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas) {
    }
}
