package com.silver.aipets.service.recall;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Retains the recall audit row while releasing only its consumed-month marker. */
public final class JdbcRecallAdminRepository implements RecallAdminRepository {
    private final DataSource dataSource;

    public JdbcRecallAdminRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean resetConsumedPeriod(UUID petId, String periodKey) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(periodKey, "periodKey");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE pet_recall_usage
                     SET consumed_period_key = NULL, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE pet_id = ? AND consumed_period_key = ?
                     """)) {
            statement.setString(1, petId.toString());
            statement.setString(2, periodKey);
            int changed = statement.executeUpdate();
            if (changed > 1) {
                throw new IllegalStateException("Recall period uniqueness invariant is violated");
            }
            return changed == 1;
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not reset recall period", failure);
        }
    }
}
