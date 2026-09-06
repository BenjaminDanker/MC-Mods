package com.silver.aipets.service.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strict no-extra-fields parser for the model's one structured response. */
public final class DialogueOutputCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "reply", "importance", "memory_candidate", "trait_deltas", "mood_deltas");

    public DialogueModelOutput decode(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(ROOT_FIELDS)) {
                throw new IllegalArgumentException("Unexpected structured-output fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            String reply = requiredString(root, "reply");
            DialogueImportance importance = DialogueImportance.valueOf(
                    requiredString(root, "importance").toUpperCase(Locale.ROOT));
            JsonElement memory = root.get("memory_candidate");
            Optional<String> candidate = memory.isJsonNull()
                    ? Optional.empty() : Optional.of(requiredString(root, "memory_candidate"));
            Map<TraitName, Integer> traits = deltas(
                    requiredObject(root, "trait_deltas"), TraitName.class);
            Map<MoodDimension, Integer> mood = deltas(
                    requiredObject(root, "mood_deltas"), MoodDimension.class);
            return new DialogueModelOutput(reply, importance, candidate, traits, mood);
        } catch (RuntimeException malformed) {
            throw new DialogueOutputException("Model response failed strict schema validation", malformed);
        }
    }

    private static <E extends Enum<E>> Map<E, Integer> deltas(
            JsonObject object, Class<E> type) {
        E[] constants = type.getEnumConstants();
        Set<String> expected = java.util.Arrays.stream(constants)
                .map(value -> value.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException("Delta fields do not match schema");
        }
        Map<E, Integer> result = new EnumMap<>(type);
        for (E value : constants) {
            JsonElement element = object.get(value.name().toLowerCase(Locale.ROOT));
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Delta must be an integer");
            }
            int delta = element.getAsInt();
            if (element.getAsDouble() != delta) {
                throw new IllegalArgumentException("Delta must be an exact integer");
            }
            result.put(value, delta);
        }
        return result;
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
