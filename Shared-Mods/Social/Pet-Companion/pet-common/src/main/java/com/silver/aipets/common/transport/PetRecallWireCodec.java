package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.WorldPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict bounded JSON for monthly recall and its spawn-failure compensation. */
public final class PetRecallWireCodec {
    private static final Set<String> RECALL_FIELDS = Set.of(
            "ownerUuid", "expectedVersion", "destinationBackendId",
            "destinationDimensionId", "position", "entityUuid", "occurredAt");
    private static final Set<String> COMPENSATE_FIELDS = Set.of(
            "expectedVersion", "destinationBackendId", "entityUuid", "occurredAt");
    private static final Set<String> RESULT_FIELDS = Set.of(
            "status", "petSnapshot", "failure", "nextAvailableAt");
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");

    private final PetWireCodec petCodec;

    public PetRecallWireCodec(PetWireCodec petCodec) {
        this.petCodec = Objects.requireNonNull(petCodec, "petCodec");
    }

    public String encodeRecall(PetTransitions.Recall command) {
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", command.ownerUuid().toString());
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("destinationBackendId", command.destinationBackendId().value());
        root.addProperty("destinationDimensionId", command.destinationDimensionId().toString());
        root.add("position", position(command.position()));
        root.addProperty("entityUuid", command.entityUuid().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.Recall decodeRecall(String json) {
        JsonObject root = parse(json, "recall request", RECALL_FIELDS);
        return new PetTransitions.Recall(
                uuid(root, "ownerUuid"), integer(root, "expectedVersion"),
                new BackendId(string(root, "destinationBackendId")),
                DimensionId.parse(string(root, "destinationDimensionId")),
                position(object(root, "position")), uuid(root, "entityUuid"),
                instant(root, "occurredAt"));
    }

    public String encodeCompensation(PetTransitions.CompensateRecallFailure command) {
        JsonObject root = new JsonObject();
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("destinationBackendId", command.destinationBackendId().value());
        root.addProperty("entityUuid", command.entityUuid().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.CompensateRecallFailure decodeCompensation(String json) {
        JsonObject root = parse(json, "recall compensation request", COMPENSATE_FIELDS);
        return new PetTransitions.CompensateRecallFailure(
                integer(root, "expectedVersion"),
                new BackendId(string(root, "destinationBackendId")),
                uuid(root, "entityUuid"), instant(root, "occurredAt"));
    }

    public String encodeResult(PetRecallWireResult result) {
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        if (result.pet().isPresent()) {
            root.addProperty("petSnapshot", petCodec.encodeSnapshot(
                    new PetSnapshotWire(result.pet().orElseThrow(), false)));
        } else {
            root.add("petSnapshot", null);
        }
        result.failure().ifPresentOrElse(
                value -> root.addProperty("failure", value.name()),
                () -> root.add("failure", null));
        result.nextAvailableAt().ifPresentOrElse(
                value -> root.addProperty("nextAvailableAt", value.toString()),
                () -> root.add("nextAvailableAt", null));
        return bounded(root.toString());
    }

    public PetRecallWireResult decodeResult(String json) {
        JsonObject root = parse(json, "recall result", RESULT_FIELDS);
        JsonElement pet = required(root, "petSnapshot");
        JsonElement failure = required(root, "failure");
        JsonElement next = required(root, "nextAvailableAt");
        return new PetRecallWireResult(
                enumValue(required(root, "status"), "status", PetRecallWireStatus.class),
                pet.isJsonNull() ? Optional.empty() : Optional.of(
                        petCodec.decodeSnapshot(stringValue(pet, "petSnapshot")).pet()),
                failure.isJsonNull() ? Optional.empty() : Optional.of(
                        enumValue(failure, "failure", TransitionFailure.class)),
                next.isJsonNull() ? Optional.empty() : Optional.of(
                        parseInstant(stringValue(next, "nextAvailableAt"), "nextAvailableAt")));
    }

    private static JsonObject position(WorldPosition value) {
        JsonObject root = new JsonObject();
        root.addProperty("x", value.x());
        root.addProperty("y", value.y());
        root.addProperty("z", value.z());
        return root;
    }

    private static WorldPosition position(JsonObject root) {
        requireOnly(root, POSITION_FIELDS, "position");
        return new WorldPosition(number(root, "x"), number(root, "y"), number(root, "z"));
    }

    private static JsonObject parse(String json, String context, Set<String> fields) {
        Objects.requireNonNull(json, "json");
        if (json.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException(context + " is too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException(context + " must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            requireOnly(root, fields, context);
            return root;
        } catch (JsonParseException failure) {
            throw new PetWireFormatException("Malformed " + context, failure);
        }
    }

    private static void requireOnly(JsonObject root, Set<String> fields, String context) {
        if (!root.keySet().equals(fields)) {
            throw new PetWireFormatException(context + " fields must be exactly " + fields);
        }
    }

    private static JsonElement required(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null) throw new PetWireFormatException(field + " is required");
        return value;
    }

    private static JsonObject object(JsonObject root, String field) {
        JsonElement value = required(root, field);
        if (!value.isJsonObject()) throw new PetWireFormatException(field + " must be an object");
        return value.getAsJsonObject();
    }

    private static String string(JsonObject root, String field) {
        return stringValue(required(root, field), field);
    }

    private static String stringValue(JsonElement value, String field) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static JsonPrimitive numeric(JsonObject root, String field) {
        JsonElement value = required(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(field + " must be numeric");
        }
        return value.getAsJsonPrimitive();
    }

    private static long integer(JsonObject root, String field) {
        try {
            return new BigDecimal(numeric(root, field).getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new PetWireFormatException(field + " must be an exact integer", failure);
        }
    }

    private static double number(JsonObject root, String field) {
        double value = numeric(root, field).getAsDouble();
        if (!Double.isFinite(value)) throw new PetWireFormatException(field + " must be finite");
        return value;
    }

    private static UUID uuid(JsonObject root, String field) {
        try {
            UUID value = UUID.fromString(string(root, field));
            if (!value.toString().equals(string(root, field))) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " must be a canonical UUID", failure);
        }
    }

    private static Instant instant(JsonObject root, String field) {
        return parseInstant(string(root, field), field);
    }

    private static Instant parseInstant(String encoded, String field) {
        try {
            return Instant.parse(encoded);
        } catch (DateTimeParseException failure) {
            throw new PetWireFormatException(field + " must be an ISO-8601 instant", failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonElement value, String field, Class<E> type) {
        try {
            return Enum.valueOf(type, stringValue(value, field));
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " is unsupported", failure);
        }
    }

    private static String bounded(String value) {
        if (value.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException("Recall JSON is too large");
        }
        return value;
    }
}
