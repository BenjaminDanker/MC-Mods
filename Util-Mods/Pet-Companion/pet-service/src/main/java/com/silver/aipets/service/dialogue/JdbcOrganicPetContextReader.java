package com.silver.aipets.service.dialogue;

import com.google.gson.JsonParser;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads bounded affect, cue-matched self-model hints, and at most two live threads. */
public final class JdbcOrganicPetContextReader {
    private final DataSource db;
    public JdbcOrganicPetContextReader(DataSource db) { this.db = java.util.Objects.requireNonNull(db); }
    public OrganicPetContext read(UUID petId, String backend, String dimension,
            String message, Instant now) {
        try (Connection c = db.getConnection()) {
            BehavioralAffect affect;
            java.util.Optional<OwnerInterpretation> ownerInterpretation;
            List<String> cues = new ArrayList<>();
            try (PreparedStatement q = c.prepareStatement("""
                    SELECT m.affect_valence,m.affect_valence_intensity,m.affect_alertness,
                      m.affect_alertness_intensity,m.affect_social_stance,m.affect_social_intensity,
                      m.affect_trigger,m.affect_target_uuid,m.affect_updated_at,m.affect_event_id,t.self_model_json
                      ,m.owner_intent_guess,m.owner_emotion_guess,m.owner_interpretation_confidence,
                      m.owner_interpretation_updated_at,
                      (SELECT COUNT(*) FROM pet_events later
                        WHERE later.pet_id=m.pet_id AND later.event_type='DIALOGUE'
                          AND later.created_at>(SELECT source.created_at FROM pet_events source
                            WHERE source.event_id=m.owner_interpretation_event_id)) AS interpretation_later_interactions
                    FROM pet_mood m JOIN pet_traits t ON t.pet_id=m.pet_id WHERE m.pet_id=?
                    """)) {
                q.setString(1, petId.toString());
                try (ResultSet r=q.executeQuery()) {
                    if (!r.next()) return OrganicPetContext.empty(now);
                    LocalDateTime updated = r.getObject("affect_updated_at", LocalDateTime.class);
                    String target = r.getString("affect_target_uuid");
                    affect = new BehavioralAffect(r.getInt("affect_valence"),r.getInt("affect_valence_intensity"),
                            r.getInt("affect_alertness"),r.getInt("affect_alertness_intensity"),
                            r.getInt("affect_social_stance"),r.getInt("affect_social_intensity"),
                            r.getString("affect_trigger"),target==null?null:UUID.fromString(target),
                            updated==null?Instant.EPOCH:updated.toInstant(ZoneOffset.UTC),
                            r.getString("affect_event_id")==null?null:UUID.fromString(r.getString("affect_event_id"))).decay(now);
                    String json = r.getString("self_model_json");
                    if (json != null && !json.isBlank()) cues.addAll(selectSelfCues(json, message));
                    String intent=r.getString("owner_intent_guess"), emotion=r.getString("owner_emotion_guess");
                    LocalDateTime interpretedAt=r.getObject("owner_interpretation_updated_at",LocalDateTime.class);
                    if(interpretedAt!=null&&(intent!=null||emotion!=null)) ownerInterpretation=java.util.Optional.of(
                            new OwnerInterpretation(java.util.Optional.ofNullable(intent),java.util.Optional.ofNullable(emotion),
                                    r.getBigDecimal("owner_interpretation_confidence").doubleValue(),interpretedAt.toInstant(ZoneOffset.UTC)));
                    else ownerInterpretation=java.util.Optional.empty();
                    if (ownerInterpretation.isPresent() && !ownerInterpretation.orElseThrow().activeAt(
                            now, r.getLong("interpretation_later_interactions"))) {
                        ownerInterpretation=java.util.Optional.empty();
                    }
                }
            }
            List<OrganicPetContext.ThreadCue> threads = new ArrayList<>();
            try (PreparedStatement q = c.prepareStatement("""
                    SELECT thread_id,summary,state,location_cue,version,source_kind,source_event_id,
                           created_at,location_server,location_dimension
                    FROM pet_open_threads WHERE pet_id=? AND state IN ('OPEN','ACTIVE','PAUSED')
                      AND (expires_at IS NULL OR expires_at>?)
                      AND COALESCE(last_referenced_at,created_at)>=?
                      AND (location_server IS NULL OR (location_server=? AND location_dimension=?))
                    ORDER BY CASE state WHEN 'ACTIVE' THEN 0 WHEN 'OPEN' THEN 1 ELSE 2 END,
                      COALESCE(last_referenced_at,created_at) DESC LIMIT 12
                    """)) {
                q.setString(1, petId.toString()); q.setObject(2, utc(now));
                q.setObject(3, utc(now.minus(java.time.Duration.ofDays(30))));
                q.setString(4, backend); q.setString(5, dimension);
                try (ResultSet r=q.executeQuery()) { while (r.next()) {
                    String sourceEvent=r.getString("source_event_id");
                    threads.add(new OrganicPetContext.ThreadCue(
                            r.getString("thread_id"),r.getString("summary"),r.getString("state"),
                            r.getString("location_cue"),r.getLong("version"),r.getString("source_kind"),
                            sourceEvent==null?java.util.Optional.empty():java.util.Optional.of(UUID.fromString(sourceEvent)),
                            r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                            r.getString("location_server"),r.getString("location_dimension")));
                }}
            }
            String cueText=message.toLowerCase(java.util.Locale.ROOT);
            boolean explicitResume=cueText.matches("(?s).*(\\b(continue|resume|back to|where were we)\\b).*" );
            if (!explicitResume && !threads.isEmpty()) threads.removeIf(thread -> {
                String key=thread.summary().toLowerCase(java.util.Locale.ROOT);
                String location=thread.locationCue()==null?"":thread.locationCue().toLowerCase(java.util.Locale.ROOT);
                return !containsCueWord(cueText,key) && !containsCueWord(cueText,location);
            });

            if(threads.size()>2) threads.subList(2,threads.size()).clear();
            java.util.Optional<OrganicPetContext.IntentionCue> intention=java.util.Optional.empty();
            try(PreparedStatement q=c.prepareStatement("""
                    SELECT intent,motivation_memory_id,expires_at,decision_version FROM pet_decisions
                    WHERE pet_id=? AND expires_at>?
                    """)) {
                q.setString(1,petId.toString());q.setObject(2,utc(now));
                try(ResultSet r=q.executeQuery()) { if(r.next()) {
                    LocalDateTime expiry=r.getObject("expires_at",LocalDateTime.class);
                    intention=java.util.Optional.of(new OrganicPetContext.IntentionCue(r.getString("intent"),
                            r.getString("motivation_memory_id"),expiry.toInstant(ZoneOffset.UTC),r.getLong("decision_version")));
                }}
            }
            return new OrganicPetContext(affect,cues,threads,ownerInterpretation,intention);
        } catch (Exception e) { throw new IllegalStateException("Could not read organic pet context", e); }
    }
    static List<String> selectSelfCues(String json, String message) {
        try {
            var root=JsonParser.parseString(json).getAsJsonObject();
            String lower=message.toLowerCase(java.util.Locale.ROOT);
            List<String> result=new ArrayList<>();
            for (String key:List.of("likes","dislikes","interests","activities","avoidances","goals","voice")) {
                if (!root.has(key) || !root.get(key).isJsonArray()) continue;
                root.getAsJsonArray(key).forEach(item->{
                    if (!item.isJsonPrimitive() || item.getAsString().length()>120 || result.size()>=5) return;
                    String value=item.getAsString();
                    if (key.equals("voice") || sharesCue(lower,value)) result.add(key+":"+value);
                });
            }
            return result;
        } catch (RuntimeException ignored) { return List.of(); }
    }
    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at, ZoneOffset.UTC); }
    private static boolean containsCueWord(String message,String candidate) {
        if(candidate.isBlank()) return false;
        for(String word:candidate.split("[^a-z0-9]+"))
            if(word.length()>=3 && message.matches("(?s).*\\b"+java.util.regex.Pattern.quote(word)+"\\b.*")) return true;
        return false;
    }
    private static boolean sharesCue(String message,String candidate) {
        List<String> messageWords=java.util.Arrays.stream(message.split("[^a-z0-9]+"))
                .filter(word->word.length()>=4).toList();
        List<String> candidateWords=java.util.Arrays.stream(candidate.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word->word.length()>=4).toList();
        return messageWords.stream().anyMatch(left->candidateWords.stream().anyMatch(right->left.equals(right)
                || left.length()>=4&&right.startsWith(left) || right.length()>=4&&left.startsWith(right)));
    }
}
