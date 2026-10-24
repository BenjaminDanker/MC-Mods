package com.silver.aipets.service.dialogue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Optional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versioned durable pet tendencies. Only repeated, cited consolidation evidence can add a cue. */
public final class PetSelfModel {
    private static final Pattern PATTERN = Pattern.compile(
            "(?i)^pet (?:often|repeatedly) (likes|enjoys|prefers|avoids|dislikes|returns to|investigates) ([a-z0-9 ,'-]{3,100})[.!]?$ ".strip());
    private static final Gson GSON = new Gson();
    private PetSelfModel() { }
    public static Optional<Update> fromConsolidatedEvidence(String text, int distinctEvents, int distinctDays) {
        if (distinctEvents < 3 || distinctDays < 2 || text == null) return Optional.empty();
        Matcher m=PATTERN.matcher(text.strip());
        if(!m.matches()) return Optional.empty();
        String verb=m.group(1).toLowerCase(Locale.ROOT), subject=m.group(2).strip().toLowerCase(Locale.ROOT);
        String category=switch(verb) {
            case "avoids","dislikes" -> "dislikes";
            case "investigates" -> "interests";
            case "returns to" -> "activities";
            case "prefers" -> "activities";
            default -> "likes";
        };
        return Optional.of(new Update(category, subject));
    }
    public static String add(String priorJson, Update update) {
        return add(priorJson, update, null);
    }

    /** {@code at} stamps the cue as freshly reinforced, which is what keeps it from fading. */
    public static String add(String priorJson, Update update, Instant at) {
        JsonObject root;
        try { root=priorJson==null||priorJson.isBlank()?new JsonObject():GSON.fromJson(priorJson,JsonObject.class); }
        catch(RuntimeException invalid) { root=new JsonObject(); }
        for(String field:new String[]{"likes","dislikes","interests","activities","avoidances","goals","voice"})
            if(!root.has(field)||!root.get(field).isJsonArray()) root.add(field,new JsonArray());
        if(!root.has("pending")||!root.get("pending").isJsonArray()) root.add("pending",new JsonArray());
        String category=update.category().equals("dislikes")?"avoidances":update.category();
        return addStable(root,category,update.value(),at) ? GSON.toJson(root) : priorJson;
    }

    /** Adds one versioned, explicitly provisional inference without changing stable cues. */
    public static String recordCandidate(String priorJson, DialogueSelfModelCandidate candidate,
            UUID interactionId, Instant supportedAt, boolean ownerReportedPreference) {
        return recordCandidate(priorJson, candidate, interactionId, supportedAt, ownerReportedPreference, false);
    }

    /**
     * {@code strong} marks a high-emotion moment (the pet's own reaction was at maximum strength).
     * Like a flashbulb memory, one such cited event is enough to promote the cue right away;
     * ordinary evidence still needs three interactions on two days.
     */
    public static String recordCandidate(String priorJson, DialogueSelfModelCandidate candidate,
            UUID interactionId, Instant supportedAt, boolean ownerReportedPreference, boolean strong) {
        String recorded = record(priorJson, candidate, interactionId, supportedAt, ownerReportedPreference, strong);
        if (java.util.Objects.equals(recorded, priorJson)) return recorded;
        recorded = reinforce(recorded, candidate, supportedAt);
        return strong ? promoteEligible(recorded, supportedAt) : recorded;
    }

    /** New evidence for a cue the pet already holds refreshes it, so it is not forgotten. */
    private static String reinforce(String json, DialogueSelfModelCandidate candidate, Instant at) {
        JsonObject root = parse(json);
        String category = candidate.category();
        if (category.isEmpty() || !root.has(category) || !root.get(category).isJsonArray()) return json;
        for (JsonElement value : root.getAsJsonArray(category)) {
            if (value.isJsonPrimitive() && value.getAsString().equalsIgnoreCase(candidate.value())) {
                return markSeen(root, category, candidate.value(), at) ? GSON.toJson(root) : json;
            }
        }
        return json;
    }

    private static String record(String priorJson, DialogueSelfModelCandidate candidate,
            UUID interactionId, Instant supportedAt, boolean ownerReportedPreference, boolean strong) {
        if (!candidate.meaningful() || ownerReportedPreference || candidate.supportingEventIds().isEmpty()) return priorJson;
        JsonObject root = parse(priorJson);
        ensureArrays(root);
        JsonArray pending = root.getAsJsonArray("pending");
        JsonObject matching = null;
        for (JsonElement element : pending) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (candidate.kind().name().equals(item.get("kind").getAsString())
                    && candidate.value().equalsIgnoreCase(item.get("value").getAsString())) {
                matching = item; break;
            }
        }
        if (matching == null) {
            if (pending.size() >= 20) pending.remove(0);
            matching = new JsonObject();
            matching.addProperty("kind", candidate.kind().name());
            matching.addProperty("value", candidate.value());
            matching.addProperty("source", "INFERENCE");
            matching.addProperty("confidence", candidate.confidence());
            matching.add("interaction_ids", new JsonArray());
            matching.add("days", new JsonArray());
            matching.add("evidence", new JsonArray());
            pending.add(matching);
        }
        if (!matching.has("evidence") || !matching.get("evidence").isJsonArray())
            matching.add("evidence", new JsonArray());
        JsonArray evidence = matching.getAsJsonArray("evidence");
        String day = supportedAt.atZone(ZoneOffset.UTC).toLocalDate().toString();
        boolean addedEvidence = false;
        for (UUID eventId : candidate.supportingEventIds()) {
            boolean duplicateEvent = false;
            for (JsonElement item : evidence) {
                if (item.isJsonObject() && item.getAsJsonObject().has("event_id")
                        && eventId.toString().equals(item.getAsJsonObject().get("event_id").getAsString())) {
                    duplicateEvent = true;
                    break;
                }
            }
            if (duplicateEvent) continue;
            JsonObject source = new JsonObject();
            source.addProperty("event_id", eventId.toString());
            source.addProperty("interaction_id", interactionId.toString());
            source.addProperty("day", day);
            if (strong) source.addProperty("strong", true);
            evidence.add(source);
            addedEvidence = true;
        }
        if (!addedEvidence) return priorJson;
        JsonArray ids = matching.getAsJsonArray("interaction_ids");
        boolean duplicate = false;
        for (JsonElement id : ids) if (interactionId.toString().equals(id.getAsString())) duplicate = true;
        if (!duplicate) ids.add(interactionId.toString());
        JsonArray days = matching.getAsJsonArray("days");
        boolean seenDay = false;
        for (JsonElement date : days) if (day.equals(date.getAsString())) seenDay = true;
        if (!seenDay) days.add(day);
        matching.addProperty("confidence", Math.min(.75,
                Math.max(matching.get("confidence").getAsDouble(), candidate.confidence())));
        return GSON.toJson(root);
    }

    /** Promotes only evidence repeated in distinct interactions across distinct UTC days. */
    public static String promoteEligible(String priorJson) {
        return promoteEligible(priorJson, null);
    }

    public static String promoteEligible(String priorJson, Instant at) {
        if (priorJson == null || priorJson.isBlank()) return priorJson;
        JsonObject root = parse(priorJson);
        if (!root.has("pending") || !root.get("pending").isJsonArray()
                || root.getAsJsonArray("pending").isEmpty()) return priorJson;
        ensureArrays(root);
        JsonArray pending = root.getAsJsonArray("pending");
        JsonArray remaining = new JsonArray();
        for (JsonElement element : pending) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (hasPromotionEvidence(item)) {
                String category = DialogueSelfModelCandidate.Kind.valueOf(item.get("kind").getAsString())
                        .name().toLowerCase(Locale.ROOT);
                category = switch (category) {
                    case "preference" -> "likes";
                    case "interest" -> "interests";
                    case "avoidance" -> "avoidances";
                    case "goal" -> "goals";
                    default -> "";
                };
                if (!category.isEmpty()) addStable(root, category, item.get("value").getAsString(), at);
            } else remaining.add(element);
        }
        root.add("pending", remaining);
        return GSON.toJson(root);
    }

    private static boolean hasPromotionEvidence(JsonObject candidate) {
        if (!candidate.has("evidence") || !candidate.get("evidence").isJsonArray()) return false;
        JsonArray evidence = candidate.getAsJsonArray("evidence");
        java.util.Set<String> events = new java.util.HashSet<>();
        java.util.Set<String> interactions = new java.util.HashSet<>();
        java.util.Set<String> days = new java.util.HashSet<>();
        boolean strong = false;
        for (JsonElement element : evidence) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (item.has("strong") && item.get("strong").getAsBoolean()) strong = true;
            if (item.has("event_id")) events.add(item.get("event_id").getAsString());
            if (item.has("interaction_id")) interactions.add(item.get("interaction_id").getAsString());
            if (item.has("day")) days.add(item.get("day").getAsString());
        }
        return strong || events.size() >= 3 && interactions.size() >= 3 && days.size() >= 2;
    }

    private static JsonObject parse(String json) {
        try { return json == null || json.isBlank() ? new JsonObject() : GSON.fromJson(json, JsonObject.class); }
        catch (RuntimeException invalid) { return new JsonObject(); }
    }
    private static void ensureArrays(JsonObject root) {
        for(String field:new String[]{"likes","dislikes","interests","activities","avoidances","goals","voice","pending"})
            if(!root.has(field)||!root.get(field).isJsonArray()) root.add(field,new JsonArray());
    }
    /** Cues that contradict each other cannot both stand; the older one becomes a dated change. */
    private static final java.util.Map<String, java.util.List<String>> OPPOSITES = java.util.Map.of(
            "likes", java.util.List.of("avoidances", "dislikes"),
            "interests", java.util.List.of("avoidances", "dislikes"),
            "activities", java.util.List.of("avoidances"),
            "avoidances", java.util.List.of("likes", "interests", "activities"),
            "dislikes", java.util.List.of("likes", "interests", "activities"));
    private static final java.util.Map<String, String> USED_TO = java.util.Map.of(
            "likes", "like", "interests", "be curious about", "activities", "do",
            "avoidances", "avoid", "dislikes", "avoid");
    static final int FADE_DAYS = 90;
    static final int CHANGE_FADE_DAYS = 60;
    static final int PENDING_FADE_DAYS = 30;
    private static final java.util.List<String> FADING = java.util.List.of(
            "likes", "dislikes", "interests", "activities", "avoidances", "goals", "changed");

    /** Returns whether the model changed. */
    private static boolean addStable(JsonObject root, String category, String value, Instant at) {
        boolean changed = false;
        JsonArray values = root.getAsJsonArray(category);
        boolean present = false;
        for (JsonElement current : values) if (current.isJsonPrimitive()
                && current.getAsString().equalsIgnoreCase(value)) present = true;
        if (!present) {
            for (String other : OPPOSITES.getOrDefault(category, java.util.List.of())) {
                if (!root.has(other) || !root.get(other).isJsonArray()) continue;
                JsonArray kept = new JsonArray();
                boolean dropped = false;
                for (JsonElement current : root.getAsJsonArray(other)) {
                    if (current.isJsonPrimitive() && current.getAsString().equalsIgnoreCase(value)) dropped = true;
                    else kept.add(current);
                }
                if (dropped) {
                    root.add(other, kept);
                    noteChange(root, "You used to " + USED_TO.get(other) + " " + value + ", but that has changed.", at);
                }
            }
            if (values.size() >= 12) values.remove(0);
            values.add(value);
            changed = true;
        }
        return markSeen(root, category, value, at) || changed;
    }

    private static void noteChange(JsonObject root, String text, Instant at) {
        if (!root.has("changed") || !root.get("changed").isJsonArray()) root.add("changed", new JsonArray());
        JsonArray changes = root.getAsJsonArray("changed");
        for (JsonElement existing : changes) if (existing.getAsString().equals(text)) return;
        if (changes.size() >= 6) changes.remove(0);
        changes.add(text);
        markSeen(root, "changed", text, at);
    }

    private static boolean markSeen(JsonObject root, String category, String value, Instant at) {
        if (at == null) return false;
        JsonObject seen = root.has("seen") && root.get("seen").isJsonObject()
                ? root.getAsJsonObject("seen") : new JsonObject();
        String key = category + ":" + value.toLowerCase(Locale.ROOT);
        String day = at.atZone(ZoneOffset.UTC).toLocalDate().toString();
        boolean same = seen.has(key) && seen.get(key).getAsString().equals(day);
        seen.addProperty(key, day);
        root.add("seen", seen);
        return !same;
    }

    /**
     * Sleep tidies the self-model like it tidies memories: a taste nobody has reinforced for
     * three months fades, an unrepeated inference expires after a month, and a note about a
     * change fades after two. Characters and voice are who the pet is and never fade.
     * Returns the same instance when nothing changed.
     */
    public static String fade(String priorJson, Instant now) {
        if (priorJson == null || priorJson.isBlank()) return priorJson;
        JsonObject root = parse(priorJson);
        JsonObject seen = root.has("seen") && root.get("seen").isJsonObject()
                ? root.getAsJsonObject("seen") : new JsonObject();
        java.time.LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        boolean changed = false;
        for (String category : FADING) {
            if (!root.has(category) || !root.get(category).isJsonArray()) continue;
            int limit = category.equals("changed") ? CHANGE_FADE_DAYS : FADE_DAYS;
            JsonArray kept = new JsonArray();
            boolean dropped = false;
            for (JsonElement item : root.getAsJsonArray(category)) {
                String key = category + ":" + item.getAsString().toLowerCase(Locale.ROOT);
                if (!seen.has(key)) {
                    seen.addProperty(key, today.toString());
                    changed = true;
                    kept.add(item);
                } else if (java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(seen.get(key).getAsString()), today) > limit) {
                    seen.remove(key);
                    dropped = true;
                } else kept.add(item);
            }
            if (dropped) { root.add(category, kept); changed = true; }
        }
        if (root.has("pending") && root.get("pending").isJsonArray()) {
            JsonArray kept = new JsonArray();
            boolean dropped = false;
            for (JsonElement element : root.getAsJsonArray("pending")) {
                if (lastEvidenceDay(element).map(day -> java.time.temporal.ChronoUnit.DAYS.between(day, today)
                        > PENDING_FADE_DAYS).orElse(false)) dropped = true;
                else kept.add(element);
            }
            if (dropped) { root.add("pending", kept); changed = true; }
        }
        if (!changed) return priorJson;
        root.add("seen", seen);
        return GSON.toJson(root);
    }

    private static Optional<java.time.LocalDate> lastEvidenceDay(JsonElement pending) {
        if (!pending.isJsonObject() || !pending.getAsJsonObject().has("days")
                || !pending.getAsJsonObject().get("days").isJsonArray()) return Optional.empty();
        java.time.LocalDate latest = null;
        for (JsonElement day : pending.getAsJsonObject().getAsJsonArray("days")) {
            java.time.LocalDate value = java.time.LocalDate.parse(day.getAsString());
            if (latest == null || value.isAfter(latest)) latest = value;
        }
        return Optional.ofNullable(latest);
    }

    /** A sleep's whole self-model tidy-up: promote what earned it, then let the stale fade. */
    public static String settle(String priorJson, Instant now) {
        return fade(promoteEligible(priorJson, now), now);
    }

    public record Update(String category,String value) { }
}
