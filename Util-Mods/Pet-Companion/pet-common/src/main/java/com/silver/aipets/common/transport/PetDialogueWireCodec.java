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
            "requestId", "sessionId", "ownerUuid", "petId", "backendId", "dimensionId", "message");
    private static final Set<String> RESULT_FIELDS = Set.of(
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
        return root.toString();
    }

    public PetDialogueWireRequest decodeRequest(String encoded) {
        JsonObject root = object(encoded, REQUEST_FIELDS, 4_096, "request");
        return new PetDialogueWireRequest(
                uuid(root, "requestId"), uuid(root, "sessionId"), uuid(root, "ownerUuid"),
                uuid(root, "petId"), new BackendId(text(root, "backendId")),
                text(root, "dimensionId"), text(root, "message"));
    }

    public String encode(PetDialogueWireResult result) {
        JsonObject root = new JsonObject();
        root.addProperty("requestId", result.requestId().toString());
        root.addProperty("sessionId", result.sessionId().toString());
        root.addProperty("petId", result.petId().toString());
        root.addProperty("status", result.status().name());
        root.addProperty("message", result.message());
        return root.toString();
    }

    public PetDialogueWireResult decodeResult(String encoded) {
        JsonObject root = object(encoded, RESULT_FIELDS, 4_096, "result");
        PetDialogueWireResult.Status status;
        try {
            status = PetDialogueWireResult.Status.valueOf(text(root, "status"));
        } catch (RuntimeException malformed) {
            throw new PetWireFormatException("Invalid dialogue status", malformed);
        }
        return new PetDialogueWireResult(
                uuid(root, "requestId"), uuid(root, "sessionId"), uuid(root, "petId"),
                status, text(root, "message"));
    }

    private static JsonObject object(String encoded, Set<String> fields, int maximum, String name) {
        if (encoded == null || encoded.isBlank() || encoded.length() > maximum) {
            throw new PetWireFormatException(name + " payload is empty or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(fields)) {
                throw new PetWireFormatException(name + " fields are invalid");
            }
            return parsed.getAsJsonObject();
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException(name + " payload is malformed", failure);
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
}
