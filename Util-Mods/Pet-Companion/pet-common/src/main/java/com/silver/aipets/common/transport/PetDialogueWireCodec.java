package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.BackendId;

import java.util.Set;
import java.util.UUID;

/** Strict bounded codec shared by Fabric and the central service. */
public final class PetDialogueWireCodec {
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "requestId", "sessionId", "ownerUuid", "petId", "backendId", "dimensionId", "message", "scene");
    private static final Set<String> LEGACY_REQUEST_FIELDS = Set.of(
            "requestId", "sessionId", "ownerUuid", "petId", "backendId", "dimensionId", "message");
    private static final Set<String> SCENE_FIELDS = Set.of("petBiome", "ownerBiome",
            "proximity", "ownerVisible", "ownerActivity", "ownerHealth", "petActivity",
            "weather", "nearbyThreat");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "requestId", "sessionId", "petId", "status", "message", "action", "attention");
    private static final Set<String> LEGACY_RESULT_FIELDS = Set.of(
            "requestId", "sessionId", "petId", "status", "message");

    public String encode(PetDialogueWireRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("requestId", request.requestId().toString());
        root.addProperty("sessionId", request.sessionId().toString());
        root.addProperty("ownerUuid", request.ownerUuid().toString());
        root.addProperty("petId", request.petId().toString());
        root.addProperty("backendId", request.backendId().value());
        root.addProperty("dimensionId", request.dimensionId());
        root.addProperty("message", request.message());
        JsonObject scene = new JsonObject();
        scene.addProperty("petBiome", request.scene().petBiome());
        scene.addProperty("ownerBiome", request.scene().ownerBiome());
        scene.addProperty("proximity", request.scene().proximity());
        scene.addProperty("ownerVisible", request.scene().ownerVisible());
        scene.addProperty("ownerActivity", request.scene().ownerActivity());
        scene.addProperty("ownerHealth", request.scene().ownerHealth());
        scene.addProperty("petActivity", request.scene().petActivity());
        scene.addProperty("weather", request.scene().weather());
        scene.addProperty("nearbyThreat", request.scene().nearbyThreat());
        request.scene().location().ifPresent(location -> scene.add("location", SpatialLocationCodec.encode(location)));
        root.add("scene", scene);
        return root.toString();
    }

    public PetDialogueWireRequest decodeRequest(String encoded) {
        JsonObject root = object(encoded, REQUEST_FIELDS, LEGACY_REQUEST_FIELDS, 4_096, "request");
        return new PetDialogueWireRequest(
                uuid(root, "requestId"), uuid(root, "sessionId"), uuid(root, "ownerUuid"),
                uuid(root, "petId"), new BackendId(text(root, "backendId")),
                text(root, "dimensionId"), text(root, "message"),
                root.has("scene") ? scene(root.getAsJsonObject("scene")) : PetSceneSnapshot.unknown());
    }

    public String encode(PetDialogueWireResult result) {
        JsonObject root = new JsonObject();
        root.addProperty("requestId", result.requestId().toString());
        root.addProperty("sessionId", result.sessionId().toString());
        root.addProperty("petId", result.petId().toString());
        root.addProperty("status", result.status().name());
        root.addProperty("message", result.message());
        root.addProperty("action", result.action().name());
        root.addProperty("attention", result.attention().name());
        return root.toString();
    }

    public PetDialogueWireResult decodeResult(String encoded) {
        JsonObject root = object(encoded, RESULT_FIELDS, LEGACY_RESULT_FIELDS, 4_096, "result");
        PetDialogueWireResult.Status status;
        try {
            status = PetDialogueWireResult.Status.valueOf(text(root, "status"));
        } catch (RuntimeException malformed) {
            throw new PetWireFormatException("Invalid dialogue status", malformed);
        }
        return new PetDialogueWireResult(
                uuid(root, "requestId"), uuid(root, "sessionId"), uuid(root, "petId"),
                status, text(root, "message"),
                root.has("action") ? enumValue(PetDialogueAction.class, text(root, "action"))
                        : PetDialogueAction.NONE,
                root.has("attention") ? enumValue(PetDialogueAttention.class, text(root, "attention"))
                        : PetDialogueAttention.QUIET);
    }

    private static JsonObject object(String encoded, Set<String> fields, int maximum, String name) {
        return object(encoded, fields, fields, maximum, name);
    }

    private static JsonObject object(String encoded, Set<String> fields, Set<String> legacy,
            int maximum, String name) {
        if (encoded == null || encoded.isBlank() || encoded.length() > maximum) {
            throw new PetWireFormatException(name + " payload is empty or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonObject() || (!parsed.getAsJsonObject().keySet().equals(fields)
                    && !parsed.getAsJsonObject().keySet().equals(legacy))) {
                throw new PetWireFormatException(name + " fields are invalid");
            }
            return parsed.getAsJsonObject();
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException(name + " payload is malformed", failure);
        }
    }

    private static PetSceneSnapshot scene(JsonObject value) {
        Set<String> extended = new java.util.HashSet<>(SCENE_FIELDS);
        extended.add("location");
        if (value == null || (!value.keySet().equals(SCENE_FIELDS)
                && !value.keySet().equals(extended))) {
            throw new PetWireFormatException("scene fields are invalid");
        }
        try {
            return new PetSceneSnapshot(text(value, "petBiome"), text(value, "ownerBiome"),
                    text(value, "proximity"), value.get("ownerVisible").getAsBoolean(),
                    text(value, "ownerActivity"), text(value, "ownerHealth"),
                    text(value, "petActivity"), text(value, "weather"),
                    value.get("nearbyThreat").getAsBoolean(),
                    value.has("location") ? java.util.Optional.of(SpatialLocationCodec.decode(
                            value.getAsJsonObject("location"))) : java.util.Optional.empty());
        } catch (RuntimeException malformed) {
            throw new PetWireFormatException("scene is invalid", malformed);
        }
    }

    private static String text(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static UUID uuid(JsonObject root, String name) {
        String encoded = text(root, name);
        try {
            UUID value = UUID.fromString(encoded);
            if (!value.toString().equals(encoded)) throw new IllegalArgumentException("non-canonical UUID");
            return value;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException(name + " must be a canonical UUID", failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException failure) { throw new PetWireFormatException("Invalid enum " + type.getSimpleName(), failure); }
    }
}
