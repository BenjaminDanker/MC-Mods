package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitCategory;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.DialogueContextUsageWireCodec;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.service.persistence.PetPersistenceException;
import com.google.gson.JsonArray;

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
    private static final System.Logger LOGGER=System.getLogger(JdbcDialogueStateStore.class.getName());
    private static final String LOCK_STATE = """
            SELECT t.curiosity, t.boldness, t.playfulness, t.expressiveness,
                   t.independence, t.attachment, t.trust, t.security,
                   t.relationship_summary, t.summary_version, t.updated_at,t.self_model_json,t.self_model_version,
                   m.content, m.excited, m.anxious, m.tired,
                   m.last_decay_at, m.updated_at AS mood_updated_at,
                   m.affect_valence,m.affect_valence_intensity,m.affect_alertness,
                   m.affect_alertness_intensity,m.affect_social_stance,m.affect_social_intensity,
                   m.affect_trigger,m.affect_target_uuid,m.affect_updated_at,m.affect_event_id
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
                prompt_eligible, consolidation_status, created_at,memory_kind,source_provenance,
                confidence,salience,emotional_significance,participants_json,location_json,metadata_json,
                memory_candidate_text,memory_candidate_confidence)
            VALUES (?, ?, ?, 'DIALOGUE', ?, ?, ?, ?, ?, ?, ?, ?, TRUE, 'PENDING', ?,
                'EPISODE','OWNER_STATEMENT',1.000,?,?,?, ?,?,?,?)
            """;
    private static final String INSERT_RELATIONSHIP_EVIDENCE = """
            INSERT INTO pet_relationship_evidence (
                interaction_id,event_id,pet_id,relationship_type,strength,occurred_at)
            VALUES (?,?,?,?,?,?)
            """;
    private static final String UPDATE_TRAITS = """
            UPDATE pet_traits SET curiosity=?, boldness=?, playfulness=?, expressiveness=?,
                independence=?, attachment=?, trust=?, security=?, updated_at=?
            WHERE pet_id=?
            """;
    private static final String UPDATE_MOOD = """
            UPDATE pet_mood SET content=?, excited=?, anxious=?, tired=?, last_decay_at=?, updated_at=?,
                affect_valence=?,affect_valence_intensity=?,affect_alertness=?,affect_alertness_intensity=?,
                affect_social_stance=?,affect_social_intensity=?,affect_trigger=?,affect_target_uuid=?,affect_updated_at=?,affect_event_id=?
                ,owner_intent_guess=CASE WHEN ? THEN NULL WHEN ? THEN ? ELSE owner_intent_guess END,
                owner_emotion_guess=CASE WHEN ? THEN NULL WHEN ? THEN ? ELSE owner_emotion_guess END,
                owner_interpretation_confidence=CASE WHEN ? THEN NULL WHEN ? THEN ? ELSE owner_interpretation_confidence END,
                owner_interpretation_event_id=CASE WHEN ? THEN NULL WHEN ? THEN ? ELSE owner_interpretation_event_id END,
                owner_interpretation_updated_at=CASE WHEN ? THEN NULL WHEN ? THEN ? ELSE owner_interpretation_updated_at END
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
        String operation = "connection";
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                operation = "lock_state";
                LockedState locked = lock(connection, request.expectedPet().petId());
                validateExpected(request, locked);
                operation = "daily_trait_budget";
                Map<TraitName, Integer> dailyUsed = dailyChanges(
                        connection, request.expectedPet().petId(), request.occurredAt());
                Applied applied = apply(request, locked, dailyUsed);
                operation = "insert_event"; insertEvent(connection, request);
                operation = "insert_relationship_evidence"; insertRelationshipEvidence(connection, request);
                operation = "record_owner_memory";
                if (request.output().ownerMemory().isPresent()) {
                    OwnerMemoryWriter.write(connection, request, request.output().ownerMemory().orElseThrow());
                }
                operation = "update_traits"; updateTraits(connection, request, applied.traits());
                operation = "update_mood"; updateMood(connection, request, applied.mood(), applied.affect());
                operation = "update_self_model_candidate"; updateSelfModelCandidate(connection, request, locked);
                operation = "insert_trait_audits"; insertAudits(connection, request, locked.traits(), applied);
                operation = "insert_usage"; insertUsage(connection, request.usage());
                operation = "commit"; connection.commit();
                return new DialoguePersistResult(
                        applied.traits(), applied.mood(), applied.traitDeltas(),
                        applied.moodDeltas(), request.output().importance()
                        .expiresAt(request.occurredAt()));
            } catch (SQLException | RuntimeException failure) {
                if (failure instanceof SQLException sqlFailure) {
                    LOGGER.log(System.Logger.Level.WARNING, "dialogue_persistence_operation_failed operation="
                            + operation + " sql_state=" + safeSqlState(sqlFailure.getSQLState())
                            + " vendor_code=" + sqlFailure.getErrorCode());
                }
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            LOGGER.log(System.Logger.Level.WARNING,"dialogue_persistence_sql_failure operation="+operation+" sql_state="
                    + safeSqlState(failure.getSQLState())+" vendor_code="+failure.getErrorCode());
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
                String target = rows.getString("affect_target_uuid");
                BehavioralAffect affect = new BehavioralAffect(
                        rows.getInt("affect_valence"), rows.getInt("affect_valence_intensity"),
                        rows.getInt("affect_alertness"), rows.getInt("affect_alertness_intensity"),
                        rows.getInt("affect_social_stance"), rows.getInt("affect_social_intensity"),
                        rows.getString("affect_trigger"), target == null ? null : UUID.fromString(target),
                        instant(rows, "affect_updated_at"), uuidOrNull(rows.getString("affect_event_id")));
                String selfModelJson = rows.getString("self_model_json");
                long selfModelVersion = rows.getLong("self_model_version");
                if (rows.next()) {
                    throw new SQLException("Pet trait/mood uniqueness invariant is violated");
                }
                return new LockedState(traits, mood, affect, selfModelJson, selfModelVersion);
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
        PetMood decayed = locked.mood().decay(request.expectedPet().appearance().species(),
                locked.traits(), request.occurredAt());
        int[] mood = java.util.Arrays.stream(MoodDimension.values())
                .mapToInt(decayed::value).toArray();
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
                mood[0], mood[1], mood[2], mood[3], decayed.lastDecayAt(),
                request.occurredAt());
        BehavioralAffect affect = locked.affect().apply(request.output().affectDelta(),
                request.output().affectStrength(), request.output().relationshipSignal(), updatedTraits,
                request.output().relationshipSignal().meaningful() ? .90
                        : request.output().affectDelta()==DialogueAffect.NONE ? 0 : .55,
                request.expectedPet().ownerUuid(), request.eventId(), request.occurredAt());
        return new Applied(updatedTraits, updatedMood, affect, appliedTraits, appliedMood);
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
            // The event summary represents the owner-authored side of the turn.
            // Keep the model's optional candidate separately with its lower
            // confidence so an assistant reply can never be mislabeled as an
            // owner statement or silently promoted into witnessed fact.
            statement.setString(8, request.normalizedPlayerMessage());
            statement.setString(9, request.normalizedPlayerMessage());
            statement.setString(10, request.output().reply());
            Optional<Instant> expiry = request.output().importance().expiresAt(request.occurredAt());
            if (expiry.isPresent()) {
                statement.setObject(11, utc(expiry.orElseThrow()));
            } else {
                statement.setNull(11, Types.TIMESTAMP);
            }
            statement.setObject(12, utc(request.occurredAt()));
            statement.setInt(13, salience(request.output().importance()));
            statement.setInt(14, request.output().relationshipSignal().meaningful() ? 2
                    : request.output().affectDelta() == DialogueAffect.NONE ? 0 : 1);
            com.google.gson.JsonObject participants = new com.google.gson.JsonObject();
            participants.addProperty("owner_uuid", request.expectedPet().ownerUuid().toString());
            participants.addProperty("pet_id", request.expectedPet().petId().toString());
            com.google.gson.JsonObject metadata = new com.google.gson.JsonObject();
            metadata.addProperty("action", request.output().action().name());
            metadata.addProperty("attention", request.output().attention().name());
            metadata.addProperty("affect", request.output().affectDelta().name());
            metadata.addProperty("affect_strength", request.output().affectStrength());
            metadata.addProperty("relationship", request.output().relationshipSignal().type().name());
            metadata.addProperty("conversation_mode", request.output().conversationMode().name());
            statement.setString(15, participants.toString());
            statement.setNull(16, Types.LONGVARCHAR); // Chat position is not the reported event's location.
            statement.setString(17, metadata.toString());
            if(request.output().memoryCandidate().isPresent() || request.output().ownerMemory().isPresent()) {
                statement.setString(18,request.output().ownerMemory().map(OwnerMemoryProposal::summary)
                        .orElseGet(() -> request.output().memoryCandidate().orElseThrow()));
                statement.setBigDecimal(19,new BigDecimal("0.500"));
            } else {
                statement.setNull(18,Types.VARCHAR); statement.setNull(19,Types.DECIMAL);
            }
            requireOne(statement.executeUpdate(), "dialogue event insert");
        }
    }

    private static int salience(DialogueImportance importance) {
        return switch (importance) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 4; };
    }

    private static void insertRelationshipEvidence(Connection connection,
            DialoguePersistRequest request) throws SQLException {
        var signal = request.output().relationshipSignal();
        if (!signal.meaningful()) return;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RELATIONSHIP_EVIDENCE)) {
            statement.setString(1, request.usage().requestId().toString());
            statement.setString(2, request.eventId().toString());
            statement.setString(3, request.expectedPet().petId().toString());
            statement.setString(4, signal.type().name());
            statement.setInt(5, signal.strength());
            statement.setObject(6, utc(request.occurredAt()));
            requireOne(statement.executeUpdate(), "relationship evidence insert");
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

    private static void updateMood(Connection connection, DialoguePersistRequest request,
            PetMood mood, BehavioralAffect affect) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_MOOD)) {
            int index = 1;
            for (MoodDimension dimension : MoodDimension.values()) {
                statement.setInt(index++, mood.value(dimension));
            }
            statement.setObject(index++, utc(mood.lastDecayAt()));
            statement.setObject(index++, utc(request.occurredAt()));
            statement.setInt(index++, affect.valence());
            statement.setInt(index++, affect.valenceIntensity());
            statement.setInt(index++, affect.alertness());
            statement.setInt(index++, affect.alertnessIntensity());
            statement.setInt(index++, affect.socialStance());
            statement.setInt(index++, affect.socialIntensity());
            statement.setString(index++, affect.trigger());
            if (affect.target() == null) statement.setNull(index++, Types.CHAR);
            else statement.setString(index++, affect.target().toString());
            statement.setObject(index++, utc(affect.updatedAt()));
            if (affect.eventId() == null) statement.setNull(index++, Types.CHAR);
            else statement.setString(index++, affect.eventId().toString());
            OwnerInterpretationCandidate interpretation=request.output().ownerInterpretation();
            boolean clearInterpretation=OwnerInterpretationMapper.isOwnerCorrection(request.normalizedPlayerMessage());
            boolean replaceInterpretation=interpretation.meaningful();
            statement.setBoolean(index++, clearInterpretation);
            statement.setBoolean(index++, replaceInterpretation);
            if (interpretation.intent().isPresent()) statement.setString(index++, interpretation.intent().orElseThrow());
            else statement.setNull(index++, Types.VARCHAR);
            statement.setBoolean(index++, clearInterpretation);
            statement.setBoolean(index++, replaceInterpretation);
            if (interpretation.emotion().isPresent()) statement.setString(index++, interpretation.emotion().orElseThrow());
            else statement.setNull(index++, Types.VARCHAR);
            statement.setBoolean(index++, clearInterpretation);
            statement.setBoolean(index++, replaceInterpretation);
            if (replaceInterpretation) statement.setBigDecimal(index++, BigDecimal.valueOf(interpretation.confidence()));
            else statement.setNull(index++, Types.DECIMAL);
            statement.setBoolean(index++, clearInterpretation);
            statement.setBoolean(index++, replaceInterpretation);
            if (replaceInterpretation) statement.setString(index++, request.eventId().toString());
            else statement.setNull(index++, Types.CHAR);
            statement.setBoolean(index++, clearInterpretation);
            statement.setBoolean(index++, replaceInterpretation);
            if (replaceInterpretation) statement.setObject(index++, utc(request.occurredAt()));
            else statement.setNull(index++, Types.TIMESTAMP);
            statement.setString(index, request.expectedPet().petId().toString());
            requireOne(statement.executeUpdate(), "mood update");
        }
    }

    private static void updateSelfModelCandidate(Connection connection,
            DialoguePersistRequest request, LockedState locked) throws SQLException {
        DialogueSelfModelCandidate candidate = request.output().selfModelCandidate();
        if (!candidate.meaningful()) return;
        String next = PetSelfModel.recordCandidate(locked.selfModelJson(), candidate,
                request.eventId(), request.occurredAt(), false);
        if (Objects.equals(locked.selfModelJson(), next)) return;
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE pet_traits SET self_model_json=?,self_model_version=self_model_version+1,
                    self_model_updated_at=?,updated_at=GREATEST(updated_at,?)
                WHERE pet_id=? AND self_model_version=?
                """)) {
            update.setString(1, next); update.setObject(2, utc(request.occurredAt()));
            update.setObject(3, utc(request.occurredAt()));
            update.setString(4, request.expectedPet().petId().toString());
            update.setLong(5, locked.selfModelVersion());
            requireOne(update.executeUpdate(), "versioned self model candidate update");
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

    private static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
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

    private static String safeSqlState(String state) {
        if(state==null)return "UNKNOWN";
        String safe=state.replaceAll("[^A-Za-z0-9]","_");
        return safe.isBlank()?"UNKNOWN":safe.substring(0,Math.min(16,safe.length()));
    }

    private static void requireOne(int rows, String operation) throws SQLException {
        if (rows != 1) throw new SQLException(operation + " did not affect exactly one row");
    }

    private record LockedState(PetTraits traits, PetMood mood, BehavioralAffect affect,
            String selfModelJson, long selfModelVersion) {
    }

    private record Applied(
            PetTraits traits,
            PetMood mood,
            BehavioralAffect affect,
            Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas) {
    }
}
