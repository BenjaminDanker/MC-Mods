package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** MariaDB decision and planning usage store. */
public final class JdbcDecisionStore implements DecisionStore {
    private final DataSource db;
    public JdbcDecisionStore(DataSource db) { this.db=db; }

    @Override public Optional<PetDecisionPlanner.Decision> current(UUID petId, Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement(
                """
                SELECT d.intent,d.utterance,d.expires_at,d.next_model_at,d.motivation_memory_id
                FROM pet_decisions d JOIN pets p ON p.pet_id=d.pet_id
                JOIN pet_events e ON e.event_id=d.source_event_id
                WHERE d.pet_id=? AND p.placement_state='PLACED'
                  AND d.entity_uuid=p.entity_uuid
                  AND e.source_server=p.placed_server
                  AND e.source_dimension=p.placed_dimension
                """)) {
            q.setString(1, petId.toString());
            try (ResultSet row = q.executeQuery()) {
                if (!row.next()) return Optional.empty();
                Instant next=row.getObject("next_model_at",LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC);
                if (!next.isAfter(now)) return Optional.empty();
                Instant expiry = row.getObject("expires_at", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC);
                return Optional.of(expiry.isAfter(now)
                        ? new PetDecisionPlanner.Decision(row.getString("intent"),
                                row.getString("utterance"),expiry,
                                uuid(row.getString("motivation_memory_id")))
                        : new PetDecisionPlanner.Decision("PAUSE",null,next));
            }
        } catch (SQLException failure) { throw new IllegalStateException("Decision lookup failed", failure); }
    }

    @Override public Optional<PetDecisionPlanner.Decision> forEvent(
            UUID petId, UUID eventId, Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT d.intent,d.utterance,d.expires_at,d.motivation_memory_id
                FROM pet_decisions d JOIN pets p ON p.pet_id=d.pet_id
                JOIN pet_events e ON e.event_id=d.source_event_id
                WHERE d.pet_id=? AND d.source_event_id=? AND d.expires_at>?
                  AND p.placement_state='PLACED' AND d.entity_uuid=p.entity_uuid
                  AND e.source_server=p.placed_server
                  AND e.source_dimension=p.placed_dimension
                """)) {
            q.setString(1, petId.toString());
            q.setString(2, eventId.toString());
            q.setObject(3, utc(now));
            try (ResultSet row = q.executeQuery()) {
                if (!row.next()) return Optional.empty();
                return Optional.of(new PetDecisionPlanner.Decision(row.getString("intent"),
                        row.getString("utterance"), row.getObject("expires_at", LocalDateTime.class)
                                .toInstant(ZoneOffset.UTC),
                        uuid(row.getString("motivation_memory_id"))));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Decision replay lookup failed", failure);
        }
    }

    @Override public void save(UUID petId, UUID eventId, PetDecisionPlanner.Decision value,
            Instant now, Instant next) {
        saveIfNewer(petId, eventId, value, now, next);
    }

    @Override public boolean saveIfNewer(UUID petId, UUID eventId,
            PetDecisionPlanner.Decision value, Instant now, Instant next) {
        return saveChecked(petId, null, eventId, value, now, next);
    }

    @Override public boolean saveForPlacement(Pet pet, UUID eventId,
            PetDecisionPlanner.Decision value, Instant now, Instant next) {
        if (!(pet.placement() instanceof PlacedPlacement placed)
                || !placed.isMaterialized()) return false;
        return saveChecked(pet.petId(), placed, eventId, value, now, next);
    }

    private boolean saveChecked(UUID petId, PlacedPlacement expected, UUID eventId,
            PetDecisionPlanner.Decision value, Instant now, Instant next) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                String entityId;
                try (PreparedStatement placement = c.prepareStatement("""
                    SELECT placement_state,entity_uuid,placed_server,placed_dimension
                    FROM pets WHERE pet_id=? FOR UPDATE
                    """)) {
                    placement.setString(1, petId.toString());
                    try (ResultSet row = placement.executeQuery()) {
                        if (!row.next() || !"PLACED".equals(row.getString(1))
                                || row.getString(2) == null) { c.commit(); return false; }
                        entityId = row.getString(2);
                        if (expected != null && !DecisionPlacementPolicy.matches(expected,
                                row.getString(1), entityId, row.getString(3),
                                row.getString(4))) {
                            c.commit(); return false;
                        }
                    }
                }
                if (value.memoryId() != null) {
                    try (PreparedStatement active = c.prepareStatement("""
                        SELECT memory_id FROM long_term_memories
                        WHERE pet_id=? AND memory_id=? AND active=TRUE FOR UPDATE
                        """)) {
                        active.setString(1,petId.toString());
                        active.setString(2,value.memoryId().toString());
                        try (ResultSet row = active.executeQuery()) {
                            if (!row.next()) { c.commit(); return false; }
                        }
                    }
                }
                try (PreparedStatement insert = c.prepareStatement("""
                    INSERT IGNORE INTO pet_decisions
                    (pet_id,source_event_id,intent,utterance,created_at,expires_at,
                     next_model_at,motivation_memory_id,entity_uuid,decision_version)
                    VALUES (?,?,?,?,?,?,?,?,?,1)
                    """)) {
                    bind(insert, petId, eventId, value, now, next, entityId);
                    if (insert.executeUpdate() == 1) { c.commit(); return true; }
                }
                try (PreparedStatement select = c.prepareStatement("""
                    SELECT source_event_id,created_at FROM pet_decisions
                    WHERE pet_id=? FOR UPDATE
                    """)) {
                    select.setString(1, petId.toString());
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next()) throw new SQLException("Decision insert was ignored");
                        if (row.getString(1).equals(eventId.toString())) {
                            c.commit(); return true;
                        }
                        Instant previous = row.getObject(2, LocalDateTime.class)
                                .toInstant(ZoneOffset.UTC);
                        if (!previous.isBefore(now)) { c.commit(); return false; }
                    }
                }
                try (PreparedStatement update = c.prepareStatement("""
                    UPDATE pet_decisions SET source_event_id=?,intent=?,utterance=?,
                    created_at=?,expires_at=?,next_model_at=?,motivation_memory_id=?,entity_uuid=?,
                    decision_version=decision_version+1 WHERE pet_id=?
                    """)) {
                    update.setString(1,eventId.toString()); update.setString(2,value.intent());
                    update.setString(3,value.utterance()); update.setObject(4,utc(now));
                    update.setObject(5,utc(value.expiresAt())); update.setObject(6,utc(next));
                    update.setString(7, value.memoryId() == null ? null : value.memoryId().toString());
                    update.setString(8,entityId);
                    update.setString(9,petId.toString()); update.executeUpdate();
                }
                c.commit(); return true;
            } catch (SQLException failure) {
                c.rollback(); throw failure;
            }
        } catch (SQLException failure) { throw new IllegalStateException("Decision save failed", failure); }
    }

    private static void bind(PreparedStatement q, UUID petId, UUID eventId,
            PetDecisionPlanner.Decision value, Instant now, Instant next,
            String entityId) throws SQLException {
        q.setString(1,petId.toString()); q.setString(2,eventId.toString());
        q.setString(3,value.intent()); q.setString(4,value.utterance());
        q.setObject(5,utc(now)); q.setObject(6,utc(value.expiresAt()));
        q.setObject(7,utc(next));
        q.setString(8,value.memoryId() == null ? null : value.memoryId().toString());
        q.setString(9,entityId);
    }

    private static UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }

    @Override public void usage(Pet pet, UUID requestId, String model,
            DialogueModelResponse result, String status, Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
            INSERT INTO ai_usage (call_id,pet_id,owner_uuid,operation,model,request_id,
                provider_id,input_tokens,cached_input_tokens,output_tokens,estimated_cost,
                latency_ms,status,error_category,created_at)
            VALUES (?,?,?,'PLANNING',?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            q.setString(1, UUID.randomUUID().toString()); q.setString(2, pet.petId().toString());
            q.setString(3, pet.ownerUuid().toString()); q.setString(4, model);
            q.setString(5, requestId.toString());
            q.setString(6, result == null ? null : result.providerId().orElse(null));
            q.setInt(7, result == null ? 0 : result.inputTokens());
            q.setInt(8, result == null ? 0 : result.cachedInputTokens());
            q.setInt(9, result == null ? 0 : result.outputTokens());
            q.setBigDecimal(10, result == null ? BigDecimal.ZERO : result.estimatedCost());
            q.setLong(11, result == null ? 0 : result.latencyMillis());
            q.setString(12, status); q.setString(13, status.equals("FAILED") ? "PROVIDER" : null);
            q.setObject(14, utc(now)); q.executeUpdate();
        } catch (SQLException failure) { throw new IllegalStateException("Planning usage failed", failure); }
    }

    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at, ZoneOffset.UTC); }
}
