package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;
import java.util.Set;

public final class RecallResetWireCodec {
    private static final Set<String> FIELDS = Set.of("status", "periodKey");

    public String encode(RecallResetWireResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        root.addProperty("periodKey", result.periodKey());
        return root.toString();
    }

    public RecallResetWireResult decode(String json) {
        if (json == null || json.length() > 1_024) {
            throw new PetWireFormatException("recall reset response is missing or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw new PetWireFormatException("invalid recall reset fields");
            }
            JsonObject root = parsed.getAsJsonObject();
            return new RecallResetWireResult(
                    RecallResetWireStatus.valueOf(root.get("status").getAsString()),
                    root.get("periodKey").getAsString());
        } catch (RuntimeException malformed) {
            if (malformed instanceof PetWireFormatException wire) throw wire;
            throw new PetWireFormatException("invalid recall reset response", malformed);
        }
    }
}
