package com.silver.aipets.service.vector;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.persistence.PetPersistenceException;
import com.silver.aipets.service.persistence.PetRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Writes provider-reported embedding usage without retaining the source text. */
public final class JdbcEmbeddingUsageRecorder implements EmbeddingUsageRecorder {
    private static final String INSERT = """
            INSERT INTO ai_usage (call_id,pet_id,owner_uuid,operation,model,request_id,
                provider_id,input_tokens,cached_input_tokens,output_tokens,estimated_cost,
                latency_ms,status,error_category,created_at)
            VALUES (?,?,?,'EMBEDDING',?,?,?,?,0,0,?,?,?,?,?)
            """;

    private final DataSource dataSource;
    private final PetRepository pets;

    public JdbcEmbeddingUsageRecorder(DataSource dataSource, PetRepository pets) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.pets = Objects.requireNonNull(pets, "pets");
    }

    @Override
    public void record(UUID petId, EmbeddingModelResponse response, Instant at,
                       String status, String errorCategory) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(at, "at");
        Optional<Pet> pet = pets.findById(petId);
        if (pet.isEmpty()) return;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, petId.toString());
            statement.setString(3, pet.orElseThrow().ownerUuid().toString());
            statement.setString(4, response.embedding().model());
            statement.setString(5, UUID.randomUUID().toString());
            if (response.providerId().isPresent()) statement.setString(6, response.providerId().orElseThrow());
            else statement.setNull(6, java.sql.Types.VARCHAR);
            statement.setInt(7, response.inputTokens());
            statement.setBigDecimal(8, response.estimatedCost());
            statement.setLong(9, response.latencyMillis());
            statement.setString(10, status);
            if (errorCategory == null || errorCategory.isBlank()) statement.setNull(11, java.sql.Types.VARCHAR);
            else statement.setString(11, errorCategory.substring(0, Math.min(64, errorCategory.length())));
            statement.setTimestamp(12, Timestamp.from(at));
            statement.executeUpdate();
        } catch (SQLException failure) {
            if (!isConstraintViolation(failure)) {
                throw new PetPersistenceException("Could not persist embedding usage", failure);
            }
        }
    }

    private static boolean isConstraintViolation(SQLException failure) {
        return "23000".equals(failure.getSQLState()) || failure.getErrorCode() == 1062;
    }
}
