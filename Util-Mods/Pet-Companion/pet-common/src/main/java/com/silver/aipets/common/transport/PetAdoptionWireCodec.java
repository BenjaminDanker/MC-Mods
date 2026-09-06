package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.domain.PetSpecies;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict bounded adoption JSON layered on the existing pet snapshot codec. */
public final class PetAdoptionWireCodec {
    private static final Set<String> REQUEST_FIELDS = Set.of("ownerUuid", "species", "name");
    private static final Set<String> RESULT_FIELDS = Set.of("status", "snapshot");

    private final PetWireCodec petCodec;

    public PetAdoptionWireCodec(PetWireCodec petCodec) {
        this.petCodec = Objects.requireNonNull(petCodec, "petCodec");
    }

    public String encodeRequest(PetAdoptionWireRequest request) {
        Objects.requireNonNull(request, "request");
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", request.ownerUuid().toString());
        root.addProperty("species", request.species().name());
        root.addProperty("name", request.name());
        return root.toString();
    }

    public PetAdoptionWireRequest decodeRequest(String json) {
        JsonObject root = object(json, "adoption request");
        requireOnly(root, REQUEST_FIELDS);
        return new PetAdoptionWireRequest(
                UUID.fromString(string(root, "ownerUuid")),
                PetSpecies.valueOf(string(root, "species")),
                string(root, "name"));
    }

    public String encodeResult(PetAdoptionWireResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        root.add(
                "snapshot",
                result.pet()
                        .<JsonElement>map(pet -> JsonParser.parseString(petCodec.encodeSnapshot(
                                new PetSnapshotWire(pet, false, true))))
                        .orElse(com.google.gson.JsonNull.INSTANCE));
        return root.toString();
    }

    public PetAdoptionWireResult decodeResult(String json) {
        JsonObject root = object(json, "adoption result");
        requireOnly(root, RESULT_FIELDS);
        PetAdoptionWireStatus status = PetAdoptionWireStatus.valueOf(string(root, "status"));
        JsonElement snapshot = root.get("snapshot");
        Optional<com.silver.aipets.common.domain.Pet> pet = snapshot == null || snapshot.isJsonNull()
                ? Optional.empty()
                : Optional.of(petCodec.decodeSnapshot(snapshot.toString()).pet());
        return new PetAdoptionWireResult(status, pet);
    }

    private static JsonObject object(String json, String context) {
        Objects.requireNonNull(json, "json");
        if (json.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException(context + " is too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException(context + " must be an object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException malformed) {
            if (malformed instanceof PetWireFormatException wire) {
                throw wire;
            }
            throw new PetWireFormatException("Malformed " + context, malformed);
        }
    }

    private static void requireOnly(JsonObject root, Set<String> fields) {
        if (!root.keySet().equals(fields)) {
            throw new PetWireFormatException("Unexpected or missing adoption fields");
        }
    }

    private static String string(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(field + " must be a string");
        }
        return value.getAsString();
    }
}
