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
        JsonObject root;
        try { root=priorJson==null||priorJson.isBlank()?new JsonObject():GSON.fromJson(priorJson,JsonObject.class); }
        catch(RuntimeException invalid) { root=new JsonObject(); }
        for(String field:new String[]{"likes","dislikes","interests","activities","avoidances","goals","voice"})
            if(!root.has(field)||!root.get(field).isJsonArray()) root.add(field,new JsonArray());
        if(!root.has("pending")||!root.get("pending").isJsonArray()) root.add("pending",new JsonArray());
        String category=update.category().equals("dislikes")?"avoidances":update.category();
        JsonArray values=root.getAsJsonArray(category);
        for(var value:values) if(value.isJsonPrimitive()&&value.getAsString().equalsIgnoreCase(update.value())) return priorJson;
        if(values.size()>=12) values.remove(0);
        values.add(update.value());
        return GSON.toJson(root);
    }

    /** Adds one versioned, explicitly provisional inference without changing stable cues. */
    public static String recordCandidate(String priorJson, DialogueSelfModelCandidate candidate,
            UUID interactionId, Instant supportedAt, boolean ownerReportedPreference) {
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
                if (!category.isEmpty()) addStable(root.getAsJsonArray(category), item.get("value").getAsString());
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
        for (JsonElement element : evidence) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (item.has("event_id")) events.add(item.get("event_id").getAsString());
            if (item.has("interaction_id")) interactions.add(item.get("interaction_id").getAsString());
            if (item.has("day")) days.add(item.get("day").getAsString());
        }
        return events.size() >= 3 && interactions.size() >= 3 && days.size() >= 2;
    }

    private static JsonObject parse(String json) {
        try { return json == null || json.isBlank() ? new JsonObject() : GSON.fromJson(json, JsonObject.class); }
        catch (RuntimeException invalid) { return new JsonObject(); }
    }
    private static void ensureArrays(JsonObject root) {
        for(String field:new String[]{"likes","dislikes","interests","activities","avoidances","goals","voice","pending"})
            if(!root.has(field)||!root.get(field).isJsonArray()) root.add(field,new JsonArray());
    }
    private static void addStable(JsonArray values, String value) {
        for (JsonElement current : values) if (current.isJsonPrimitive()
                && current.getAsString().equalsIgnoreCase(value)) return;
        if (values.size() >= 12) values.remove(0);
        values.add(value);
    }
    public record Update(String category,String value) { }
}
