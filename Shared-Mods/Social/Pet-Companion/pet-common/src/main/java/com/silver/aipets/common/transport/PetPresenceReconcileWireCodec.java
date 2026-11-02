package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded codec for proxy-start presence reconciliation. */
public final class PetPresenceReconcileWireCodec {
    private static final Set<String> FIELDS = Set.of("onlineOwnerUuids", "occurredAt");

    public String encode(PetPresenceReconcileWireRequest request) {
        JsonObject root = new JsonObject();
        var owners = new com.google.gson.JsonArray();
        request.onlineOwnerUuids().stream().sorted().forEach(owner -> owners.add(owner.toString()));
        root.add("onlineOwnerUuids", owners);
        root.addProperty("occurredAt", request.occurredAt().toString());
        return root.toString();
    }

    public PetPresenceReconcileWireRequest decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 512 * 1_024) {
            throw new PetWireFormatException("Presence reconciliation payload is empty or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw new PetWireFormatException("Presence reconciliation fields are invalid");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement ownersValue = root.get("onlineOwnerUuids");
            if (!ownersValue.isJsonArray() || ownersValue.getAsJsonArray().size() > 10_000) {
                throw new PetWireFormatException("onlineOwnerUuids must be a bounded array");
            }
            Set<UUID> owners = new HashSet<>();
            for (JsonElement value : ownersValue.getAsJsonArray()) {
                UUID owner = uuid(value);
                if (!owners.add(owner)) {
                    throw new PetWireFormatException("onlineOwnerUuids contains a duplicate");
                }
            }
            JsonElement occurredValue = root.get("occurredAt");
            if (!occurredValue.isJsonPrimitive() || !occurredValue.getAsJsonPrimitive().isString()) {
                throw new PetWireFormatException("occurredAt must be a string");
            }
            return new PetPresenceReconcileWireRequest(
                    owners, Instant.parse(occurredValue.getAsString()));
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException("Presence reconciliation payload is malformed", failure);
        }
    }

    private static UUID uuid(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException("UUID field must be a string");
        }
        String encoded = value.getAsString();
        UUID parsed = UUID.fromString(encoded);
        if (!parsed.toString().equals(encoded)) {
            throw new PetWireFormatException("UUID field must be canonical lowercase");
        }
        return parsed;
    }
}
