package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Small codec used for durable, non-text prompt accounting in ai_usage. */
public final class DialogueContextUsageWireCodec {
    private static final Set<String> FIELDS = Set.of(
            "systemTokens", "identityTokens", "shortTermDbTokens", "longTermRelationalTokens",
            "longTermVectorTokens", "recentTurnsDbTokens", "gameContextTokens", "ownerInputTokens",
            "instructionTokens", "totalPromptTokens", "shortTermItems", "longTermRelationalItems",
            "longTermVectorItems", "recentTurnItems");

    public String encode(DialogueContextUsageWire value) {
        JsonObject root = new JsonObject();
        root.addProperty("systemTokens", value.systemTokens());
        root.addProperty("identityTokens", value.identityTokens());
        root.addProperty("shortTermDbTokens", value.shortTermDbTokens());
        root.addProperty("longTermRelationalTokens", value.longTermRelationalTokens());
        root.addProperty("longTermVectorTokens", value.longTermVectorTokens());
        root.addProperty("recentTurnsDbTokens", value.recentTurnsDbTokens());
        root.addProperty("gameContextTokens", value.gameContextTokens());
        root.addProperty("ownerInputTokens", value.ownerInputTokens());
        root.addProperty("instructionTokens", value.instructionTokens());
        root.addProperty("totalPromptTokens", value.totalPromptTokens());
        root.addProperty("shortTermItems", value.shortTermItems());
        root.addProperty("longTermRelationalItems", value.longTermRelationalItems());
        root.addProperty("longTermVectorItems", value.longTermVectorItems());
        root.addProperty("recentTurnItems", value.recentTurnItems());
        return root.toString();
    }

    public DialogueContextUsageWire decode(String json) {
        if (json == null || json.length() > 2_048) {
            throw new PetWireFormatException("context usage is missing or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw new PetWireFormatException("unexpected context usage fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            return new DialogueContextUsageWire(
                    integer(root, "systemTokens"), integer(root, "identityTokens"),
                    integer(root, "shortTermDbTokens"), integer(root, "longTermRelationalTokens"),
                    integer(root, "longTermVectorTokens"), integer(root, "recentTurnsDbTokens"),
                    integer(root, "gameContextTokens"), integer(root, "ownerInputTokens"),
                    integer(root, "instructionTokens"), integer(root, "totalPromptTokens"),
                    integer(root, "shortTermItems"), integer(root, "longTermRelationalItems"),
                    integer(root, "longTermVectorItems"), integer(root, "recentTurnItems"));
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException malformed) {
            throw new PetWireFormatException("invalid context usage", malformed);
        }
    }

    private static int integer(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(key + " must be a number");
        }
        int result = value.getAsInt();
        if (result < 0) throw new PetWireFormatException(key + " cannot be negative");
        return result;
    }
}
