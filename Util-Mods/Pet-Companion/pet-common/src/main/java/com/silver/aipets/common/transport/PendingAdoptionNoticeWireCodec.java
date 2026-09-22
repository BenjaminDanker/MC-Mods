package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Strict small codec for a pending adoption notification. */
public final class PendingAdoptionNoticeWireCodec {
    private static final Set<String> FIELDS = Set.of("intentId", "petName");

    public String encode(PendingAdoptionNoticeWire notice) {
        Objects.requireNonNull(notice, "notice");
        JsonObject object = new JsonObject();
        object.addProperty("intentId", notice.intentId().toString());
        object.addProperty("petName", notice.petName());
        return object.toString();
    }

    public PendingAdoptionNoticeWire decode(String json) {
        Objects.requireNonNull(json, "json");
        if (json.length() > 2_048) throw new PetWireFormatException("Adoption notice is too large");
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new PetWireFormatException("Adoption notice must be an object");
            JsonObject object = parsed.getAsJsonObject();
            if (!object.keySet().equals(FIELDS)) throw new PetWireFormatException("Unexpected adoption notice fields");
            JsonElement intent = object.get("intentId");
            JsonElement name = object.get("petName");
            if (!intent.isJsonPrimitive() || !intent.getAsJsonPrimitive().isString()
                    || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
                throw new PetWireFormatException("Adoption notice fields must be strings");
            }
            return new PendingAdoptionNoticeWire(UUID.fromString(intent.getAsString()), name.getAsString());
        } catch (RuntimeException failure) {
            if (failure instanceof PetWireFormatException wire) throw wire;
            throw new PetWireFormatException("Malformed adoption notice", failure);
        }
    }
}
