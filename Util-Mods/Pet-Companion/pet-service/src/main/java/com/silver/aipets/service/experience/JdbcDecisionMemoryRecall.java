package com.silver.aipets.service.experience;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Only a small recent card window is read, with current active/provenance values. */
public final class JdbcDecisionMemoryRecall implements DecisionMemoryRecall {
    private final DataSource db;
    public JdbcDecisionMemoryRecall(DataSource db) { this.db = db; }

    @Override public Optional<Cue> find(UUID petId, String type, String summary) {
        if (!type.equals("OWNER_ACTION") && !type.equals("SURVIVED_DANGER")
                && !type.equals("NEARBY_THREAT")) return Optional.empty();
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
            SELECT memory_id,provenance,memory_text FROM long_term_memories
            WHERE pet_id=? AND active=TRUE ORDER BY updated_at DESC LIMIT 24
            """)) {
            q.setString(1, petId.toString());
            try (ResultSet rows = q.executeQuery()) {
                while (rows.next()) {
                    String text = rows.getString("memory_text");
                    if (MemoryInfluencePolicy.relevant(type, summary, text))
                        return Optional.of(new Cue(UUID.fromString(rows.getString("memory_id")),
                                rows.getString("provenance"), text.substring(0,
                                        Math.min(180, text.length()))));
                }
            }
        } catch (SQLException failure) {
            // Recall is optional for local behavior. A database fault must not stall capture.
            return Optional.empty();
        }
        return Optional.empty();
    }
}
