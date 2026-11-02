package com.silver.aipets.service.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.experience.PetDecisionPlanner;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.sleep.PetSleepService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** Server-derived, idempotent experience ingestion. SQL placement verifies awareness. */
public final class PetExperienceHttpHandler implements HttpHandler {
    private static final Set<String> TYPES = Set.of("ARRIVED_TOGETHER", "OWNER_RETURNED",
            "OWNER_RETURNED_AFTER_ABSENCE",
            "NEARBY_THREAT", "SURVIVED_DANGER", "NEW_AREA", "DIRECT_INTERACTION",
            "ACTION_COMPLETED", "OWNER_ACTION");
    private final DataSource db;
    private final PetRepository pets;
    private final byte[] authorization;
    private final PetDecisionPlanner planner;
    private final SubscriptionAccess access;
    private final PetSleepService sleep;

    public PetExperienceHttpHandler(DataSource db, PetRepository pets, String token,
            PetDecisionPlanner planner, SubscriptionAccess access) {
        this(db, pets, token, planner, access, null);
    }

    public PetExperienceHttpHandler(DataSource db, PetRepository pets, String token,
            PetDecisionPlanner planner, SubscriptionAccess access, PetSleepService sleep) {
        this.db = db;
        this.pets = pets;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.planner = planner;
        this.access = access;
        this.sleep = sleep;
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !MessageDigest.isEqual(authorization,
                    auth.getBytes(StandardCharsets.UTF_8))) { reply(exchange, 401); return; }
            if (!"POST".equals(exchange.getRequestMethod())
                    || !"/v1/experiences".equals(exchange.getRequestURI().getRawPath())) {
                reply(exchange, 404); return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(2049);
            if (bytes.length > 2048) { reply(exchange, 413); return; }
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) { reply(exchange, 400); return; }
            JsonObject body = parsed.getAsJsonObject();
            UUID petId = UUID.fromString(requiredText(body, "petId"));
            UUID ownerId = UUID.fromString(requiredText(body, "ownerUuid"));
            UUID entityId = UUID.fromString(requiredText(body, "entityUuid"));
            UUID eventId = UUID.fromString(requiredText(body, "eventId"));
            String backend = requiredText(body, "backend");
            String dimension = requiredText(body, "dimension");
            var location = body.has("location") ? java.util.Optional.of(
                    com.silver.aipets.common.transport.SpatialLocationCodec.decode(body.getAsJsonObject("location")))
                    : java.util.Optional.<com.silver.aipets.common.transport.SpatialLocation>empty();
            if (location.isPresent() && (!location.get().server().equals(backend)
                    || !location.get().dimension().equals(dimension))) { reply(exchange, 400); return; }
            String type = requiredText(body, "type");
            String summary = requiredText(body, "summary").strip();
            Instant occurred = Instant.parse(requiredText(body, "occurredAt"));
            if (!TYPES.contains(type) || summary.isBlank() || summary.length() > 500
                    || backend.length() > 128 || dimension.length() > 191
                    || Math.abs(Duration.between(occurred, Instant.now()).toMinutes()) > 5) {
                reply(exchange, 400); return;
            }
            Pet pet = pets.findById(petId).orElse(null);
            if (pet == null || !pet.ownerUuid().equals(ownerId)
                    || !(pet.placement() instanceof PlacedPlacement placed)
                    || !placed.isMaterialized()
                    || !placed.entityUuid().orElseThrow().equals(entityId)
                    || !placed.backendId().value().equals(backend)
                    || !placed.dimensionId().toString().equals(dimension)) {
                reply(exchange, 409); return;
            }
            // The first response may have been lost after SQL committed the decision.
            // Replay only the same source event, including its original utterance.
            if (planner != null) {
                var replay = planner.replay(petId, eventId, Instant.now());
                if (replay.isPresent()) { decision(exchange, replay.orElseThrow()); return; }
            }
            if (duplicate(petId, type, summary, occurred)) { reply(exchange, 204); return; }
            String importance = switch (type) {
                case "SURVIVED_DANGER", "ARRIVED_TOGETHER" -> "HIGH";
                case "NEARBY_THREAT", "OWNER_RETURNED", "OWNER_RETURNED_AFTER_ABSENCE", "NEW_AREA" -> "MEDIUM";
                case "OWNER_ACTION" -> summary.startsWith("Owner defeated ")
                        ? "MEDIUM" : "LOW";
                default -> "LOW";
            };
            int inserted;
            try (Connection connection = db.getConnection(); PreparedStatement insert =
                    connection.prepareStatement("""
                        INSERT IGNORE INTO pet_events
                        (event_id, pet_id, owner_uuid, event_type, occurred_at, source_server,
                         source_dimension, importance, summary, short_term_expires_at,
                         prompt_eligible, consolidation_status, created_at, location_json)
                        VALUES (?,?,?,?,?,?,?,?,?,?,TRUE,'PENDING',?,?)
                        """)) {
                insert.setString(1, eventId.toString());
                insert.setString(2, petId.toString());
                insert.setString(3, ownerId.toString());
                insert.setString(4, type);
                insert.setObject(5, utc(occurred));
                insert.setString(6, backend);
                insert.setString(7, dimension);
                insert.setString(8, importance);
                insert.setString(9, summary);
                insert.setObject(10, utc(occurred.plus(Duration.ofHours(2))));
                insert.setObject(11, utc(Instant.now()));
                insert.setString(12, location.map(value ->
                        com.silver.aipets.common.transport.SpatialLocationCodec.encode(value).toString()).orElse(null));
                inserted = insert.executeUpdate();
            }
            if (inserted == 0) { reply(exchange, 204); return; }
            if (sleep != null) sleep.recordActivity(petId, occurred, switch (type) {
                case "SURVIVED_DANGER" -> 8;
                case "NEARBY_THREAT", "NEW_AREA", "ARRIVED_TOGETHER" -> 5;
                case "ACTION_COMPLETED" -> 2;
                default -> 1;
            });
            if (planner == null || (!type.equals("OWNER_ACTION")
                    && !novel(petId, eventId, type, summary))) {
                reply(exchange, 204); return;
            }
            var decision = planner.consider(pet, eventId, type, summary, Instant.now(),
                    access.canAdopt(ownerId));
            if (decision.isEmpty()) { reply(exchange, 204); return; }
            decision(exchange, decision.orElseThrow());
        } catch (IllegalArgumentException | JsonParseException
                | java.time.format.DateTimeParseException malformed) {
            reply(exchange, 400);
        } catch (IllegalStateException unavailable) {
            reply(exchange, 503);
        } catch (SQLException failure) {
            reply(exchange, 503);
        } finally { exchange.close(); }
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String requiredText(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or invalid " + name);
        }
        return value.getAsString();
    }

    private boolean novel(UUID petId, UUID current, String type, String summary) throws SQLException {
        if (type.equals("OWNER_RETURNED_AFTER_ABSENCE")) return true;
        if (!Set.of("ARRIVED_TOGETHER", "NEW_AREA", "SURVIVED_DANGER").contains(type))
            return false;
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT event_id FROM pet_events WHERE pet_id=? AND event_type=?
                AND summary=? AND event_id<>? LIMIT 1
                """)) {
            q.setString(1,petId.toString()); q.setString(2,type);
            q.setString(3,summary); q.setString(4,current.toString());
            try (var rows=q.executeQuery()) { return !rows.next(); }
        }
    }

    private boolean duplicate(UUID petId, String type, String summary, Instant occurred)
            throws SQLException {
        Duration window = switch (type) {
                case "NEW_AREA", "ARRIVED_TOGETHER" -> Duration.ofDays(36500);
                case "OWNER_ACTION" -> Duration.ofMinutes(5);
            case "SURVIVED_DANGER", "NEARBY_THREAT" -> Duration.ofMinutes(10);
            case "OWNER_RETURNED" -> Duration.ofMinutes(5);
            case "OWNER_RETURNED_AFTER_ABSENCE" -> Duration.ofMinutes(30);
            default -> Duration.ofSeconds(30);
        };
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT event_id FROM pet_events WHERE pet_id=? AND event_type=?
                    AND summary=? AND occurred_at>=? LIMIT 1
                """)) {
            q.setString(1,petId.toString()); q.setString(2,type);
            q.setString(3,summary); q.setObject(4,utc(occurred.minus(window)));
            try (var rows=q.executeQuery()) { return rows.next(); }
        }
    }

    private static void reply(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private static void decision(HttpExchange exchange,
            PetDecisionPlanner.Decision value) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("intent", value.intent());
        response.addProperty("expiresAt", value.expiresAt().toString());
        if (value.utterance() != null) response.addProperty("utterance", value.utterance());
        if (value.memoryId() != null)
            response.addProperty("memoryId", value.memoryId().toString());
        byte[] result = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, result.length);
        exchange.getResponseBody().write(result);
    }
}
