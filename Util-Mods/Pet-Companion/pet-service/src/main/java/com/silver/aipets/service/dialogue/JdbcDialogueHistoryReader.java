package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** MariaDB reader for bounded, non-expired prompt-eligible dialogue events. */
public final class JdbcDialogueHistoryReader implements DialogueHistoryReader {
    private static final String EVENTS = """
            SELECT event_id, event_type, occurred_at, importance, summary, raw_player_text,
                   raw_pet_reply, short_term_expires_at, source_server, source_dimension,
                   source_provenance, location_json
            FROM pet_events
            WHERE pet_id=? AND prompt_eligible=TRUE
              AND (short_term_expires_at IS NULL OR short_term_expires_at>?)
            ORDER BY occurred_at DESC, event_id DESC LIMIT ?
            """;
    private static final String RELATIONSHIP_EVIDENCE = """
            SELECT interaction_id,event_id,relationship_type,strength,occurred_at
            FROM pet_relationship_evidence
            WHERE pet_id=? AND occurred_at>=?
            ORDER BY occurred_at DESC,event_id DESC LIMIT ?
            """;
    private final DataSource dataSource;

    public JdbcDialogueHistoryReader(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public DialogueHistory read(UUID petId, Instant now, int maximumEvents) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(now, "now");
        if (maximumEvents < 1 || maximumEvents > 64) {
            throw new IllegalArgumentException("maximumEvents must be between 1 and 64");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EVENTS)) {
            statement.setString(1, petId.toString());
            statement.setObject(2, utc(now));
            statement.setInt(3, maximumEvents);
            List<Event> events = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) events.add(map(rows));
            }
            // The query is newest-first for a cheap LIMIT; prompt rendering is chronological.
            events.sort(java.util.Comparator.comparing(Event::occurredAt)
                    .thenComparing(Event::eventId));
            List<ShortTermMemorySnippet> shortTerm = new ArrayList<>(events.size());
            List<DialogueTurn> turns = new ArrayList<>(events.size() * 2);
            for (Event event : events) {
                // DIALOGUE.summary is the model's previous reply, not evidence.
                // Its owner and pet turns appear below with their actual roles.
                if (!event.eventType().equals("DIALOGUE")) {
                    shortTerm.add(new ShortTermMemorySnippet(
                            event.eventId(), event.importance(),
                            event.eventType() + " on " + event.sourceServer() + " / "
                                    + event.sourceDimension() + ": " + event.summary(),
                            event.occurredAt(), event.expiresAt(),
                            relevance(event.eventType(), event.importance()), event.provenance(), event.location()));
                }
                if (event.playerText() != null) {
                    turns.add(new DialogueTurn(
                            DialogueTurn.Role.OWNER, event.playerText(), event.occurredAt()));
                }
                if (event.petReply() != null) {
                    turns.add(new DialogueTurn(
                            DialogueTurn.Role.PET, event.petReply(), event.occurredAt()));
                }
            }
            List<RelationshipEvidence> relationship = new ArrayList<>();
            try (PreparedStatement evidenceQuery = connection.prepareStatement(RELATIONSHIP_EVIDENCE)) {
                evidenceQuery.setString(1, petId.toString());
                evidenceQuery.setObject(2, utc(now.minus(java.time.Duration.ofDays(30))));
                evidenceQuery.setInt(3, Math.min(maximumEvents, 12));
                try (ResultSet rows = evidenceQuery.executeQuery()) {
                    while (rows.next()) {
                        relationship.add(new RelationshipEvidence(
                                UUID.fromString(rows.getString("interaction_id")),
                                UUID.fromString(rows.getString("event_id")),
                                new RelationshipSignal(
                                        RelationshipSignal.Type.valueOf(rows.getString("relationship_type")),
                                        rows.getInt("strength")),
                                instant(rows, "occurred_at")));
                    }
                }
            }
            relationship.sort(java.util.Comparator.comparing(RelationshipEvidence::occurredAt));
            return new DialogueHistory(shortTerm, turns, relationship);
        } catch (SQLException | RuntimeException failure) {
            throw failure instanceof PetPersistenceException existing
                    ? existing
                    : new PetPersistenceException("Could not read dialogue history", failure);
        }
    }

    private static Event map(ResultSet rows) throws SQLException {
        return new Event(
                UUID.fromString(rows.getString("event_id")),
                rows.getString("event_type"),
                importance(rows.getString("importance")),
                ShortTermMemorySnippet.bounded(rows.getString("summary"), 2_000, "summary"),
                instant(rows, "occurred_at"),
                optionalInstant(rows, "short_term_expires_at"),
                nullableBounded(rows.getString("raw_player_text")),
                nullableBounded(rows.getString("raw_pet_reply")),
                rows.getString("source_server") == null ? "unknown"
                        : rows.getString("source_server"),
                rows.getString("source_dimension") == null ? "unknown"
                        : rows.getString("source_dimension"),
                rows.getString("source_provenance"),
                com.silver.aipets.common.transport.SpatialLocationCodec.readStored(rows.getString("location_json")));
    }

    private static DialogueImportance importance(String value) throws SQLException {
        try {
            return DialogueImportance.valueOf(value);
        } catch (RuntimeException failure) {
            throw new SQLException("Invalid dialogue importance", failure);
        }
    }

    private static double relevance(String type, DialogueImportance importance) {
        if ("ACTION_COMPLETED".equals(type) || "DIRECT_INTERACTION".equals(type)) return .35;
        if ("NEW_AREA".equals(type) || "OWNER_RETURNED".equals(type)) return .7;
        return switch (importance) {
            case HIGH -> .9;
            case MEDIUM -> .65;
            case LOW -> .25;
        };
    }

    private static String nullableBounded(String value) throws SQLException {
        if (value == null) return null;
        try {
            return ShortTermMemorySnippet.bounded(value, 2_000, "dialogue text");
        } catch (RuntimeException failure) {
            throw new SQLException("Invalid retained dialogue text", failure);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        if (value == null) throw new SQLException(column + " cannot be null");
        return value.toInstant(ZoneOffset.UTC);
    }

    private static java.util.Optional<Instant> optionalInstant(ResultSet rows, String column)
            throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        return value == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(value.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private record Event(
            UUID eventId,
            String eventType,
            DialogueImportance importance,
            String summary,
            Instant occurredAt,
            java.util.Optional<Instant> expiresAt,
            String playerText,
            String petReply,
            String sourceServer,
            String sourceDimension,
            String provenance, java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> location) {
    }
}
