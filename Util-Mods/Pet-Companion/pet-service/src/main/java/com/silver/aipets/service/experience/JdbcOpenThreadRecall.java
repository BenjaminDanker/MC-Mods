package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Reads persistent location-cued threads from authoritative SQL. */
public final class JdbcOpenThreadRecall implements OpenThreadRecall {
    private final DataSource db;
    public JdbcOpenThreadRecall(DataSource db) { this.db = db; }

    @Override public Optional<ThreadCue> find(Pet pet, String type, String summary, Instant now) {
        if (!Set.of("OWNER_RETURNED_AFTER_ABSENCE", "ARRIVED_TOGETHER").contains(type)
                || !(pet.placement() instanceof PlacedPlacement placed)) return Optional.empty();
        String biome = summary.contains("biome=")
                ? summary.substring(summary.indexOf("biome=") + 6).split("\\s+", 2)[0] : "";
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT thread_id,thread_key,location_server,location_dimension,location_cue,
                       created_at,last_referenced_at,state
                FROM pet_open_threads
                WHERE pet_id=? AND state IN ('OPEN','PAUSED')
                  AND (expires_at IS NULL OR expires_at>?)
                  AND location_server=? AND location_dimension=?
                ORDER BY created_at DESC LIMIT 8
                """)) {
            q.setString(1, pet.petId().toString());
            q.setObject(2,utc(now)); q.setString(3,placed.backendId().value());
            q.setString(4,placed.dimensionId().toString());
            try (ResultSet rows = q.executeQuery()) {
                while (rows.next()) {
                    Instant created = utc(rows, "created_at");
                    if (created.plus(Duration.ofMinutes(30)).isAfter(now)) continue;
                    LocalDateTime last = rows.getObject("last_referenced_at", LocalDateTime.class);
                    if (last != null && last.toInstant(ZoneOffset.UTC)
                            .plus(Duration.ofHours(12)).isAfter(now)) continue;
                    String locationCue=rows.getString("location_cue");
                    if(!biome.isBlank()&&locationCue!=null&&!locationCue.equalsIgnoreCase(biome)) continue;
                    return Optional.of(new ThreadCue(UUID.fromString(rows.getString("thread_id")),
                            rows.getString("thread_key")));
                }
            }
        } catch (SQLException failure) { throw new IllegalStateException("Thread lookup failed", failure); }
        return Optional.empty();
    }

    @Override public void followedUp(UUID petId, UUID memoryId, Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                UPDATE pet_open_threads SET state='ACTIVE',last_referenced_at=?,version=version+1
                WHERE pet_id=? AND thread_id=? AND state IN ('OPEN','PAUSED')
                """)) {
            q.setObject(1, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            q.setString(2, petId.toString());
            q.setString(3, memoryId.toString());
            q.executeUpdate();
        } catch (SQLException failure) { throw new IllegalStateException("Thread follow-up failed", failure); }
    }

    private static Instant utc(ResultSet rows, String name) throws SQLException {
        return rows.getObject(name, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant,ZoneOffset.UTC);
    }
}
