package com.silver.aipets.common.transport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict bounded codec for the authenticated admin dialogue-history projection. */
public final class DialogueHistoryWireCodec {
    private static final int MAX_JSON_CHARS = 96_000;
    private static final Set<String> ROOT = Set.of("ownerUuid", "petId", "petName", "conversations", "usage");
    private static final Set<String> CONVERSATION = Set.of(
            "occurredAt", "importance", "summary", "ownerText", "petReply");
    private static final Set<String> USAGE = Set.of(
            "createdAt", "operation", "model", "inputTokens", "cachedInputTokens", "outputTokens",
            "estimatedCost", "status", "context");
    private static final Set<String> CONTEXT = Set.of(
            "systemTokens", "identityTokens", "shortTermDbTokens", "longTermRelationalTokens",
            "longTermVectorTokens", "recentTurnsDbTokens", "gameContextTokens", "ownerInputTokens",
            "instructionTokens", "totalPromptTokens", "shortTermItems", "longTermRelationalItems",
            "longTermVectorItems", "recentTurnItems");

    public String encode(DialogueHistoryWireResult result) {
        JsonObject root = new JsonObject();
        root.addProperty("ownerUuid", result.ownerUuid().toString());
        root.addProperty("petId", result.petId().toString());
        root.addProperty("petName", result.petName());
        JsonArray conversations = new JsonArray();
        result.conversations().forEach(entry -> {
            JsonObject value = new JsonObject();
            value.addProperty("occurredAt", entry.occurredAt().toString());
            value.addProperty("importance", entry.importance());
            value.addProperty("summary", entry.summary());
            addNullable(value, "ownerText", entry.ownerText());
            addNullable(value, "petReply", entry.petReply());
            conversations.add(value);
        });
        root.add("conversations", conversations);
        JsonArray usage = new JsonArray();
        result.usage().forEach(entry -> {
            JsonObject value = new JsonObject();
            value.addProperty("createdAt", entry.createdAt().toString());
            value.addProperty("operation", entry.operation());
            value.addProperty("model", entry.model());
            value.addProperty("inputTokens", entry.inputTokens());
            value.addProperty("cachedInputTokens", entry.cachedInputTokens());
            value.addProperty("outputTokens", entry.outputTokens());
            value.addProperty("estimatedCost", entry.estimatedCost());
            value.addProperty("status", entry.status());
            if (entry.context().isEmpty()) value.add("context", null);
            else usageContext(value, entry.context().orElseThrow());
            usage.add(value);
        });
        root.add("usage", usage);
        return root.toString();
    }

    private static void usageContext(JsonObject usage, DialogueContextUsageWire context) {
        JsonObject value = new JsonObject();
        value.addProperty("systemTokens", context.systemTokens());
        value.addProperty("identityTokens", context.identityTokens());
        value.addProperty("shortTermDbTokens", context.shortTermDbTokens());
        value.addProperty("longTermRelationalTokens", context.longTermRelationalTokens());
        value.addProperty("longTermVectorTokens", context.longTermVectorTokens());
        value.addProperty("recentTurnsDbTokens", context.recentTurnsDbTokens());
        value.addProperty("gameContextTokens", context.gameContextTokens());
        value.addProperty("ownerInputTokens", context.ownerInputTokens());
        value.addProperty("instructionTokens", context.instructionTokens());
        value.addProperty("totalPromptTokens", context.totalPromptTokens());
        value.addProperty("shortTermItems", context.shortTermItems());
        value.addProperty("longTermRelationalItems", context.longTermRelationalItems());
        value.addProperty("longTermVectorItems", context.longTermVectorItems());
        value.addProperty("recentTurnItems", context.recentTurnItems());
        usage.add("context", value);
    }

    private static void addNullable(JsonObject root, String key, String value) {
        if (value == null) root.add(key, null);
        else root.addProperty(key, value);
    }

    public DialogueHistoryWireResult decode(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) {
            throw new PetWireFormatException("dialogue history response is missing or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new PetWireFormatException("dialogue history must be an object");
            JsonObject root = parsed.getAsJsonObject();
            exact(root, ROOT, "dialogue history");
            UUID owner = uuid(root, "ownerUuid");
            UUID pet = uuid(root, "petId");
            JsonArray conversations = array(root, "conversations");
            JsonArray usage = array(root, "usage");
            if (conversations.size() > 32 || usage.size() > 64) {
                throw new PetWireFormatException("dialogue history contains too many entries");
            }
            ArrayList<DialogueHistoryWireResult.ConversationEntry> entries = new ArrayList<>();
            for (JsonElement element : conversations) {
                JsonObject value = object(element, "conversation entry");
                exact(value, CONVERSATION, "conversation entry");
                entries.add(new DialogueHistoryWireResult.ConversationEntry(
                        instant(value, "occurredAt"), string(value, "importance", 16),
                        string(value, "summary", 2_000), nullable(value, "ownerText", 2_000),
                        nullable(value, "petReply", 2_000)));
            }
            ArrayList<DialogueHistoryWireResult.UsageEntry> usages = new ArrayList<>();
            for (JsonElement element : usage) {
                JsonObject value = object(element, "usage entry");
                exact(value, USAGE, "usage entry");
                JsonElement context = value.get("context");
                usages.add(new DialogueHistoryWireResult.UsageEntry(
                        instant(value, "createdAt"), string(value, "operation", 32),
                        string(value, "model", 191), integer(value, "inputTokens"),
                        integer(value, "cachedInputTokens"), integer(value, "outputTokens"),
                        decimal(value, "estimatedCost"), string(value, "status", 32),
                        context == null || context.isJsonNull()
                                ? Optional.empty() : Optional.of(context(context))));
            }
            return new DialogueHistoryWireResult(owner, pet, string(root, "petName", 64), entries, usages);
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException malformed) {
            throw new PetWireFormatException("invalid dialogue history response", malformed);
        }
    }

    private static DialogueContextUsageWire context(JsonElement element) {
        JsonObject value = object(element, "context usage");
        exact(value, CONTEXT, "context usage");
        return new DialogueContextUsageWire(
                integer(value, "systemTokens"), integer(value, "identityTokens"),
                integer(value, "shortTermDbTokens"), integer(value, "longTermRelationalTokens"),
                integer(value, "longTermVectorTokens"), integer(value, "recentTurnsDbTokens"),
                integer(value, "gameContextTokens"), integer(value, "ownerInputTokens"),
                integer(value, "instructionTokens"), integer(value, "totalPromptTokens"),
                integer(value, "shortTermItems"), integer(value, "longTermRelationalItems"),
                integer(value, "longTermVectorItems"), integer(value, "recentTurnItems"));
    }

    private static void exact(JsonObject value, Set<String> expected, String label) {
        if (!value.keySet().equals(new HashSet<>(expected))) {
            throw new PetWireFormatException("unexpected " + label + " fields");
        }
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new PetWireFormatException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) throw new PetWireFormatException(key + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject root, String key, int max) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(key + " must be a string");
        }
        String text = value.getAsString().strip();
        if (text.isEmpty() || text.length() > max) throw new PetWireFormatException(key + " is invalid");
        return text;
    }

    private static String nullable(JsonObject root, String key, int max) {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) return null;
        return string(root, key, max);
    }

    private static int integer(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(key + " must be an integer");
        }
        try {
            int result = value.getAsInt();
            if (result < 0) throw new PetWireFormatException(key + " cannot be negative");
            return result;
        } catch (NumberFormatException malformed) {
            throw new PetWireFormatException(key + " must be an integer", malformed);
        }
    }

    private static BigDecimal decimal(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(key + " must be a decimal");
        }
        try {
            BigDecimal result = new BigDecimal(value.getAsString());
            if (result.signum() < 0 || result.scale() > 8) throw new PetWireFormatException(key + " is invalid");
            return result;
        } catch (NumberFormatException malformed) {
            throw new PetWireFormatException(key + " is invalid", malformed);
        }
    }

    private static UUID uuid(JsonObject root, String key) {
        try { return UUID.fromString(string(root, key, 64)); }
        catch (RuntimeException malformed) { throw new PetWireFormatException(key + " must be a UUID", malformed); }
    }

    private static Instant instant(JsonObject root, String key) {
        try { return Instant.parse(string(root, key, 64)); }
        catch (RuntimeException malformed) { throw new PetWireFormatException(key + " must be an ISO instant", malformed); }
    }
}
