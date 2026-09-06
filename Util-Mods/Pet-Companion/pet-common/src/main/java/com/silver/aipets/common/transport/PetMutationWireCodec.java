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
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.WorldPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict bounded request/response JSON for authoritative placement mutations. */
public final class PetMutationWireCodec {
    private static final Set<String> RESULT_FIELDS = Set.of("status", "petSnapshot", "failure");
    private static final Set<String> PLACE_FIELDS = Set.of(
            "ownerUuid", "expectedVersion", "backendId", "dimensionId", "position",
            "entityUuid", "occurredAt");
    private static final Set<String> COMPENSATE_FIELDS = Set.of(
            "expectedVersion", "backendId", "entityUuid", "occurredAt");
    private static final Set<String> PICKUP_FIELDS = Set.of(
            "ownerUuid", "expectedVersion", "backendId", "dimensionId", "entityUuid",
            "ownerPosition", "entityPosition", "maximumDistance", "occurredAt");
    private static final Set<String> PREPARE_TRANSFER_FIELDS = Set.of(
            "ownerUuid", "expectedVersion", "sourceDimensionId", "ownerPosition",
            "entityPosition", "maximumDistance", "transfer");
    private static final Set<String> TRANSFER_FIELDS = Set.of(
            "transferId", "sourceBackendId", "sourceEntityUuid", "destinationBackendId",
            "startedAt", "expiresAt");
    private static final Set<String> COMPLETE_TRANSFER_FIELDS = Set.of(
            "expectedVersion", "transferId", "destinationBackendId", "destinationDimensionId",
            "position", "newEntityUuid", "occurredAt");
    private static final Set<String> EXPIRE_TRANSFER_FIELDS = Set.of(
            "expectedVersion", "transferId", "occurredAt");
    private static final Set<String> ADMIN_RECOVER_FIELDS = Set.of(
            "expectedVersion", "occurredAt");
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");

    private final PetWireCodec petCodec;

    public PetMutationWireCodec(PetWireCodec petCodec) {
        this.petCodec = Objects.requireNonNull(petCodec, "petCodec");
    }

    public String encodeResult(PetMutationWireResult result) {
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
        return bounded(root.toString());
    }

    public PetMutationWireResult decodeResult(String json) {
        JsonObject root = parse(json, "mutation result", RESULT_FIELDS);
        PetMutationWireStatus status = enumValue(root, "status", PetMutationWireStatus.class);
        JsonElement petValue = required(root, "petSnapshot");
        JsonElement failureValue = required(root, "failure");
        Optional<com.silver.aipets.common.domain.Pet> pet = petValue.isJsonNull()
                ? Optional.empty()
                : Optional.of(petCodec.decodeSnapshot(stringValue(petValue, "petSnapshot")).pet());
        Optional<TransitionFailure> failure = failureValue.isJsonNull()
                ? Optional.empty()
                : Optional.of(enumValue(failureValue, "failure", TransitionFailure.class));
        return new PetMutationWireResult(status, pet, failure);
    }

    public String encodePlace(PetTransitions.Place command) {
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", command.ownerUuid().toString());
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("backendId", command.backendId().value());
        root.addProperty("dimensionId", command.dimensionId().toString());
        root.add("position", position(command.position()));
        root.addProperty("entityUuid", command.entityUuid().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.Place decodePlace(String json) {
        JsonObject root = parse(json, "place request", PLACE_FIELDS);
        return new PetTransitions.Place(
                uuid(root, "ownerUuid"), integer(root, "expectedVersion"),
                new BackendId(string(root, "backendId")),
                DimensionId.parse(string(root, "dimensionId")),
                position(object(root, "position")), uuid(root, "entityUuid"),
                instant(root, "occurredAt"));
    }

    public String encodeCompensate(PetTransitions.CompensatePlaceFailure command) {
        JsonObject root = new JsonObject();
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("backendId", command.backendId().value());
        root.addProperty("entityUuid", command.entityUuid().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.CompensatePlaceFailure decodeCompensate(String json) {
        JsonObject root = parse(json, "compensation request", COMPENSATE_FIELDS);
        return new PetTransitions.CompensatePlaceFailure(
                integer(root, "expectedVersion"), new BackendId(string(root, "backendId")),
                uuid(root, "entityUuid"), instant(root, "occurredAt"));
    }

    public String encodePickup(PetTransitions.Pickup command) {
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", command.ownerUuid().toString());
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("backendId", command.backendId().value());
        root.addProperty("dimensionId", command.dimensionId().toString());
        root.addProperty("entityUuid", command.entityUuid().toString());
        root.add("ownerPosition", position(command.ownerPosition()));
        root.add("entityPosition", position(command.entityPosition()));
        root.addProperty("maximumDistance", command.maximumDistance());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.Pickup decodePickup(String json) {
        JsonObject root = parse(json, "pickup request", PICKUP_FIELDS);
        return new PetTransitions.Pickup(
                uuid(root, "ownerUuid"), integer(root, "expectedVersion"),
                new BackendId(string(root, "backendId")),
                DimensionId.parse(string(root, "dimensionId")), uuid(root, "entityUuid"),
                position(object(root, "ownerPosition")), position(object(root, "entityPosition")),
                number(root, "maximumDistance"), instant(root, "occurredAt"));
    }

    public String encodePrepareTransfer(PetTransitions.PrepareTransfer command) {
        Objects.requireNonNull(command, "command");
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", command.ownerUuid().toString());
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("sourceDimensionId", command.sourceDimensionId().toString());
        root.add("ownerPosition", position(command.ownerPosition()));
        root.add("entityPosition", position(command.entityPosition()));
        root.addProperty("maximumDistance", command.maximumDistance());
        root.add("transfer", transfer(command.transfer()));
        return bounded(root.toString());
    }

    public PetTransitions.PrepareTransfer decodePrepareTransfer(String json) {
        JsonObject root = parse(json, "prepare transfer request", PREPARE_TRANSFER_FIELDS);
        return new PetTransitions.PrepareTransfer(
                uuid(root, "ownerUuid"),
                integer(root, "expectedVersion"),
                DimensionId.parse(string(root, "sourceDimensionId")),
                position(object(root, "ownerPosition")),
                position(object(root, "entityPosition")),
                number(root, "maximumDistance"),
                transfer(object(root, "transfer")));
    }

    public String encodeCompleteTransfer(PetTransitions.CompleteTransfer command) {
        Objects.requireNonNull(command, "command");
        JsonObject root = new JsonObject();
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("transferId", command.transferId().toString());
        root.addProperty("destinationBackendId", command.destinationBackendId().value());
        root.addProperty("destinationDimensionId", command.destinationDimensionId().toString());
        root.add("position", position(command.position()));
        root.addProperty("newEntityUuid", command.newEntityUuid().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.CompleteTransfer decodeCompleteTransfer(String json) {
        JsonObject root = parse(json, "complete transfer request", COMPLETE_TRANSFER_FIELDS);
        return new PetTransitions.CompleteTransfer(
                integer(root, "expectedVersion"),
                uuid(root, "transferId"),
                new BackendId(string(root, "destinationBackendId")),
                DimensionId.parse(string(root, "destinationDimensionId")),
                position(object(root, "position")),
                uuid(root, "newEntityUuid"),
                instant(root, "occurredAt"));
    }

    public String encodeExpireTransfer(PetTransitions.ExpireTransfer command) {
        Objects.requireNonNull(command, "command");
        JsonObject root = new JsonObject();
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("transferId", command.transferId().toString());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.ExpireTransfer decodeExpireTransfer(String json) {
        JsonObject root = parse(json, "expire transfer request", EXPIRE_TRANSFER_FIELDS);
        return new PetTransitions.ExpireTransfer(
                integer(root, "expectedVersion"),
                uuid(root, "transferId"),
                instant(root, "occurredAt"));
    }

    public String encodeAdminRecover(PetTransitions.AdminRecover command) {
        Objects.requireNonNull(command, "command");
        JsonObject root = new JsonObject();
        root.addProperty("expectedVersion", command.expectedVersion());
        root.addProperty("occurredAt", command.occurredAt().toString());
        return bounded(root.toString());
    }

    public PetTransitions.AdminRecover decodeAdminRecover(String json) {
        JsonObject root = parse(json, "admin recovery request", ADMIN_RECOVER_FIELDS);
        return new PetTransitions.AdminRecover(
                integer(root, "expectedVersion"), instant(root, "occurredAt"));
    }

    private static JsonObject transfer(TransferMetadata transfer) {
        JsonObject root = new JsonObject();
        root.addProperty("transferId", transfer.transferId().toString());
        root.addProperty("sourceBackendId", transfer.sourceBackendId().value());
        root.addProperty("sourceEntityUuid", transfer.sourceEntityUuid().toString());
        root.addProperty("destinationBackendId", transfer.destinationBackendId().value());
        root.addProperty("startedAt", transfer.startedAt().toString());
        root.addProperty("expiresAt", transfer.expiresAt().toString());
        return root;
    }

    private static TransferMetadata transfer(JsonObject root) {
        requireOnly(root, TRANSFER_FIELDS, "transfer");
        return new TransferMetadata(
                uuid(root, "transferId"),
                new BackendId(string(root, "sourceBackendId")),
                uuid(root, "sourceEntityUuid"),
                new BackendId(string(root, "destinationBackendId")),
                instant(root, "startedAt"),
                instant(root, "expiresAt"));
    }

    private static JsonObject position(WorldPosition position) {
        JsonObject root = new JsonObject();
        root.addProperty("x", position.x());
        root.addProperty("y", position.y());
        root.addProperty("z", position.z());
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

    private static String bounded(String json) {
        if (json.length() > PetWireCodec.DEFAULT_MAX_JSON_CHARS) {
            throw new PetWireFormatException("Mutation JSON is too large");
        }
        return json;
    }

    private static void requireOnly(JsonObject root, Set<String> fields, String context) {
        if (!root.keySet().equals(fields)) {
            throw new PetWireFormatException(context + " fields must be exactly " + fields);
        }
    }

    private static JsonElement required(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null) {
            throw new PetWireFormatException(field + " is required");
        }
        return value;
    }

    private static JsonObject object(JsonObject root, String field) {
        JsonElement value = required(root, field);
        if (!value.isJsonObject()) {
            throw new PetWireFormatException(field + " must be an object");
        }
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

    private static long integer(JsonObject root, String field) {
        JsonPrimitive value = numeric(root, field);
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new PetWireFormatException(field + " must be an exact integer", failure);
        }
    }

    private static double number(JsonObject root, String field) {
        double value = numeric(root, field).getAsDouble();
        if (!Double.isFinite(value)) {
            throw new PetWireFormatException(field + " must be finite");
        }
        return value;
    }

    private static JsonPrimitive numeric(JsonObject root, String field) {
        JsonElement value = required(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(field + " must be numeric");
        }
        return value.getAsJsonPrimitive();
    }

    private static UUID uuid(JsonObject root, String field) {
        String encoded = string(root, field);
        try {
            UUID value = UUID.fromString(encoded);
            if (!value.toString().equals(encoded)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return value;
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " must be a canonical UUID", failure);
        }
    }

    private static Instant instant(JsonObject root, String field) {
        try {
            return Instant.parse(string(root, field));
        } catch (DateTimeParseException failure) {
            throw new PetWireFormatException(field + " must be an ISO-8601 instant", failure);
        }
    }

    private static <E extends Enum<E>> E enumValue(
            JsonObject root, String field, Class<E> enumType) {
        return enumValue(required(root, field), field, enumType);
    }

    private static <E extends Enum<E>> E enumValue(
            JsonElement value, String field, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, stringValue(value, field));
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " is not a supported value", failure);
        }
    }
}
