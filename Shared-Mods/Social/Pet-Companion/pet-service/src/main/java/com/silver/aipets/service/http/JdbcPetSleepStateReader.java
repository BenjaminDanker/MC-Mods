package com.silver.aipets.service.http;

import com.silver.aipets.service.persistence.PetPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Loaded-on-demand sleep flag; absence is treated as awake until sleep state is initialized. */
public final class JdbcPetSleepStateReader implements PetSleepStateReader {
    private static final String SELECT_SLEEP = """
            SELECT sleeping FROM pet_sleep_state WHERE pet_id = ?
            """;

    private final DataSource dataSource;

    public JdbcPetSleepStateReader(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean isSleeping(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SLEEP)) {
            statement.setString(1, petId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return false;
                }
                boolean sleeping = rows.getBoolean(1);
                if (rows.next()) {
                    throw new PetPersistenceException(
                            "Sleep-state uniqueness invariant is violated", null);
                }
                return sleeping;
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not read pet sleep state", failure);
        }
    }
}
