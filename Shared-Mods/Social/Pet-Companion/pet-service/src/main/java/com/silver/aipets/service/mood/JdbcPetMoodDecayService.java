package com.silver.aipets.service.mood;

import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.service.persistence.PetPersistenceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/** Bounded SQL-authoritative mood decay, including held and unloaded pets. */
public final class JdbcPetMoodDecayService {
    private final DataSource db;
    public JdbcPetMoodDecayService(DataSource db) { this.db=db; }

    public int decayDue(Instant now, int limit) {
        if (limit<1 || limit>1000) throw new IllegalArgumentException("limit out of bounds");
        List<UUID> due=new ArrayList<>();
        try (Connection c=db.getConnection(); PreparedStatement q=c.prepareStatement("""
                SELECT m.pet_id FROM pet_mood m
                WHERE m.last_decay_at<=? ORDER BY m.last_decay_at,m.pet_id LIMIT ?
                """)) {
            q.setObject(1,utc(now.minusSeconds(1800))); q.setInt(2,limit);
            try (ResultSet rows=q.executeQuery()) {
                while (rows.next()) due.add(UUID.fromString(rows.getString(1)));
            }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not list due mood decay",failure);
        }
        int changed=0;
        for (UUID petId:due) if (decayOne(petId,now)) changed++;
        return changed;
    }

    private boolean decayOne(UUID petId,Instant now) {
        try (Connection c=db.getConnection()) {
            boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try (PreparedStatement q=c.prepareStatement("""
                        SELECT p.mob_type,
                            t.curiosity,t.boldness,t.playfulness,t.expressiveness,
                            t.independence,t.attachment,t.trust,t.security,
                            t.relationship_summary,t.summary_version,t.updated_at AS traits_at,
                            m.content,m.excited,m.anxious,m.tired,m.last_decay_at,m.updated_at AS mood_at
                        FROM pets p JOIN pet_traits t ON t.pet_id=p.pet_id
                        JOIN pet_mood m ON m.pet_id=p.pet_id WHERE p.pet_id=? FOR UPDATE
                        """)) {
                    q.setString(1,petId.toString());
                    try (ResultSet row=q.executeQuery()) {
                        if (!row.next()) {
                            c.commit(); return false;
                        }
                        PetSpecies species=PetSpecies.fromEntityTypeId(row.getString("mob_type"));
                        PetTraits traits=new PetTraits(row.getInt("curiosity"),row.getInt("boldness"),
                                row.getInt("playfulness"),row.getInt("expressiveness"),
                                row.getInt("independence"),row.getInt("attachment"),
                                row.getInt("trust"),row.getInt("security"),
                                row.getString("relationship_summary"),row.getLong("summary_version"),
                                instant(row,"traits_at"));
                        PetMood mood=new PetMood(row.getInt("content"),row.getInt("excited"),
                                row.getInt("anxious"),row.getInt("tired"),
                                instant(row,"last_decay_at"),instant(row,"mood_at"));
                        PetMood decayed=mood.decay(species,traits,now);
                        if (decayed.lastDecayAt().equals(mood.lastDecayAt())) {
                            c.commit(); return false;
                        }
                        try (PreparedStatement update=c.prepareStatement("""
                                UPDATE pet_mood SET content=?,excited=?,anxious=?,tired=?,
                                    last_decay_at=?,updated_at=? WHERE pet_id=?
                                """)) {
                            update.setInt(1,decayed.content()); update.setInt(2,decayed.excited());
                            update.setInt(3,decayed.anxious()); update.setInt(4,decayed.tired());
                            update.setObject(5,utc(decayed.lastDecayAt()));
                            update.setObject(6,utc(decayed.updatedAt()));
                            update.setString(7,petId.toString()); update.executeUpdate();
                        }
                        c.commit(); return true;
                    }
                }
            } catch (RuntimeException | SQLException failure) {
                c.rollback(); throw failure;
            } finally { c.setAutoCommit(auto); }
        } catch (SQLException failure) {
            throw new PetPersistenceException("Could not decay pet mood",failure);
        }
    }

    private static Instant instant(ResultSet row,String field) throws SQLException {
        return row.getObject(field,LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at,ZoneOffset.UTC); }
}
