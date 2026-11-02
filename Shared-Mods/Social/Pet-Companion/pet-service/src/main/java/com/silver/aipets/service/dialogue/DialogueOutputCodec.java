package com.silver.aipets.service.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strict no-extra-fields parser for the model's one structured response. */
public final class DialogueOutputCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "reply", "importance", "memory_candidate", "trait_deltas", "mood_deltas");
    private static final Set<String> COMPACT_FIELDS = Set.of(
            "reply", "action", "attention", "memory_candidate");
    private static final Set<String> COMPACT_AFFECT_FIELDS = Set.of(
            "reply", "action", "attention", "memory_candidate", "affect_delta");
    private static final Set<String> COMPACT_RELATIONSHIP_FIELDS = Set.of(
            "reply", "action", "attention", "memory_candidate", "affect_delta", "relationship_event");
    private static final Set<String> COMPACT_ORGANIC_FIELDS = Set.of(
            "reply", "action", "attention", "memory_candidate", "affect_delta", "affect_strength", "relationship_event");
    private static final Set<String> PRODUCTION_FIELDS = Set.of(
            "reply", "action", "attention", "memory_candidate", "affect_delta", "affect_strength", "relationship_event", "self_model_candidate", "owner_interpretation");

    public DialogueModelOutput decode(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || (!parsed.getAsJsonObject().keySet().equals(ROOT_FIELDS)
                    && !parsed.getAsJsonObject().keySet().equals(COMPACT_FIELDS)
                    && !parsed.getAsJsonObject().keySet().equals(COMPACT_AFFECT_FIELDS)
                    && !parsed.getAsJsonObject().keySet().equals(COMPACT_RELATIONSHIP_FIELDS)
                    && !parsed.getAsJsonObject().keySet().equals(COMPACT_ORGANIC_FIELDS)
                    && !parsed.getAsJsonObject().keySet().equals(PRODUCTION_FIELDS))) {
                throw new IllegalArgumentException("Unexpected structured-output fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            String reply = requiredString(root, "reply");
            JsonElement memory = root.get("memory_candidate");
            Optional<String> candidate = memory.isJsonNull() || memory.isJsonObject()
                    ? Optional.empty() : Optional.of(requiredString(root, "memory_candidate"));
            Optional<OwnerMemoryProposal> ownerMemory = memory.isJsonObject()
                    ? Optional.of(ownerMemory(memory.getAsJsonObject())) : Optional.empty();
            if (root.keySet().equals(COMPACT_FIELDS)
                    || root.keySet().equals(COMPACT_AFFECT_FIELDS)
                    || root.keySet().equals(COMPACT_RELATIONSHIP_FIELDS)
                    || root.keySet().equals(COMPACT_ORGANIC_FIELDS)
                    || root.keySet().equals(PRODUCTION_FIELDS)) {
                PetDialogueAction action = PetDialogueAction.valueOf(
                        requiredString(root, "action").toUpperCase(Locale.ROOT));
                PetDialogueAttention attention = PetDialogueAttention.valueOf(
                        requiredString(root, "attention").toUpperCase(Locale.ROOT));
                DialogueAffect affect = root.has("affect_delta")
                        ? DialogueAffect.valueOf(requiredString(root, "affect_delta")
                                .toUpperCase(Locale.ROOT)) : DialogueAffect.NONE;
                RelationshipSignal relationship = root.has("relationship_event")
                        ? relationshipSignal(root.getAsJsonObject("relationship_event"))
                        : RelationshipSignal.none();
                int affectStrength = root.has("affect_strength")
                        ? boundedInteger(root.get("affect_strength"), 0, 2) : (affect == DialogueAffect.NONE ? 0 : 1);
                DialogueSelfModelCandidate selfCandidate = root.has("self_model_candidate")
                        ? selfModelCandidate(root.getAsJsonObject("self_model_candidate"))
                        : DialogueSelfModelCandidate.none();
                OwnerInterpretationCandidate ownerInterpretation = root.has("owner_interpretation")
                        ? ownerInterpretation(root.getAsJsonObject("owner_interpretation"))
                        : OwnerInterpretationCandidate.none();
                return new DialogueModelOutput(reply, DialogueImportance.MEDIUM, candidate,
                        zeroes(TraitName.class), moodFromAffect(affect), action, attention, affect,
                        relationship, affectStrength, selfCandidate, ownerInterpretation, ownerMemory);
            }
            DialogueImportance importance = DialogueImportance.valueOf(
                    requiredString(root, "importance").toUpperCase(Locale.ROOT));
            Map<TraitName, Integer> traits = deltas(requiredObject(root, "trait_deltas"), TraitName.class);
            Map<MoodDimension, Integer> mood = deltas(requiredObject(root, "mood_deltas"), MoodDimension.class);
            return new DialogueModelOutput(reply, importance, candidate, traits, mood,
                    PetDialogueAction.NONE, PetDialogueAttention.QUIET);
        } catch (RuntimeException malformed) {
            throw new DialogueOutputException("Model response failed strict schema validation", malformed);
        }
    }

    private static OwnerMemoryProposal ownerMemory(JsonObject object) {
        if (!object.keySet().equals(Set.of("kind", "summary", "owner_quote", "replaces_memory_id", "thread_id", "thread_operation")))
            throw new IllegalArgumentException("Unexpected memory_candidate fields");
        return new OwnerMemoryProposal(OwnerMemoryProposal.Kind.valueOf(requiredString(object, "kind")),
                requiredString(object, "summary"), requiredString(object, "owner_quote"),
                optionalString(object.get("replaces_memory_id"), "replaces_memory_id").map(java.util.UUID::fromString),
                optionalString(object.get("thread_id"), "thread_id").map(java.util.UUID::fromString),
                OwnerMemoryProposal.Operation.valueOf(requiredString(object, "thread_operation")));
    }

    private static RelationshipSignal relationshipSignal(JsonObject object) {
        if (!object.keySet().equals(Set.of("type", "strength"))) {
            throw new IllegalArgumentException("Unexpected relationship_event fields");
        }
        RelationshipSignal.Type type = RelationshipSignal.Type.valueOf(
                requiredString(object, "type").toUpperCase(Locale.ROOT));
        JsonElement strength = object.get("strength");
        if (!strength.isJsonPrimitive() || !strength.getAsJsonPrimitive().isNumber()
                || strength.getAsDouble() != strength.getAsInt()) {
            throw new IllegalArgumentException("Relationship strength must be an integer");
        }
        int bounded = boundedInteger(strength, 0, 2);
        // Some providers emit a nonzero default strength for NONE. Treat that as
        // an explicit no-op instead of rejecting an otherwise complete reply.
        return type == RelationshipSignal.Type.NONE
                ? RelationshipSignal.none() : new RelationshipSignal(type, bounded);
    }

    private static int boundedInteger(JsonElement value, int minimum, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || value.getAsDouble() != value.getAsInt()) {
            throw new IllegalArgumentException("Affect strength must be an integer");
        }
        int result = value.getAsInt();
        if (result < minimum || result > maximum) throw new IllegalArgumentException("Affect strength is out of range");
        return result;
    }

    private static DialogueSelfModelCandidate selfModelCandidate(JsonObject object) {
        if (!object.keySet().equals(Set.of("kind", "value", "confidence")))
            throw new IllegalArgumentException("Unexpected self_model_candidate fields");
        DialogueSelfModelCandidate.Kind kind = DialogueSelfModelCandidate.parseKind(requiredString(object, "kind"));
        JsonElement value = object.get("value");
        String text;
        if (value == null || !value.isJsonNull() && !value.isJsonPrimitive())
            throw new IllegalArgumentException("Self-model candidate value must be a string or null");
        text = value.isJsonNull() ? "" : value.getAsString();
        JsonElement confidence = object.get("confidence");
        if (confidence == null || !confidence.isJsonPrimitive() || !confidence.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Self-model confidence must be numeric");
        return new DialogueSelfModelCandidate(kind, text, confidence.getAsDouble());
    }

    private static OwnerInterpretationCandidate ownerInterpretation(JsonObject object) {
        if (!object.keySet().equals(Set.of("intent", "emotion", "confidence")))
            throw new IllegalArgumentException("Unexpected owner_interpretation fields");
        Optional<String> intent = optionalString(object.get("intent"), "owner interpretation intent");
        Optional<String> emotion = optionalString(object.get("emotion"), "owner interpretation emotion");
        JsonElement confidence = object.get("confidence");
        if (confidence == null || !confidence.isJsonPrimitive()
                || !confidence.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Owner interpretation confidence must be numeric");
        return new OwnerInterpretationCandidate(intent, emotion, confidence.getAsDouble());
    }

    private static Optional<String> optionalString(JsonElement value, String name) {
        if (value == null || !value.isJsonNull() && !value.isJsonPrimitive())
            throw new IllegalArgumentException(name + " must be a string or null");
        if (value.isJsonNull()) return Optional.empty();
        if (!value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must be a string or null");
        String text = value.getAsString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static <E extends Enum<E>> Map<E, Integer> zeroes(Class<E> type) {
        Map<E, Integer> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) result.put(value, 0);
        return result;
    }

    private static Map<MoodDimension, Integer> moodFromAffect(DialogueAffect affect) {
        Map<MoodDimension, Integer> deltas = zeroes(MoodDimension.class);
        switch (affect) {
            case NONE -> { }
            case CALMER -> { deltas.put(MoodDimension.CONTENT, 1); deltas.put(MoodDimension.ANXIOUS, -2); }
            case BRIGHTER -> { deltas.put(MoodDimension.CONTENT, 1); deltas.put(MoodDimension.EXCITED, 2); }
            case WARY -> { deltas.put(MoodDimension.ANXIOUS, 2); deltas.put(MoodDimension.EXCITED, -1); }
            case TIRED -> { deltas.put(MoodDimension.TIRED, 2); deltas.put(MoodDimension.EXCITED, -1); }
        }
        return deltas;
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
