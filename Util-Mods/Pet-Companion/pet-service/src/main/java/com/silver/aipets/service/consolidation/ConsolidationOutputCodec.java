package com.silver.aipets.service.consolidation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.service.memory.MemoryImportance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exact-field structured output parser; semantic grounding is validated separately. */
public final class ConsolidationOutputCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "memory_cards", "relationship_summary", "trait_deltas");
    private static final Set<String> CARD_FIELDS = Set.of(
            "text", "importance", "source_event_ids",
            "emotion_tags", "entity_tags", "location_tags");

    public ConsolidationModelOutput decode(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(ROOT_FIELDS)) {
                throw new IllegalArgumentException("Unexpected consolidation output fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            List<ConsolidationCardProposal> cards = cards(requiredArray(root, "memory_cards"));
            String summary = requiredString(root, "relationship_summary");
            Map<TraitName, Integer> deltas = traitDeltas(requiredObject(root, "trait_deltas"));
            return new ConsolidationModelOutput(cards, summary, deltas);
        } catch (RuntimeException malformed) {
            throw new ConsolidationOutputException(
                    "Model response failed strict consolidation schema validation", malformed);
        }
    }

    private static List<ConsolidationCardProposal> cards(JsonArray values) {
        List<ConsolidationCardProposal> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(CARD_FIELDS)) {
                throw new IllegalArgumentException("Unexpected memory-card fields");
            }
            JsonObject card = value.getAsJsonObject();
            result.add(new ConsolidationCardProposal(
                    requiredString(card, "text"),
                    MemoryImportance.valueOf(requiredString(card, "importance")
                            .toUpperCase(Locale.ROOT)),
                    uuids(requiredArray(card, "source_event_ids")),
                    strings(requiredArray(card, "emotion_tags")),
                    strings(requiredArray(card, "entity_tags")),
                    strings(requiredArray(card, "location_tags"))));
        }
        return result;
    }

    private static Map<TraitName, Integer> traitDeltas(JsonObject object) {
        Set<String> expected = java.util.Arrays.stream(TraitName.values())
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException("Trait fields do not match schema");
        }
        Map<TraitName, Integer> result = new EnumMap<>(TraitName.class);
        for (TraitName trait : TraitName.values()) {
            JsonElement value = object.get(trait.name().toLowerCase(Locale.ROOT));
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Trait delta must be an integer");
            }
            int delta = value.getAsInt();
            if (value.getAsDouble() != delta || delta < -100 || delta > 100) {
                throw new IllegalArgumentException("Trait delta is outside schema bounds");
            }
            result.put(trait, delta);
        }
        return result;
    }

    private static List<UUID> uuids(JsonArray values) {
        List<UUID> result = new ArrayList<>();
        for (JsonElement value : values) {
            result.add(UUID.fromString(requiredArrayString(value)));
        }
        if (new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("Source event IDs must be unique");
        }
        return result;
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> result = new HashSet<>();
        for (JsonElement value : values) {
            result.add(requiredArrayString(value));
        }
        if (result.size() != values.size()) {
            throw new IllegalArgumentException("Tags must be unique");
        }
        return result;
    }

    private static String requiredArrayString(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Array entry must be a string");
        }
        return value.getAsString();
    }

    private static JsonArray requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.getAsString();
    }
}
