package com.silver.aipets.service.dialogue;

import java.sql.*;
import java.time.*;
import java.util.UUID;

/** Writes Luna's source-bound proposals into the existing memory/thread transaction. */
final class OwnerMemoryWriter {
    private OwnerMemoryWriter() { }

    static void write(Connection c, DialoguePersistRequest request, OwnerMemoryProposal proposal) throws SQLException {
        String pet = request.expectedPet().petId().toString();
        String priorKey = null;
        if (proposal.replacesMemoryId().isPresent()) {
            try (PreparedStatement q = c.prepareStatement("""
                    SELECT fact_key FROM long_term_memories
                    WHERE memory_id=? AND pet_id=? AND active=TRUE AND memory_version=? FOR UPDATE
                    """)) {
                q.setString(1, proposal.replacesMemoryId().orElseThrow().toString());
                q.setString(2, pet); q.setLong(3, proposal.expectedMemoryVersion());
                try (ResultSet r = q.executeQuery()) { if (!r.next()) return; priorKey = r.getString(1); }
            }
        }
        if (proposal.threadId().isPresent()) {
            try (PreparedStatement q = c.prepareStatement("""
                    SELECT state,created_at,expires_at FROM pet_open_threads
                    WHERE thread_id=? AND pet_id=? AND version=? FOR UPDATE
                    """)) {
                q.setString(1, proposal.threadId().orElseThrow().toString());
                q.setString(2, pet); q.setLong(3, proposal.expectedThreadVersion());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) return;
                    LocalDateTime expires = r.getObject("expires_at", LocalDateTime.class);
                    if (expires != null && !expires.toInstant(ZoneOffset.UTC).isAfter(request.occurredAt())) return;
                    var current = new OpenThreadStateMachine.ThreadState(
                            OpenThreadStateMachine.State.valueOf(r.getString("state")),
                            r.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            proposal.expectedThreadVersion(), request.occurredAt());
                    var event = switch (proposal.threadOperation()) {
                        case CONTINUE -> current.state() == OpenThreadStateMachine.State.PAUSED
                                ? OpenThreadStateMachine.Event.RESUMED : OpenThreadStateMachine.Event.CUE_FOUND;
                        case PAUSE -> OpenThreadStateMachine.Event.INTERRUPTED;
                        case COMPLETE -> OpenThreadStateMachine.Event.OWNER_COMPLETED;
                        case FORGET -> OpenThreadStateMachine.Event.ABANDONED;
                        default -> null;
                    };
                    if (current.state() == OpenThreadStateMachine.State.COMPLETED
                            || current.state() == OpenThreadStateMachine.State.FORGOTTEN) return;
                    var next = event == null ? current : OpenThreadStateMachine.transition(
                            current, event, current.version(), request.occurredAt());
                    // A summary correction also advances the version; illegal lifecycle changes do not.
                    if (event != null && next.state() == current.state()
                            && !(proposal.threadOperation() == OwnerMemoryProposal.Operation.CONTINUE
                                && current.state() == OpenThreadStateMachine.State.ACTIVE)) return;
                    try (PreparedStatement u = c.prepareStatement("""
                            UPDATE pet_open_threads SET summary=?,state=?,last_referenced_at=?,updated_at=?,version=version+1
                            WHERE thread_id=? AND pet_id=? AND version=?
                            """)) {
                        u.setString(1, proposal.summary()); u.setString(2, next.state().name());
                        u.setObject(3, utc(request.occurredAt())); u.setObject(4, utc(request.occurredAt()));
                        u.setString(5, proposal.threadId().orElseThrow().toString());
                        u.setString(6, pet); u.setLong(7, current.version());
                        if (u.executeUpdate() != 1) return;
                    }
                }
            }
        }
        if (proposal.replacesMemoryId().isPresent()) {
            try (PreparedStatement u = c.prepareStatement("""
                    UPDATE long_term_memories SET active=FALSE,updated_at=?
                    WHERE memory_id=? AND pet_id=? AND active=TRUE AND memory_version=?
                    """)) {
                u.setObject(1, utc(request.occurredAt()));
                u.setString(2, proposal.replacesMemoryId().orElseThrow().toString());
                u.setString(3, pet); u.setLong(4, proposal.expectedMemoryVersion());
                if (u.executeUpdate() != 1) throw new SQLException("Memory version changed within transaction");
            }
        }
        if (proposal.threadId().isPresent() && proposal.replacesMemoryId().isEmpty()
                && (proposal.threadOperation() == OwnerMemoryProposal.Operation.CONTINUE
                    || proposal.threadOperation() == OwnerMemoryProposal.Operation.PAUSE)) return;
        boolean closesThread = proposal.threadOperation() == OwnerMemoryProposal.Operation.COMPLETE
                || proposal.threadOperation() == OwnerMemoryProposal.Operation.FORGET;
        if (closesThread && proposal.threadId().isPresent()) {
            try (PreparedStatement s = c.prepareStatement("""
                    UPDATE long_term_memories m JOIN long_term_memory_source_events links ON links.memory_id=m.memory_id
                    JOIN pet_open_threads t ON t.source_event_id=links.source_event_id AND t.pet_id=m.pet_id
                    SET m.active=FALSE,m.updated_at=?
                    WHERE t.thread_id=? AND m.pet_id=? AND m.memory_kind='OPEN_THREAD' AND m.active=TRUE
                    """)) {
                s.setObject(1, utc(request.occurredAt()));
                s.setString(2, proposal.threadId().orElseThrow().toString()); s.setString(3, pet); s.executeUpdate();
            }
        }
        UUID memory = UUID.nameUUIDFromBytes((request.eventId() + ":owner-memory").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean thread = proposal.kind() == OwnerMemoryProposal.Kind.OPEN_THREAD
                || proposal.threadId().isPresent();
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO long_term_memories
                (memory_id,pet_id,memory_text,memory_version,importance,emotion_tags,entity_tags,location_tags,
                 provenance,fact_key,memory_kind,confidence,salience,emotional_significance,supersedes_memory_id,
                 embedding_status,active,created_at,updated_at)
                VALUES (?,?,?,1,'MEDIUM','[]','[]','[]','OWNER_STATEMENT',?,?,0.850,3,0,?,'PENDING',TRUE,?,?)
                """)) {
            s.setString(1, memory.toString()); s.setString(2, pet);
            s.setString(3, "Owner-reported: " + proposal.summary());
            s.setString(4, priorKey == null ? "owner.report." + memory : priorKey);
            s.setString(5, closesThread ? "FACT" : thread ? "OPEN_THREAD" : proposal.kind().name());
            s.setString(6, proposal.replacesMemoryId().map(Object::toString).orElse(null));
            s.setObject(7, utc(request.occurredAt())); s.setObject(8, utc(request.occurredAt())); s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO long_term_memory_source_events(memory_id,pet_id,source_event_id,created_at) VALUES (?,?,?,?)
                """)) {
            s.setString(1, memory.toString()); s.setString(2, pet);
            s.setString(3, request.eventId().toString()); s.setObject(4, utc(request.occurredAt())); s.executeUpdate();
        }
        if (proposal.replacesMemoryId().isPresent()) {
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT IGNORE INTO long_term_memory_source_events(memory_id,pet_id,source_event_id,created_at)
                    SELECT ?,pet_id,source_event_id,? FROM long_term_memory_source_events WHERE memory_id=? AND pet_id=?
                    """)) {
                s.setString(1, memory.toString()); s.setObject(2, utc(request.occurredAt()));
                s.setString(3, proposal.replacesMemoryId().orElseThrow().toString()); s.setString(4, pet); s.executeUpdate();
            }
        }
        if (proposal.threadOperation() == OwnerMemoryProposal.Operation.CREATE) {
            UUID id = UUID.nameUUIDFromBytes((request.eventId() + ":owner-thread").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (PreparedStatement s = c.prepareStatement("""
                    INSERT INTO pet_open_threads(thread_id,pet_id,summary,source_kind,source_event_id,thread_key,
                    state,created_at,updated_at,next_cue,expires_at,version)
                    VALUES (?,?,?,'OWNER_REPORTED',?,?,'OPEN',?,?,?,?,1)
                    """)) {
                s.setString(1, id.toString()); s.setString(2, pet); s.setString(3, proposal.summary());
                s.setString(4, request.eventId().toString()); s.setString(5, "owner." + id);
                s.setObject(6, utc(request.occurredAt())); s.setObject(7, utc(request.occurredAt()));
                s.setString(8, "owner returns to this activity");
                s.setObject(9, utc(request.occurredAt().plus(Duration.ofDays(30)))); s.executeUpdate();
            }
        }
    }

    private static LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
