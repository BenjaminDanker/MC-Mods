package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded presence JSON. Unknown fields and non-canonical UUIDs are rejected. */
public final class PetPresenceWireCodec {
    private static final Set<String> FIELDS =
            Set.of("ownerUuid", "online", "absenceSessionId", "occurredAt");

    public String encode(PetPresenceWireRequest request) {
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", request.ownerUuid().toString());
        root.addProperty("online", request.online());
        request.absenceSessionId().ifPresentOrElse(
                value -> root.addProperty("absenceSessionId", value.toString()),
                () -> root.add("absenceSessionId", null));
        root.addProperty("occurredAt", request.occurredAt().toString());
        return root.toString();
    }

    public PetPresenceWireRequest decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 1_024) {
            throw new PetWireFormatException("Presence payload is empty or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw new PetWireFormatException("Presence payload fields are invalid");
            }
            JsonObject root = parsed.getAsJsonObject();
            UUID owner = uuid(root.get("ownerUuid"));
            JsonElement onlineValue = root.get("online");
            if (!onlineValue.isJsonPrimitive() || !onlineValue.getAsJsonPrimitive().isBoolean()) {
                throw new PetWireFormatException("online must be boolean");
            }
            boolean online = onlineValue.getAsBoolean();
            JsonElement absenceValue = root.get("absenceSessionId");
            Optional<UUID> absence = absenceValue.isJsonNull()
                    ? Optional.empty() : Optional.of(uuid(absenceValue));
            JsonElement occurredValue = root.get("occurredAt");
            if (!occurredValue.isJsonPrimitive() || !occurredValue.getAsJsonPrimitive().isString()) {
                throw new PetWireFormatException("occurredAt must be a string");
            }
            return new PetPresenceWireRequest(
                    owner, online, absence, Instant.parse(occurredValue.getAsString()));
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException("Presence payload is malformed", failure);
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
