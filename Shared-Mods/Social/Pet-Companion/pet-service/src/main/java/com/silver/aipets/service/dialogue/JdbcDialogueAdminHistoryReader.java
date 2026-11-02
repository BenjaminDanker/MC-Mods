package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.transport.DialogueContextUsageWire;
import com.silver.aipets.common.transport.DialogueContextUsageWireCodec;
import com.silver.aipets.common.transport.DialogueHistoryWireResult;
import com.silver.aipets.service.persistence.PetPersistenceException;
import com.silver.aipets.service.persistence.PetRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
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
import java.util.Optional;
import java.util.UUID;

/** Bounded admin projection of persisted conversation events and provider usage. */
public final class JdbcDialogueAdminHistoryReader {
    private static final String EVENTS = """
            SELECT occurred_at, importance, summary, raw_player_text, raw_pet_reply
            FROM pet_events WHERE pet_id=?
            ORDER BY occurred_at DESC, event_id DESC LIMIT ?
            """;
    private static final String USAGE = """
            SELECT created_at, operation, model, input_tokens, cached_input_tokens,
                   output_tokens, estimated_cost, status, context_json
            FROM ai_usage WHERE owner_uuid=?
            ORDER BY created_at DESC, call_id DESC LIMIT ?
            """;

    private final DataSource dataSource;
    private final PetRepository pets;
    private final DialogueContextUsageWireCodec contextCodec = new DialogueContextUsageWireCodec();

    public JdbcDialogueAdminHistoryReader(DataSource dataSource, PetRepository pets) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.pets = Objects.requireNonNull(pets, "pets");
    }

    public Optional<DialogueHistoryWireResult> read(UUID ownerUuid, int eventLimit, int usageLimit) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        if (eventLimit < 1 || eventLimit > 32 || usageLimit < 1 || usageLimit > 64) {
            throw new IllegalArgumentException("history limits are out of range");
        }
        Optional<Pet> pet = pets.findByOwner(ownerUuid);
        if (pet.isEmpty()) return Optional.empty();
        try (Connection connection = dataSource.getConnection()) {
            List<DialogueHistoryWireResult.ConversationEntry> conversations = readEvents(
                    connection, pet.orElseThrow().petId(), eventLimit);
            List<DialogueHistoryWireResult.UsageEntry> usage = readUsage(
                    connection, ownerUuid, usageLimit);
            return Optional.of(new DialogueHistoryWireResult(
                    ownerUuid, pet.orElseThrow().petId(), pet.orElseThrow().name(), conversations, usage));
        } catch (SQLException | RuntimeException failure) {
            throw failure instanceof PetPersistenceException existing
                    ? existing
                    : new PetPersistenceException("Could not read dialogue admin history", failure);
        }
    }

    private List<DialogueHistoryWireResult.ConversationEntry> readEvents(
            Connection connection, UUID petId, int limit) throws SQLException {
        List<DialogueHistoryWireResult.ConversationEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(EVENTS)) {
            statement.setString(1, petId.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new DialogueHistoryWireResult.ConversationEntry(
                            instant(rows, "occurred_at"), rows.getString("importance"),
                            rows.getString("summary"), rows.getString("raw_player_text"),
                            rows.getString("raw_pet_reply")));
                }
            }
        }
        return result;
    }

    private List<DialogueHistoryWireResult.UsageEntry> readUsage(
            Connection connection, UUID ownerUuid, int limit) throws SQLException {
        List<DialogueHistoryWireResult.UsageEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(USAGE)) {
            statement.setString(1, ownerUuid.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String contextJson = rows.getString("context_json");
                    Optional<DialogueContextUsageWire> context = contextJson == null
                            ? Optional.empty() : Optional.of(contextCodec.decode(contextJson));
                    BigDecimal cost = rows.getBigDecimal("estimated_cost");
                    result.add(new DialogueHistoryWireResult.UsageEntry(
                            instant(rows, "created_at"), rows.getString("operation"),
                            rows.getString("model"), rows.getInt("input_tokens"),
                            rows.getInt("cached_input_tokens"), rows.getInt("output_tokens"),
                            cost == null ? BigDecimal.ZERO : cost,
                            rows.getString("status"), context));
                }
            }
        }
        return result;
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        LocalDateTime value = rows.getObject(column, LocalDateTime.class);
        if (value == null) throw new SQLException(column + " cannot be null");
        return value.toInstant(ZoneOffset.UTC);
    }
}
