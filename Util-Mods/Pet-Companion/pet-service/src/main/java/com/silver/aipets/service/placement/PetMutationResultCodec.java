package com.silver.aipets.service.placement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.transport.PetSnapshotWire;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetWireFormatException;

import java.util.Objects;
import java.util.Set;

/** Strict bounded representation stored in the idempotency ledger and reused by HTTP. */
public final class PetMutationResultCodec {
    private static final Set<String> FIELDS = Set.of("status", "petSnapshot", "failure");

    private final PetWireCodec petCodec;

    public PetMutationResultCodec(PetWireCodec petCodec) {
        this.petCodec = Objects.requireNonNull(petCodec, "petCodec");
    }

    public String encode(PetMutationResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        if (result.pet().isPresent()) {
            root.addProperty("petSnapshot", petCodec.encodeSnapshot(
                    new PetSnapshotWire(result.pet().orElseThrow(), false)));
        } else {
            root.add("petSnapshot", null);
        }
        if (result.failure().isPresent()) {
            root.addProperty("failure", result.failure().orElseThrow().name());
        } else {
            root.add("failure", null);
        }
        String encoded = root.toString();
        if (encoded.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException("Mutation JSON is too large");
        }
        return encoded;
    }

    public PetMutationResult decode(String json) {
        Objects.requireNonNull(json, "json");
        if (json.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException("Mutation JSON is too large");
        }
        final JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException("Mutation JSON must be an object");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException failure) {
            throw new PetWireFormatException("Malformed mutation JSON", failure);
        }
        if (!root.keySet().equals(FIELDS)) {
            throw new PetWireFormatException("Mutation fields must be exactly " + FIELDS);
        }
        PetMutationStatus status = parseEnum(root, "status", PetMutationStatus.class);
        JsonElement petValue = require(root, "petSnapshot");
        JsonElement failureValue = require(root, "failure");
        return switch (status) {
            case APPLIED -> {
                requireNull(failureValue, "failure");
                yield PetMutationResult.applied(petCodec.decodeSnapshot(
                        requireString(petValue, "petSnapshot")).pet());
            }
            case REJECTED -> PetMutationResult.rejected(
                    petCodec.decodeSnapshot(requireString(petValue, "petSnapshot")).pet(),
                    parseEnum(failureValue, "failure", TransitionFailure.class));
            case NOT_FOUND -> {
                requireNull(petValue, "petSnapshot");
                requireNull(failureValue, "failure");
                yield PetMutationResult.notFound();
            }
            case CONCURRENT_MODIFICATION -> {
                requireNull(failureValue, "failure");
                yield PetMutationResult.concurrentModification(
                        petCodec.decodeSnapshot(requireString(petValue, "petSnapshot")).pet());
            }
        };
    }

    private static JsonElement require(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null) {
            throw new PetWireFormatException(field + " is required");
        }
        return value;
    }

    private static String requireString(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static void requireNull(JsonElement value, String field) {
        if (!value.isJsonNull()) {
            throw new PetWireFormatException(field + " must be null");
        }
    }

    private static <E extends Enum<E>> E parseEnum(
            JsonObject root, String field, Class<E> enumType) {
        return parseEnum(require(root, field), field, enumType);
    }

    private static <E extends Enum<E>> E parseEnum(
            JsonElement value, String field, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, requireString(value, field));
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " is not a supported value", failure);
        }
    }
}
