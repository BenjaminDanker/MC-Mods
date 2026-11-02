package com.silver.aipets.service.consolidation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitCategory;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.service.dialogue.DialogueImportance;
import com.silver.aipets.service.dialogue.DialogueUsage;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.dialogue.PetSelfModel;
import com.silver.aipets.service.memory.MemoryProvenance;
import com.silver.aipets.service.memory.MemoryFactKey;
import com.silver.aipets.service.persistence.PetPersistenceException;
import com.silver.aipets.service.persistence.PetRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** MariaDB V001 adapter with one atomic card/trait/audit/event/job commit. */
public final class JdbcConsolidationRepository implements ConsolidationRepository {
    private static final String JOB_TYPE = "SLEEP_CONSOLIDATION";
    private static final Set<String> PAYLOAD_FIELDS = Set.of("sleepCycleId");
    private static final String JOB_COLUMNS = """
            job_id, pet_id, idempotency_key, payload_json, status, attempt_count,
            not_before, locked_by, locked_until, last_error_sanitized
            """;
    private static final String INSERT_JOB = """
            INSERT INTO jobs (job_id, job_type, pet_id, idempotency_key, payload_json,
                status, attempt_count, not_before, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            """;
    private static final String FIND_KEY = "SELECT " + JOB_COLUMNS + """
             FROM jobs WHERE job_type=? AND idempotency_key=?
            """;
    private static final String CLAIM_DUE = "SELECT " + JOB_COLUMNS + """
             FROM jobs WHERE job_type=? AND (
                (status IN ('PENDING','RETRY') AND not_before<=?) OR
                (status='RUNNING' AND locked_until<=?))
             ORDER BY not_before, job_id LIMIT ? FOR UPDATE
            """;
    private static final String CLAIM_DUE_PET = "SELECT " + JOB_COLUMNS + """
             FROM jobs WHERE job_type=? AND pet_id=? AND (
                (status IN ('PENDING','RETRY') AND not_before<=?) OR
                (status='RUNNING' AND locked_until<=?))
             ORDER BY not_before, job_id LIMIT ? FOR UPDATE
            """;
    private static final String CLAIM = """
            UPDATE jobs SET status='RUNNING', attempt_count=attempt_count+1,
                locked_by=?, locked_until=?, updated_at=?, completed_at=NULL
            WHERE job_id=?
            """;
    private static final String PENDING_EVENTS = """
            SELECT e.event_id, e.pet_id, e.importance, COALESCE(e.memory_candidate_text,e.summary) AS summary,
                   e.event_type, e.occurred_at, e.raw_player_text, e.source_server, e.source_dimension,
                   e.source_provenance,e.memory_candidate_text,e.memory_candidate_confidence,e.location_json,
                   r.interaction_id, r.relationship_type, r.strength
            FROM pet_events e LEFT JOIN pet_relationship_evidence r
              ON r.event_id=e.event_id AND r.pet_id=e.pet_id
            WHERE e.pet_id=? AND e.consolidation_status='PENDING'
            ORDER BY e.occurred_at, e.event_id
            """;
    private static final String LOCK_JOB = """
            SELECT status, locked_by, attempt_count FROM jobs
            WHERE job_id=? AND job_type=? FOR UPDATE
            """;
    private static final String LOCK_TRAITS = """
            SELECT curiosity,boldness,playfulness,expressiveness,independence,
                attachment,trust,security,relationship_summary,summary_version,updated_at
            FROM pet_traits WHERE pet_id=? FOR UPDATE
            """;
    private static final String LOCK_EVENT = """
            SELECT consolidation_status FROM pet_events
            WHERE event_id=? AND pet_id=? FOR UPDATE
            """;
    private static final String DAILY_CHANGES = """
            SELECT trait_name,COALESCE(SUM(ABS(applied_delta)),0) used
            FROM trait_change_audit WHERE pet_id=? AND created_at>=? AND created_at<?
            GROUP BY trait_name
            """;
    private static final String INSERT_MEMORY = """
            INSERT INTO long_term_memories (memory_id,pet_id,memory_text,memory_version,
                importance,emotion_tags,entity_tags,location_tags,provenance,fact_key,
                memory_kind,confidence,salience,emotional_significance,
                embedding_status,active,
                created_at,updated_at)
            VALUES (?,?,?,1,?,?,?,?,?,?,?,?,?,?,'PENDING',TRUE,?,?)
            """;
    private static final String INSERT_SOURCE = """
            INSERT INTO long_term_memory_source_events
                (memory_id,pet_id,source_event_id,created_at) VALUES (?,?,?,?)
            """;
    private static final String UPDATE_TRAITS = """
            UPDATE pet_traits SET curiosity=?,boldness=?,playfulness=?,expressiveness=?,
                independence=?,attachment=?,trust=?,security=?,relationship_summary=?,
                summary_version=?,updated_at=? WHERE pet_id=?
            """;
    private static final String INSERT_AUDIT = """
            INSERT INTO trait_change_audit (change_id,pet_id,event_id,job_id,trait_name,
                old_value,proposed_delta,applied_delta,new_value,reason,created_at)
            VALUES (?,?,NULL,?,?,?,?,?,?,'validated sleep consolidation proposal',?)
            """;
    private static final String UPDATE_EVENT = """
            UPDATE pet_events SET consolidation_status=?, consolidation_job_id=?,
                prompt_eligible=FALSE WHERE event_id=? AND pet_id=? AND consolidation_status='PENDING'
            """;
    private static final String SUCCEEDED = """
            UPDATE jobs SET status='SUCCEEDED',locked_by=NULL,locked_until=NULL,
                last_error_sanitized=NULL,updated_at=?,completed_at=?
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String RETRY = """
            UPDATE jobs SET status='RETRY',not_before=?,locked_by=NULL,locked_until=NULL,
                last_error_sanitized=?,updated_at=?,completed_at=NULL
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String DEFER = """
            UPDATE jobs SET status='RETRY', attempt_count=GREATEST(0,attempt_count-1),
                not_before=?,locked_by=NULL,locked_until=NULL,
                last_error_sanitized=?,updated_at=?,completed_at=NULL
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String FAILED = """
            UPDATE jobs SET status='FAILED',locked_by=NULL,locked_until=NULL,
                last_error_sanitized=?,updated_at=?,completed_at=?
            WHERE job_id=? AND status='RUNNING' AND locked_by=?
            """;
    private static final String INSERT_USAGE = """
            INSERT INTO ai_usage (call_id,pet_id,owner_uuid,operation,model,request_id,
                provider_id,input_tokens,cached_input_tokens,output_tokens,estimated_cost,
                latency_ms,status,error_category,created_at)
            VALUES (?,?,?,'CONSOLIDATION',?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final DataSource dataSource;
    private final PetRepository pets;
    private final Supplier<UUID> memoryIds;
    private final Supplier<UUID> auditIds;

    public JdbcConsolidationRepository(
            DataSource dataSource,
            PetRepository pets,
            Supplier<UUID> memoryIds,
            Supplier<UUID> auditIds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.memoryIds = Objects.requireNonNull(memoryIds, "memoryIds");
        this.auditIds = Objects.requireNonNull(auditIds, "auditIds");
    }

    @Override
    public EnqueueResult enqueue(ConsolidationJob proposed) {
        Objects.requireNonNull(proposed, "proposed");
        if (proposed.status() != ConsolidationJobStatus.PENDING || proposed.attemptCount() != 0) {
            throw new IllegalArgumentException("Only a fresh pending consolidation job can be enqueued");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_JOB)) {
            statement.setString(1, proposed.jobId().toString());
            statement.setString(2, JOB_TYPE);
            statement.setString(3, proposed.petId().toString());
            statement.setString(4, proposed.idempotencyKey());
            statement.setString(5, payload(proposed));
            statement.setObject(6, utc(proposed.notBefore()));
            statement.setObject(7, utc(proposed.notBefore()));
            statement.setObject(8, utc(proposed.notBefore()));
            requireOne(statement.executeUpdate(), "consolidation job insert");
            return new EnqueueResult(proposed, true);
        } catch (SQLException failure) {
            if (!constraint(failure)) throw persistence("Could not enqueue consolidation", failure);
            ConsolidationJob existing = findByKey(proposed.idempotencyKey()).orElseThrow(() ->
                    persistence("Consolidation enqueue collided without an idempotency row", failure));
            if (!existing.petId().equals(proposed.petId())
                    || !existing.sleepCycleId().equals(proposed.sleepCycleId())) {
                throw new IllegalStateException("Consolidation idempotency content conflicts");
            }
            return new EnqueueResult(existing, false);
        }
    }

    @Override
    public List<ConsolidationJob> claimDue(
            String workerId, Instant now, Duration lease, int limit) {
        return claimDue(Optional.empty(), workerId, now, lease, limit);
    }

    @Override
    public List<ConsolidationJob> claimDueForPet(
            UUID petId, String workerId, Instant now, Duration lease, int limit) {
        return claimDue(Optional.of(Objects.requireNonNull(petId, "petId")), workerId, now, lease, limit);
    }

    private List<ConsolidationJob> claimDue(
            Optional<UUID> petId, String workerId, Instant now, Duration lease, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (workerId.isBlank() || workerId.length() > 191 || lease.isZero()
                || lease.isNegative() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid consolidation claim bounds");
        }
        return transaction("claim consolidation jobs", connection -> {
            List<ConsolidationJob> due = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    petId.isPresent() ? CLAIM_DUE_PET : CLAIM_DUE)) {
                int index = 1;
                statement.setString(index++, JOB_TYPE);
                if (petId.isPresent()) statement.setString(index++, petId.orElseThrow().toString());
                statement.setObject(index++, utc(now));
                statement.setObject(index++, utc(now));
                statement.setInt(index, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) due.add(mapJob(rows));
                }
            }
            List<ConsolidationJob> result = new ArrayList<>();
            for (ConsolidationJob job : due) {
                Instant until = now.plus(lease);
                try (PreparedStatement statement = connection.prepareStatement(CLAIM)) {
                    statement.setString(1, workerId);
                    statement.setObject(2, utc(until));
                    statement.setObject(3, utc(now));
                    statement.setString(4, job.jobId().toString());
                    requireOne(statement.executeUpdate(), "consolidation job claim");
                }
                result.add(new ConsolidationJob(
                        job.jobId(),job.petId(),job.sleepCycleId(),job.idempotencyKey(),
                        ConsolidationJobStatus.RUNNING,job.attemptCount()+1,job.notBefore(),
                        Optional.of(workerId),Optional.of(until),job.lastErrorCategory()));
            }
            return List.copyOf(result);
        });
    }

    @Override public Optional<Pet> findPet(UUID petId) { return pets.findById(petId); }

    @Override
    public List<ConsolidationEvent> pendingEvents(UUID petId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_EVENTS)) {
            statement.setString(1, petId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<ConsolidationEvent> result = new ArrayList<>();
                while (rows.next()) {
                    String ownerText = rows.getString("raw_player_text");
                    String candidate = rows.getString("memory_candidate_text");
                    String summary = "DIALOGUE".equals(rows.getString("event_type")) && ownerText != null
                            ? "Owner said: " + ownerText.substring(0, Math.min(1250, ownerText.length()))
                                    + (candidate == null ? "" : " Tentative, unverified model memory candidate (confidence "
                                            + rows.getBigDecimal("memory_candidate_confidence") + "): " + candidate)
                            : rows.getString("summary");
                    if (rows.getString("relationship_type") != null) {
                        summary += " Relationship evidence type=" + rows.getString("relationship_type")
                                + " strength=" + rows.getInt("strength")
                                + " interaction_id=" + rows.getString("interaction_id") + ".";
                    }
                    result.add(new ConsolidationEvent(
                            UUID.fromString(rows.getString("event_id")),
                            UUID.fromString(rows.getString("pet_id")),
                            DialogueImportance.valueOf(rows.getString("importance")),
                            summary,
                            rows.getString("event_type"), instant(rows,"occurred_at"),
                            rows.getBigDecimal("memory_candidate_confidence") != null
                                    ? MemoryProvenance.INFERENCE
                                    : MemoryProvenance.valueOf(rows.getString("source_provenance")),
                            rows.getString("source_server") == null ? ""
                                    : rows.getString("source_server"),
                            rows.getString("source_dimension") == null ? ""
                                    : rows.getString("source_dimension"),
                            com.silver.aipets.common.transport.SpatialLocationCodec.readStored(rows.getString("location_json"))));
                }
                return List.copyOf(result);
            }
        } catch (SQLException failure) {
            throw persistence("Could not read consolidation events", failure);
        }
    }

    @Override
    public void completeWithoutModel(
            ConsolidationJob job, String workerId, List<UUID> discardedIds, Instant at) {
        transaction("complete skipped consolidation", connection -> {
            requireJobLock(connection, job, workerId);
            for (UUID id : discardedIds) {
                lockPendingEvent(connection, id, job.petId());
                updateEvent(connection, id, job.petId(), job.jobId(), "DISCARDED");
            }
            decayUnusedInferenceMemories(connection,job.petId(),at);
            forgetExpiredThreads(connection,job.petId(),at);
            finishSuccess(connection, job.jobId(), workerId, at);
            return null;
        });
    }

    @Override
    public ConsolidationCommitResult commit(
            ConsolidationJob job,String workerId,CandidateSelection selection,
            ValidatedConsolidationOutput output,DialogueUsage usage,Instant at) {
        return transaction("commit consolidation", connection -> {
            requireJobLock(connection, job, workerId);
            if (!usage.petId().equals(job.petId())
                    || usage.status()!=DialogueUsage.Status.SUCCEEDED) {
                throw new SQLException("Invalid successful consolidation usage");
            }
            PetTraits current = lockTraits(connection, job.petId());
            for (ConsolidationEvent event : selection.selected()) {
                lockPendingEvent(connection,event.eventId(),job.petId());
            }
            for (UUID id : selection.discardedEventIds()) lockPendingEvent(connection,id,job.petId());
            Applied applied = apply(current,output,job.petId(),at,dailyChanges(connection,job.petId(),at),
                    isYoung(connection,job.petId(),at));
            List<UUID> retired = new ArrayList<>();
            List<LongTermMemoryCard> cards = insertMemories(
                    connection,job,selection,output,at,retired);
            promoteSelfModelCandidates(connection, job.petId(), at);
            updateTraits(connection,job.petId(),applied.traits(),at);
            decayUnusedInferenceMemories(connection,job.petId(),at);
            forgetExpiredThreads(connection,job.petId(),at);
            insertAudits(connection,job,current,output,applied,at);
            insertUsage(connection,usage);
            for (ConsolidationEvent event : selection.selected()) {
                updateEvent(connection,event.eventId(),job.petId(),job.jobId(),"CONSOLIDATED");
            }
            for (UUID id : selection.discardedEventIds()) {
                updateEvent(connection,id,job.petId(),job.jobId(),"DISCARDED");
            }
            finishSuccess(connection,job.jobId(),workerId,at);
            return new ConsolidationCommitResult(
                    cards,applied.traits(),selection.selected().size(),
                    selection.discardedEventIds().size(),retired);
        });
    }

    private static void forgetExpiredThreads(Connection connection,UUID petId,Instant at) throws SQLException {
        try(PreparedStatement pause=connection.prepareStatement("""
              UPDATE pet_open_threads SET state='PAUSED',updated_at=?,version=version+1
                WHERE pet_id=? AND state='ACTIVE' AND expires_at IS NOT NULL AND expires_at<=?
                """)) {
            pause.setObject(1,utc(at));pause.setString(2,petId.toString());pause.setObject(3,utc(at));pause.executeUpdate();
        }
        try(PreparedStatement forget=connection.prepareStatement("""
              UPDATE pet_open_threads SET state='FORGOTTEN',last_referenced_at=?,updated_at=?,version=version+1
                WHERE pet_id=? AND state IN ('OPEN','PAUSED') AND expires_at IS NOT NULL AND expires_at<=?
                """)) {
            forget.setObject(1,utc(at));forget.setObject(2,utc(at));forget.setString(3,petId.toString());forget.setObject(4,utc(at));forget.executeUpdate();
        }
    }

    private static void decayUnusedInferenceMemories(Connection connection,UUID petId,Instant at) throws SQLException {
        try(PreparedStatement q=connection.prepareStatement("""
                UPDATE long_term_memories SET salience=GREATEST(0,salience-1),
                    active=CASE WHEN salience<=1 THEN FALSE ELSE active END,updated_at=?
                WHERE pet_id=? AND active=TRUE AND provenance IN ('INFERENCE','CONSOLIDATED')
                  AND importance='MEDIUM' AND last_recalled_at IS NULL
                  AND created_at<=? AND salience>0
                """)) {
            q.setObject(1,utc(at));q.setString(2,petId.toString());
            q.setObject(3,utc(at.minus(java.time.Duration.ofDays(30))));q.executeUpdate();
        }
    }

    @Override
    public void recordUsage(DialogueUsage usage) {
        Objects.requireNonNull(usage,"usage");
        try (Connection connection=dataSource.getConnection()) {
            insertUsage(connection,usage);
        } catch (SQLException failure) {
            if (!constraint(failure)) throw persistence("Could not record consolidation usage",failure);
        }
    }

    @Override
    public void retry(
            ConsolidationJob job,String workerId,Instant attemptedAt,
            Instant notBefore,String errorCategory) {
        executeLocked(RETRY, statement -> {
            statement.setObject(1,utc(notBefore)); statement.setString(2,bounded(errorCategory));
            statement.setObject(3,utc(attemptedAt)); statement.setString(4,job.jobId().toString());
            statement.setString(5,workerId);
        }, "retry consolidation");
    }

    @Override
    public void failed(
            ConsolidationJob job,String workerId,Instant at,String errorCategory) {
        executeLocked(FAILED, statement -> {
            statement.setString(1,bounded(errorCategory)); statement.setObject(2,utc(at));
            statement.setObject(3,utc(at)); statement.setString(4,job.jobId().toString());
            statement.setString(5,workerId);
        }, "fail consolidation");
    }

    @Override
    public Optional<ConsolidationJob> findByKey(String key) {
        try (Connection connection=dataSource.getConnection();
             PreparedStatement statement=connection.prepareStatement(FIND_KEY)) {
            statement.setString(1,JOB_TYPE); statement.setString(2,key);
            try (ResultSet rows=statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                ConsolidationJob result=mapJob(rows);
                if (rows.next()) throw new SQLException("Consolidation key is not unique");
                return Optional.of(result);
            }
        } catch (SQLException failure) {
            throw persistence("Could not read consolidation job",failure);
        }
    }

    private List<LongTermMemoryCard> insertMemories(
            Connection connection,ConsolidationJob job,CandidateSelection selection,
            ValidatedConsolidationOutput output,Instant at,List<UUID> retired) throws SQLException {
        Set<UUID> selected=selection.selected().stream().map(ConsolidationEvent::eventId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<LongTermMemoryCard> result=new ArrayList<>();
        Map<UUID, ConsolidationEvent> eventById=selection.selected().stream()
                .collect(java.util.stream.Collectors.toMap(ConsolidationEvent::eventId,
                        java.util.function.Function.identity()));
        for (ConsolidationCardProposal proposal:output.memoryCards()) {
            if (!selected.containsAll(proposal.sourceEventIds())) {
                throw new SQLException("Memory source is not selected for this pet/job");
            }
            UUID memoryId=Objects.requireNonNull(memoryIds.get(),"memoryIds returned null");
            MemoryProvenance provenance = proposal.sourceEventIds().size() > 1
                    ? MemoryProvenance.INFERENCE : proposal.sourceEventIds().stream()
                    .map(id -> eventById.get(id).provenance()).distinct().count() == 1
                    ? eventById.get(proposal.sourceEventIds().getFirst()).provenance()
                    : MemoryProvenance.INFERENCE;
            String factKey = provenance == MemoryProvenance.OWNER_STATEMENT
                    ? MemoryFactKey.fromOwnerStatement(proposal.text()).orElse(null) : null;
            if (factKey != null) {
                Instant latestSource = proposal.sourceEventIds().stream()
                        .map(id -> eventById.get(id).occurredAt())
                        .max(Instant::compareTo).orElseThrow();
                // An explicit owner correction may have been committed since this sleep job
                // selected its events. Never resurrect an older conflicting preference.
                if (hasAtLeastAsRecentFact(connection, job.petId(), factKey, latestSource))
                    continue;
            }
            if (factKey != null) retireFact(connection, job.petId(), factKey, at, retired);
            double confidence = provenance == MemoryProvenance.OWNER_STATEMENT ? 1.0
                    : provenance == MemoryProvenance.VERIFIED_OBSERVATION ? .9
                    : Math.min(.9, .5 + proposal.sourceEventIds().size() * .08);
            LongTermMemoryCard card=new LongTermMemoryCard(
                    memoryId,job.petId(),1,proposal.text(),proposal.importance(),
                    proposal.emotionTags(),proposal.entityTags(),
                    ConsolidationLocationTags.from(proposal, eventById),true,
                    at,provenance,factKey!=null?com.silver.aipets.service.memory.MemoryKind.PREFERENCE
                            :com.silver.aipets.service.memory.MemoryKind.SUMMARY,
                    confidence,cardSalience(proposal.importance()),proposal.emotionTags().isEmpty()?0:2,
                    java.util.Optional.empty());
            try (PreparedStatement statement=connection.prepareStatement(INSERT_MEMORY)) {
                statement.setString(1,memoryId.toString()); statement.setString(2,job.petId().toString());
                statement.setString(3,card.text()); statement.setString(4,card.importance().name());
                statement.setString(5,tags(card.emotionTags())); statement.setString(6,tags(card.entityTags()));
                statement.setString(7,tags(card.locationTags()));
                statement.setString(8,provenance.name()); statement.setString(9,factKey);
                statement.setString(10,factKey != null ? "PREFERENCE" : "SUMMARY");
                statement.setBigDecimal(11, java.math.BigDecimal.valueOf(confidence));
                statement.setInt(12, card.importance() == MemoryImportance.HIGH ? 4 : 2);
                statement.setInt(13, proposal.emotionTags().isEmpty() ? 0 : 2);
                statement.setObject(14,utc(at));
                statement.setObject(15,utc(at)); requireOne(statement.executeUpdate(),"memory insert");
            }
            for (UUID source:proposal.sourceEventIds()) {
                try (PreparedStatement statement=connection.prepareStatement(INSERT_SOURCE)) {
                    statement.setString(1,memoryId.toString()); statement.setString(2,job.petId().toString());
                    statement.setString(3,source.toString()); statement.setObject(4,utc(at));
                    requireOne(statement.executeUpdate(),"memory source insert");
                }
            }
            if (provenance != MemoryProvenance.OWNER_STATEMENT) {
                SelfModelSupport support = witnessedSelfModelSupport(
                        connection, job.petId(), proposal.sourceEventIds());
                PetSelfModel.fromConsolidatedEvidence(card.text(), support.events(), support.days())
                        .ifPresent(update -> {
                            try { applySelfModelEvidence(connection, job.petId(), update, at); }
                            catch (SQLException failure) { throw new IllegalStateException(failure); }
                        });
            }
            result.add(card);
        }
        return List.copyOf(result);
    }

    private static int cardSalience(MemoryImportance importance) {
        return importance==MemoryImportance.HIGH?4:2;
    }

    private static SelfModelSupport witnessedSelfModelSupport(Connection connection, UUID petId,
            List<UUID> sourceEventIds) throws SQLException {
        if (sourceEventIds.isEmpty()) return new SelfModelSupport(0, 0);
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceEventIds.size(), "?"));
        String sql = "SELECT COUNT(DISTINCT e.event_id),COUNT(DISTINCT DATE(e.occurred_at)) "
                + "FROM pet_events e WHERE e.pet_id=? AND e.source_provenance='VERIFIED_OBSERVATION' "
                + "AND e.event_id IN (" + placeholders + ")";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, petId.toString());
            for (int i = 0; i < sourceEventIds.size(); i++) query.setString(i + 2, sourceEventIds.get(i).toString());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return new SelfModelSupport(0, 0);
                return new SelfModelSupport(row.getInt(1), row.getInt(2));
            }
        }
    }

    private record SelfModelSupport(int events, int days) { }

    private static void applySelfModelEvidence(Connection c, UUID petId,
            PetSelfModel.Update evidence, Instant at) throws SQLException {
        String prior;
        try (PreparedStatement q=c.prepareStatement(
                "SELECT self_model_json FROM pet_traits WHERE pet_id=? FOR UPDATE")) {
            q.setString(1,petId.toString());
            try(ResultSet r=q.executeQuery()) { if(!r.next()) throw new SQLException("Pet self model missing"); prior=r.getString(1); }
        }
        String next=PetSelfModel.add(prior,evidence,at);
        if (java.util.Objects.equals(prior,next)) return;
        try(PreparedStatement q=c.prepareStatement("""
                UPDATE pet_traits SET self_model_json=?,self_model_version=self_model_version+1,
                    self_model_updated_at=?,updated_at=GREATEST(updated_at,?) WHERE pet_id=?
                """)) {
            q.setString(1,next); q.setObject(2,utc(at)); q.setObject(3,utc(at)); q.setString(4,petId.toString());
            requireOne(q.executeUpdate(),"self model update");
        }
    }

    private static void promoteSelfModelCandidates(Connection c, UUID petId, Instant at) throws SQLException {
        String prior; long version;
        try (PreparedStatement query = c.prepareStatement(
                "SELECT self_model_json,self_model_version FROM pet_traits WHERE pet_id=? FOR UPDATE")) {
            query.setString(1, petId.toString());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new SQLException("Pet self model missing");
                prior = row.getString(1); version = row.getLong(2);
            }
        }
        String next = PetSelfModel.settle(prior, at);
        if (Objects.equals(prior, next)) return;
        try (PreparedStatement update = c.prepareStatement("""
                UPDATE pet_traits SET self_model_json=?,self_model_version=self_model_version+1,
                    self_model_updated_at=?,updated_at=GREATEST(updated_at,?)
                WHERE pet_id=? AND self_model_version=?
                """)) {
            update.setString(1, next); update.setObject(2, utc(at)); update.setObject(3, utc(at));
            update.setString(4, petId.toString()); update.setLong(5, version);
            requireOne(update.executeUpdate(), "versioned sleep self-model promotion");
        }
    }

    private static boolean hasAtLeastAsRecentFact(Connection connection, UUID petId,
            String key, Instant sourceAt) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement("""
                SELECT created_at FROM long_term_memories
                WHERE pet_id=? AND fact_key=? AND active=TRUE
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """)) {
            q.setString(1, petId.toString());
            q.setString(2, key);
            try (ResultSet row = q.executeQuery()) {
                return row.next() && !row.getObject(1, LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC).isBefore(sourceAt);
            }
        }
    }

    @Override
    public void defer(ConsolidationJob job,String workerId,Instant attemptedAt,
            Instant notBefore,String reason) {
        executeLocked(DEFER, statement -> {
            statement.setObject(1,utc(notBefore)); statement.setString(2,bounded(reason));
            statement.setObject(3,utc(attemptedAt)); statement.setString(4,job.jobId().toString());
            statement.setString(5,workerId);
        }, "defer consolidation");
    }

    private static void retireFact(Connection connection, UUID petId, String key,
            Instant at, List<UUID> retired) throws SQLException {
        try (PreparedStatement find = connection.prepareStatement("""
                SELECT memory_id FROM long_term_memories
                WHERE pet_id=? AND fact_key=? AND active=TRUE FOR UPDATE
                """)) {
            find.setString(1,petId.toString()); find.setString(2,key);
            try (ResultSet rows=find.executeQuery()) {
                while (rows.next()) retired.add(UUID.fromString(rows.getString(1)));
            }
        }
        try (PreparedStatement update=connection.prepareStatement("""
                UPDATE long_term_memories SET active=FALSE,updated_at=?
                WHERE pet_id=? AND fact_key=? AND active=TRUE
                """)) {
            update.setObject(1,utc(at)); update.setString(2,petId.toString());
            update.setString(3,key); update.executeUpdate();
        }
    }

    private static PetTraits lockTraits(Connection connection,UUID petId) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(LOCK_TRAITS)) {
            statement.setString(1,petId.toString());
            try (ResultSet rows=statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Pet traits missing");
                return new PetTraits(
                        rows.getInt("curiosity"),rows.getInt("boldness"),rows.getInt("playfulness"),
                        rows.getInt("expressiveness"),rows.getInt("independence"),rows.getInt("attachment"),
                        rows.getInt("trust"),rows.getInt("security"),rows.getString("relationship_summary"),
                        rows.getLong("summary_version"),instant(rows,"updated_at"));
            }
        }
    }

    private static Map<TraitName,Integer> dailyChanges(
            Connection connection,UUID petId,Instant at) throws SQLException {
        LocalDate day=at.atZone(ZoneOffset.UTC).toLocalDate();
        Map<TraitName,Integer> result=new EnumMap<>(TraitName.class);
        try (PreparedStatement statement=connection.prepareStatement(DAILY_CHANGES)) {
            statement.setString(1,petId.toString());
            statement.setObject(2,utc(day.atStartOfDay(ZoneOffset.UTC).toInstant()));
            statement.setObject(3,utc(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            try (ResultSet rows=statement.executeQuery()) {
                while (rows.next()) result.put(TraitName.valueOf(
                        rows.getString("trait_name").toUpperCase(Locale.ROOT)),rows.getInt("used"));
            }
        }
        return result;
    }

    private static Applied apply(
            PetTraits current,ValidatedConsolidationOutput output,UUID petId,
            Instant at,Map<TraitName,Integer> daily,boolean young) {
        int[] values=java.util.Arrays.stream(TraitName.values()).mapToInt(current::value).toArray();
        Map<TraitName,Integer> deltas=new EnumMap<>(TraitName.class);
        for (TraitName trait:TraitName.values()) {
            int bounded=boundedDelta(trait,output.proposedTraitDeltas().get(trait),daily.getOrDefault(trait,0),young);
            int old=values[trait.ordinal()]; int next=clamp(old+bounded,0,100);
            values[trait.ordinal()]=next; deltas.put(trait,next-old);
        }
        return new Applied(new PetTraits(
                values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],
                output.relationshipSummary(),current.summaryVersion()+1,at),deltas);
    }

    /**
     * One sleep's change to a trait. Temperament drifts slowly. Trust and security are built slowly
     * and lost quickly (one point up, three down); the daily total still bounds both.
     */
    static int boundedDelta(TraitName trait,int proposed,int dailyUsed) {
        return boundedDelta(trait,proposed,dailyUsed,false);
    }

    /**
     * A pet in its first week is still imprinting: how much it trusts, feels safe and attaches
     * moves up faster than it will later. Temperament never speeds up.
     */
    static int boundedDelta(TraitName trait,int proposed,int dailyUsed,boolean young) {
        boolean temperament=trait.category()==TraitCategory.TEMPERAMENT;
        boolean asymmetric=trait==TraitName.TRUST||trait==TraitName.SECURITY;
        int imprint=young&&!temperament?1:0;
        int up=(temperament||asymmetric?1:2)+imprint;
        int down=temperament?1:asymmetric?3:2;
        int remaining=Math.max(0,(temperament?3:young?10:6)-dailyUsed);
        return clamp(clamp(proposed,-down,up),-remaining,remaining);
    }

    private static final java.time.Duration IMPRINTING=java.time.Duration.ofDays(7);

    private static boolean isYoung(Connection c,UUID petId,Instant at) throws SQLException {
        try (PreparedStatement q=c.prepareStatement("SELECT created_at FROM pets WHERE pet_id=?")) {
            q.setString(1,petId.toString());
            try (ResultSet r=q.executeQuery()) {
                return r.next() && java.time.Duration.between(
                        r.getObject(1,java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),at)
                        .compareTo(IMPRINTING)<0;
            }
        }
    }

    private static void updateTraits(
            Connection connection,UUID petId,PetTraits traits,Instant at) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(UPDATE_TRAITS)) {
            int index=1; for (TraitName trait:TraitName.values()) statement.setInt(index++,traits.value(trait));
            statement.setString(index++,traits.relationshipSummary()); statement.setLong(index++,traits.summaryVersion());
            statement.setObject(index++,utc(at)); statement.setString(index,petId.toString());
            requireOne(statement.executeUpdate(),"consolidated trait update");
        }
    }

    private void insertAudits(
            Connection connection,ConsolidationJob job,PetTraits old,
            ValidatedConsolidationOutput output,Applied applied,Instant at) throws SQLException {
        for (TraitName trait:TraitName.values()) {
            int delta=applied.deltas().get(trait); if (delta==0) continue;
            try (PreparedStatement statement=connection.prepareStatement(INSERT_AUDIT)) {
                statement.setString(1,Objects.requireNonNull(auditIds.get(),"auditIds returned null").toString());
                statement.setString(2,job.petId().toString()); statement.setString(3,job.jobId().toString());
                statement.setString(4,trait.name().toLowerCase(Locale.ROOT)); statement.setInt(5,old.value(trait));
                statement.setInt(6,output.proposedTraitDeltas().get(trait)); statement.setInt(7,delta);
                statement.setInt(8,applied.traits().value(trait)); statement.setObject(9,utc(at));
                requireOne(statement.executeUpdate(),"consolidation trait audit insert");
            }
        }
    }

    private static void insertUsage(Connection connection,DialogueUsage usage) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(INSERT_USAGE)) {
            statement.setString(1,usage.callId().toString());
            statement.setString(2,usage.petId().toString());
            statement.setString(3,usage.ownerUuid().toString());
            statement.setString(4,usage.model());
            statement.setString(5,usage.requestId().toString());
            if (usage.providerId().isPresent()) statement.setString(6,usage.providerId().orElseThrow());
            else statement.setNull(6,Types.VARCHAR);
            statement.setInt(7,usage.inputTokens()); statement.setInt(8,usage.cachedInputTokens());
            statement.setInt(9,usage.outputTokens()); statement.setBigDecimal(10,usage.estimatedCost());
            statement.setLong(11,usage.latencyMillis()); statement.setString(12,usage.status().name());
            if (usage.errorCategory().isPresent()) statement.setString(13,usage.errorCategory().orElseThrow());
            else statement.setNull(13,Types.VARCHAR);
            statement.setObject(14,utc(usage.createdAt()));
            requireOne(statement.executeUpdate(),"consolidation usage insert");
        }
    }

    private static void lockPendingEvent(
            Connection connection,UUID eventId,UUID petId) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(LOCK_EVENT)) {
            statement.setString(1,eventId.toString()); statement.setString(2,petId.toString());
            try (ResultSet rows=statement.executeQuery()) {
                if (!rows.next() || !"PENDING".equals(rows.getString("consolidation_status"))) {
                    throw new SQLException("Event missing, cross-pet, or already consolidated");
                }
            }
        }
    }

    private static void updateEvent(
            Connection connection,UUID eventId,UUID petId,UUID jobId,String status) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(UPDATE_EVENT)) {
            statement.setString(1,status); statement.setString(2,jobId.toString());
            statement.setString(3,eventId.toString()); statement.setString(4,petId.toString());
            requireOne(statement.executeUpdate(),"consolidation event update");
        }
    }

    private static void requireJobLock(
            Connection connection,ConsolidationJob job,String workerId) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(LOCK_JOB)) {
            statement.setString(1,job.jobId().toString()); statement.setString(2,JOB_TYPE);
            try (ResultSet rows=statement.executeQuery()) {
                if (!rows.next() || !"RUNNING".equals(rows.getString("status"))
                        || !workerId.equals(rows.getString("locked_by"))
                        || rows.getInt("attempt_count")!=job.attemptCount()) {
                    throw new SQLException("Consolidation lease is not owned by worker");
                }
            }
        }
    }

    private static void finishSuccess(
            Connection connection,UUID jobId,String workerId,Instant at) throws SQLException {
        try (PreparedStatement statement=connection.prepareStatement(SUCCEEDED)) {
            statement.setObject(1,utc(at)); statement.setObject(2,utc(at));
            statement.setString(3,jobId.toString()); statement.setString(4,workerId);
            requireOne(statement.executeUpdate(),"complete consolidation job");
        }
    }

    private void executeLocked(String sql,Binder binder,String operation) {
        try (Connection connection=dataSource.getConnection();
             PreparedStatement statement=connection.prepareStatement(sql)) {
            binder.bind(statement); requireOne(statement.executeUpdate(),operation);
        } catch (SQLException failure) { throw persistence("Could not "+operation,failure); }
    }

    private <T> T transaction(String operation,SqlWork<T> work) {
        try (Connection connection=dataSource.getConnection()) {
            boolean auto=connection.getAutoCommit(); connection.setAutoCommit(false);
            try { T value=work.apply(connection); connection.commit(); return value; }
            catch (SQLException|RuntimeException failure) { connection.rollback(); throw failure; }
            finally { connection.setAutoCommit(auto); }
        } catch (SQLException failure) { throw persistence("Could not "+operation,failure); }
    }

    private static ConsolidationJob mapJob(ResultSet rows) throws SQLException {
        UUID cycle=parsePayload(rows.getString("payload_json"));
        return new ConsolidationJob(
                UUID.fromString(rows.getString("job_id")),UUID.fromString(rows.getString("pet_id")),
                cycle,rows.getString("idempotency_key"),ConsolidationJobStatus.valueOf(rows.getString("status")),
                rows.getInt("attempt_count"),instant(rows,"not_before"),
                Optional.ofNullable(rows.getString("locked_by")),nullableInstant(rows,"locked_until"),
                Optional.ofNullable(rows.getString("last_error_sanitized")));
    }

    private static String payload(ConsolidationJob job) {
        JsonObject root=new JsonObject(); root.addProperty("sleepCycleId",job.sleepCycleId().toString());
        return root.toString();
    }

    private static UUID parsePayload(String json) throws SQLException {
        try {
            JsonElement parsed=JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(PAYLOAD_FIELDS)) {
                throw new IllegalArgumentException("unexpected payload fields");
            }
            return UUID.fromString(parsed.getAsJsonObject().get("sleepCycleId").getAsString());
        } catch (RuntimeException failure) { throw new SQLException("Invalid consolidation payload",failure); }
    }

    private static String tags(Set<String> values) {
        JsonArray array=new JsonArray(); values.stream().sorted().forEach(array::add); return array.toString();
    }

    private static Optional<Instant> nullableInstant(ResultSet rows,String column) throws SQLException {
        LocalDateTime value=rows.getObject(column,LocalDateTime.class);
        return value==null?Optional.empty():Optional.of(value.toInstant(ZoneOffset.UTC));
    }
    private static Instant instant(ResultSet rows,String column) throws SQLException {
        return nullableInstant(rows,column).orElseThrow(()->new SQLException(column+" cannot be null"));
    }
    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at,ZoneOffset.UTC); }
    private static int clamp(int value,int min,int max) { return Math.max(min,Math.min(max,value)); }
    private static boolean constraint(SQLException failure) {
        return failure.getSQLState()!=null && failure.getSQLState().startsWith("23");
    }
    private static String bounded(String value) {
        String normalized=Objects.requireNonNull(value,"value").strip();
        if (normalized.isEmpty()) normalized="RuntimeException";
        return normalized.substring(0,Math.min(normalized.length(),128));
    }
    private static void requireOne(int rows,String operation) throws SQLException {
        if (rows!=1) throw new SQLException(operation+" did not affect exactly one row");
    }
    private static PetPersistenceException persistence(String message,Throwable failure) {
        return new PetPersistenceException(message,failure);
    }
    private record Applied(PetTraits traits,Map<TraitName,Integer> deltas) { }
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface SqlWork<T> { T apply(Connection connection) throws SQLException; }
}
