package com.silver.aipets.service.memory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** MariaDB relational memory source; all point operations are pet-scoped. */
public final class JdbcLongTermMemoryStore implements LongTermMemoryStore {
    private static final String COLUMNS = """
            memory_id, pet_id, memory_text, memory_version, importance,
            emotion_tags, entity_tags, location_tags, active
            """;
    private static final String FIND_ACTIVE = "SELECT " + COLUMNS + """
             FROM long_term_memories
             WHERE pet_id = ? AND memory_id = ? AND active = TRUE
            """;
    private static final String LIST_FIRST = "SELECT " + COLUMNS + """
             FROM long_term_memories
             WHERE active = TRUE
             ORDER BY memory_id LIMIT ?
            """;
    private static final String LIST_AFTER = "SELECT " + COLUMNS + """
             FROM long_term_memories
             WHERE active = TRUE AND memory_id > ?
             ORDER BY memory_id LIMIT ?
            """;
    private static final String READY = """
            UPDATE long_term_memories
            SET embedding_status = 'READY', embedding_model = ?, embedding_reference = ?,
                updated_at = ?
            WHERE pet_id = ? AND memory_id = ? AND memory_version = ? AND active = TRUE
            """;
    private static final String FAILED = """
            UPDATE long_term_memories
            SET embedding_status = 'FAILED', embedding_model = NULL,
                embedding_reference = NULL, updated_at = ?
            WHERE pet_id = ? AND memory_id = ? AND memory_version = ?
            """;
    private static final String RECALLED = """
            UPDATE long_term_memories
            SET last_recalled_at = ?, recall_count = recall_count + 1
            WHERE pet_id = ? AND memory_id = ? AND active = TRUE
            """;

    private final DataSource dataSource;

    public JdbcLongTermMemoryStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<LongTermMemoryCard> findActive(UUID petId, UUID memoryId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE)) {
            statement.setString(1, petId.toString());
            statement.setString(2, memoryId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                LongTermMemoryCard card = map(rows);
                if (rows.next()) {
                    throw new SQLException("Memory uniqueness invariant is violated");
                }
                return Optional.of(card);
            }
        } catch (SQLException | RuntimeException failure) {
            throw persistence("Could not read long-term memory", failure);
        }
    }

    @Override
    public MemoryPage listActive(Optional<UUID> afterMemoryId, int limit) {
        Objects.requireNonNull(afterMemoryId, "afterMemoryId");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        // Fetch one extra row so the cursor is emitted only when another page exists.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     afterMemoryId.isPresent() ? LIST_AFTER : LIST_FIRST)) {
            int parameter = 1;
            if (afterMemoryId.isPresent()) {
                statement.setString(parameter++, afterMemoryId.orElseThrow().toString());
            }
            statement.setInt(parameter, limit + 1);
            List<LongTermMemoryCard> cards = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    cards.add(map(rows));
                }
            }
            boolean hasMore = cards.size() > limit;
            if (hasMore) {
                cards.removeLast();
            }
            Optional<UUID> next = hasMore
                    ? Optional.of(cards.getLast().memoryId()) : Optional.empty();
            return new MemoryPage(cards, next);
        } catch (SQLException | RuntimeException failure) {
            throw persistence("Could not page active long-term memories", failure);
        }
    }

    @Override
    public void markEmbeddingReady(
            UUID petId, UUID memoryId, long expectedVersion,
            String model, String reference, Instant at) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(reference, "reference");
        executeVersioned(READY, statement -> {
            statement.setString(1, model);
            statement.setString(2, reference);
            statement.setObject(3, utc(at));
            statement.setString(4, petId.toString());
            statement.setString(5, memoryId.toString());
            statement.setLong(6, expectedVersion);
        }, "mark memory embedding ready");
    }

    @Override
    public void markEmbeddingFailed(
            UUID petId, UUID memoryId, long expectedVersion, Instant at) {
        executeVersioned(FAILED, statement -> {
            statement.setObject(1, utc(at));
            statement.setString(2, petId.toString());
            statement.setString(3, memoryId.toString());
            statement.setLong(4, expectedVersion);
        }, "mark memory embedding failed");
    }

    @Override
    public void recordRecalled(UUID petId, UUID memoryId, Instant at) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(at, "at");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECALLED)) {
            statement.setObject(1, utc(at));
            statement.setString(2, petId.toString());
            statement.setString(3, memoryId.toString());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not update memory recall counters", failure);
        }
    }

    private void executeVersioned(String sql, Binder binder, String operation) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(operation + " lost its pet/version precondition");
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not " + operation, failure);
        }
    }

    private static LongTermMemoryCard map(ResultSet rows) throws SQLException {
        return new LongTermMemoryCard(
                UUID.fromString(rows.getString("memory_id")),
                UUID.fromString(rows.getString("pet_id")),
                rows.getLong("memory_version"),
                rows.getString("memory_text"),
                MemoryImportance.valueOf(rows.getString("importance")),
                tags(rows.getString("emotion_tags"), "emotion_tags"),
                tags(rows.getString("entity_tags"), "entity_tags"),
                tags(rows.getString("location_tags"), "location_tags"),
                rows.getBoolean("active"));
    }

    private static Set<String> tags(String json, String column) throws SQLException {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() > 32) {
                throw new IllegalArgumentException("expected bounded array");
            }
            Set<String> tags = new HashSet<>();
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("expected string tag");
                }
                tags.add(element.getAsString());
            }
            return Set.copyOf(tags);
        } catch (RuntimeException malformed) {
            throw new SQLException("Invalid " + column + " JSON", malformed);
        }
    }

    private static LocalDateTime utc(Instant at) {
        return LocalDateTime.ofInstant(Objects.requireNonNull(at, "at"), ZoneOffset.UTC);
    }

    private static PetPersistenceException persistence(String message, Throwable failure) {
        return failure instanceof PetPersistenceException existing
                ? existing : new PetPersistenceException(message, failure);
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
