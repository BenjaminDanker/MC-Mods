package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetPlacement;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.PlacementState;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded JSON codec shared by the internal service and gameplay clients. */
public final class PetWireCodec {
    public static final int DEFAULT_MAX_JSON_CHARS = 131_072;

    private static final Set<String> SNAPSHOT_FIELDS = Set.of("pet", "sleeping", "aiAccessEnabled");
    private static final Set<String> PET_FIELDS = Set.of(
            "petId", "ownerUuid", "name", "appearance", "traits", "mood", "placement",
            "recordVersion", "createdAt", "updatedAt");
    private static final Set<String> APPEARANCE_FIELDS = Set.of(
            "species", "variantId", "scale", "appearanceSeed");
    private static final Set<String> TRAIT_FIELDS = Set.of(
            "curiosity", "boldness", "playfulness", "expressiveness", "independence",
            "attachment", "trust", "security", "relationshipSummary", "summaryVersion",
            "updatedAt");
    private static final Set<String> MOOD_FIELDS = Set.of(
            "content", "excited", "anxious", "tired", "lastDecayAt", "updatedAt");
    private static final Set<String> HELD_FIELDS = Set.of("state");
    private static final Set<String> PLACED_FIELDS = Set.of(
            "state", "backendId", "dimensionId", "position", "entityUuid");
    private static final Set<String> TRANSFERRING_FIELDS = Set.of(
            "state", "transferId", "sourceBackendId", "sourceEntityUuid",
            "destinationBackendId", "startedAt", "expiresAt");
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");

    private final AppearanceRules appearanceRules;
    private final int maxJsonChars;

    public PetWireCodec(AppearanceRules appearanceRules) {
        this(appearanceRules, DEFAULT_MAX_JSON_CHARS);
    }

    public PetWireCodec(AppearanceRules appearanceRules, int maxJsonChars) {
        this.appearanceRules = Objects.requireNonNull(appearanceRules, "appearanceRules");
        if (maxJsonChars < 1) {
            throw new IllegalArgumentException("maxJsonChars must be positive");
        }
        this.maxJsonChars = maxJsonChars;
    }

    public String encodeSnapshot(PetSnapshotWire snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        JsonObject root = new JsonObject();
        root.add("pet", writePet(snapshot.pet()));
        root.addProperty("sleeping", snapshot.sleeping());
        root.addProperty("aiAccessEnabled", snapshot.aiAccessEnabled());
        String encoded = root.toString();
        requireBound(encoded);
        return encoded;
    }

    public PetSnapshotWire decodeSnapshot(String json) {
        JsonObject root = parseObject(json, "snapshot");
        requireOnly(root, SNAPSHOT_FIELDS, "snapshot");
        return new PetSnapshotWire(
                readPet(requireObject(root, "pet")),
                requireBoolean(root, "sleeping"),
                requireBoolean(root, "aiAccessEnabled"));
    }

    private JsonObject writePet(Pet pet) {
        JsonObject root = new JsonObject();
        root.addProperty("petId", pet.petId().toString());
        root.addProperty("ownerUuid", pet.ownerUuid().toString());
        root.addProperty("name", pet.name());
        root.add("appearance", writeAppearance(pet.appearance()));
        root.add("traits", writeTraits(pet.traits()));
        root.add("mood", writeMood(pet.mood()));
        root.add("placement", writePlacement(pet.placement()));
        root.addProperty("recordVersion", pet.recordVersion());
        root.addProperty("createdAt", pet.createdAt().toString());
        root.addProperty("updatedAt", pet.updatedAt().toString());
        return root;
    }

    private Pet readPet(JsonObject root) {
        requireOnly(root, PET_FIELDS, "pet");
        return new Pet(
                requireUuid(root, "petId"),
                requireUuid(root, "ownerUuid"),
                requireString(root, "name"),
                readAppearance(requireObject(root, "appearance")),
                readTraits(requireObject(root, "traits")),
                readMood(requireObject(root, "mood")),
                readPlacement(requireObject(root, "placement")),
                requireLong(root, "recordVersion"),
                requireInstant(root, "createdAt"),
                requireInstant(root, "updatedAt"));
    }

    private static JsonObject writeAppearance(PetAppearance appearance) {
        JsonObject root = new JsonObject();
        root.addProperty("species", appearance.species().name());
        root.addProperty("variantId", appearance.variantId().value());
        root.addProperty("scale", appearance.scale());
        if (appearance.appearanceSeed().isPresent()) {
            root.addProperty("appearanceSeed", appearance.appearanceSeed().getAsLong());
        } else {
            root.add("appearanceSeed", null);
        }
        return root;
    }

    private PetAppearance readAppearance(JsonObject root) {
        requireOnly(root, APPEARANCE_FIELDS, "appearance");
        PetSpecies species = requireEnum(root, "species", PetSpecies.class);
        OptionalLong seed = optionalLong(root, "appearanceSeed");
        return PetAppearance.create(
                species,
                requireString(root, "variantId"),
                requireDouble(root, "scale"),
                seed.isPresent() ? seed.getAsLong() : null,
                appearanceRules);
    }

    private static JsonObject writeTraits(PetTraits traits) {
        JsonObject root = new JsonObject();
        root.addProperty("curiosity", traits.curiosity());
        root.addProperty("boldness", traits.boldness());
        root.addProperty("playfulness", traits.playfulness());
        root.addProperty("expressiveness", traits.expressiveness());
        root.addProperty("independence", traits.independence());
        root.addProperty("attachment", traits.attachment());
        root.addProperty("trust", traits.trust());
        root.addProperty("security", traits.security());
        root.addProperty("relationshipSummary", traits.relationshipSummary());
        root.addProperty("summaryVersion", traits.summaryVersion());
        root.addProperty("updatedAt", traits.updatedAt().toString());
        return root;
    }

    private static PetTraits readTraits(JsonObject root) {
        requireOnly(root, TRAIT_FIELDS, "traits");
        return new PetTraits(
                requireInt(root, "curiosity"),
                requireInt(root, "boldness"),
                requireInt(root, "playfulness"),
                requireInt(root, "expressiveness"),
                requireInt(root, "independence"),
                requireInt(root, "attachment"),
                requireInt(root, "trust"),
                requireInt(root, "security"),
                requireString(root, "relationshipSummary"),
                requireLong(root, "summaryVersion"),
                requireInstant(root, "updatedAt"));
    }

    private static JsonObject writeMood(PetMood mood) {
        JsonObject root = new JsonObject();
        root.addProperty("content", mood.content());
        root.addProperty("excited", mood.excited());
        root.addProperty("anxious", mood.anxious());
        root.addProperty("tired", mood.tired());
        root.addProperty("lastDecayAt", mood.lastDecayAt().toString());
        root.addProperty("updatedAt", mood.updatedAt().toString());
        return root;
    }

    private static PetMood readMood(JsonObject root) {
        requireOnly(root, MOOD_FIELDS, "mood");
        return new PetMood(
                requireInt(root, "content"),
                requireInt(root, "excited"),
                requireInt(root, "anxious"),
                requireInt(root, "tired"),
                requireInstant(root, "lastDecayAt"),
                requireInstant(root, "updatedAt"));
    }

    private static JsonObject writePlacement(PetPlacement placement) {
        JsonObject root = new JsonObject();
        root.addProperty("state", placement.state().name());
        if (placement instanceof PlacedPlacement placed) {
            root.addProperty("backendId", placed.backendId().value());
            root.addProperty("dimensionId", placed.dimensionId().toString());
            root.add("position", writePosition(placed.position()));
            if (placed.entityUuid().isPresent()) {
                root.addProperty("entityUuid", placed.entityUuid().orElseThrow().toString());
            } else {
                root.add("entityUuid", null);
            }
        } else if (placement instanceof TransferringPlacement transferring) {
            TransferMetadata transfer = transferring.transfer();
            root.addProperty("transferId", transfer.transferId().toString());
            root.addProperty("sourceBackendId", transfer.sourceBackendId().value());
            root.addProperty("sourceEntityUuid", transfer.sourceEntityUuid().toString());
            root.addProperty("destinationBackendId", transfer.destinationBackendId().value());
            root.addProperty("startedAt", transfer.startedAt().toString());
            root.addProperty("expiresAt", transfer.expiresAt().toString());
        }
        return root;
    }

    private static PetPlacement readPlacement(JsonObject root) {
        PlacementState state = requireEnum(root, "state", PlacementState.class);
        return switch (state) {
            case HELD -> {
                requireOnly(root, HELD_FIELDS, "held placement");
                yield HeldPlacement.INSTANCE;
            }
            case PLACED -> {
                requireOnly(root, PLACED_FIELDS, "placed placement");
                BackendId backend = new BackendId(requireString(root, "backendId"));
                DimensionId dimension = DimensionId.parse(requireString(root, "dimensionId"));
                WorldPosition position = readPosition(requireObject(root, "position"));
                Optional<UUID> entityId = optionalUuid(root, "entityUuid");
                yield entityId.isPresent()
                        ? PlacedPlacement.materialized(
                                backend, dimension, position, entityId.orElseThrow())
                        : PlacedPlacement.virtualized(backend, dimension, position);
            }
            case TRANSFERRING -> {
                requireOnly(root, TRANSFERRING_FIELDS, "transferring placement");
                yield new TransferringPlacement(new TransferMetadata(
                        requireUuid(root, "transferId"),
                        new BackendId(requireString(root, "sourceBackendId")),
                        requireUuid(root, "sourceEntityUuid"),
                        new BackendId(requireString(root, "destinationBackendId")),
                        requireInstant(root, "startedAt"),
                        requireInstant(root, "expiresAt")));
            }
        };
    }

    private static JsonObject writePosition(WorldPosition position) {
        JsonObject root = new JsonObject();
        root.addProperty("x", position.x());
        root.addProperty("y", position.y());
        root.addProperty("z", position.z());
        return root;
    }

    private static WorldPosition readPosition(JsonObject root) {
        requireOnly(root, POSITION_FIELDS, "position");
        return new WorldPosition(
                requireDouble(root, "x"),
                requireDouble(root, "y"),
                requireDouble(root, "z"));
    }

    private JsonObject parseObject(String json, String context) {
        Objects.requireNonNull(json, "json");
        requireBound(json);
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException(context + " must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException failure) {
            throw new PetWireFormatException("Malformed " + context + " JSON", failure);
        }
    }

    private void requireBound(String json) {
        if (json.length() > maxJsonChars) {
            throw new PetWireFormatException("Pet JSON exceeds " + maxJsonChars + " characters");
        }
    }

    private static void requireOnly(JsonObject root, Set<String> expected, String context) {
        if (!root.keySet().equals(expected)) {
            throw new PetWireFormatException(
                    context + " fields must be exactly " + expected + "; got " + root.keySet());
        }
    }

    private static JsonObject requireObject(JsonObject root, String field) {
        JsonElement value = require(root, field);
        if (!value.isJsonObject()) {
            throw new PetWireFormatException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requireString(JsonObject root, String field) {
        JsonElement value = require(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean requireBoolean(JsonObject root, String field) {
        JsonElement value = require(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new PetWireFormatException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static long requireLong(JsonObject root, String field) {
        JsonPrimitive value = requireNumber(root, field);
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new PetWireFormatException(field + " must be an exact integer", failure);
        }
    }

    private static int requireInt(JsonObject root, String field) {
        try {
            return Math.toIntExact(requireLong(root, field));
        } catch (ArithmeticException failure) {
            throw new PetWireFormatException(field + " is outside the integer range", failure);
        }
    }

    private static double requireDouble(JsonObject root, String field) {
        double value = requireNumber(root, field).getAsDouble();
        if (!Double.isFinite(value)) {
            throw new PetWireFormatException(field + " must be finite");
        }
        return value;
    }

    private static JsonPrimitive requireNumber(JsonObject root, String field) {
        JsonElement value = require(root, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(field + " must be a number");
        }
        return value.getAsJsonPrimitive();
    }

    private static UUID requireUuid(JsonObject root, String field) {
        return parseUuid(requireString(root, field), field);
    }

    private static Optional<UUID> optionalUuid(JsonObject root, String field) {
        JsonElement value = require(root, field);
        return value.isJsonNull()
                ? Optional.empty()
                : Optional.of(parseUuid(requireString(root, field), field));
    }

    private static UUID parseUuid(String encoded, String field) {
        try {
            UUID value = UUID.fromString(encoded);
            if (!value.toString().equals(encoded)) {
                throw new IllegalArgumentException("UUID is not canonical lowercase text");
            }
            return value;
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " must be a canonical UUID", failure);
        }
    }

    private static OptionalLong optionalLong(JsonObject root, String field) {
        JsonElement value = require(root, field);
        return value.isJsonNull()
                ? OptionalLong.empty()
                : OptionalLong.of(requireLong(root, field));
    }

    private static Instant requireInstant(JsonObject root, String field) {
        try {
            return Instant.parse(requireString(root, field));
        } catch (DateTimeParseException failure) {
            throw new PetWireFormatException(field + " must be an ISO-8601 UTC instant", failure);
        }
    }

    private static <E extends Enum<E>> E requireEnum(
            JsonObject root,
            String field,
            Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, requireString(root, field));
        } catch (IllegalArgumentException failure) {
            throw new PetWireFormatException(field + " is not a supported value", failure);
        }
    }

    private static JsonElement require(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null || value.isJsonNull()) {
            throw new PetWireFormatException(field + " is required");
        }
        return value;
    }
}
