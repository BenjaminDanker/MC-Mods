package com.silver.aipets.common.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import java.util.Set;

/** Strict wire decoding; historical partial locations remain unknown on SQL reads. */
public final class SpatialLocationCodec {
    private static final Set<String> FIELDS = Set.of("server", "world", "dimension", "x", "y", "z");
    private SpatialLocationCodec() { }

    public static JsonObject encode(SpatialLocation location) {
        JsonObject value = new JsonObject();
        value.addProperty("server", location.server());
        value.addProperty("world", location.world());
        value.addProperty("dimension", location.dimension());
        value.addProperty("x", location.x());
        value.addProperty("y", location.y());
        value.addProperty("z", location.z());
        return value;
    }

    public static SpatialLocation decode(JsonObject value) {
        if (value == null || !value.keySet().equals(FIELDS))
            throw new IllegalArgumentException("Invalid spatial location fields");
        for (String field : FIELDS) {
            var element = value.get(field);
            if (element == null || !element.isJsonPrimitive()
                    || (Set.of("x", "y", "z").contains(field)
                            ? !element.getAsJsonPrimitive().isNumber()
                            : !element.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Invalid location field " + field);
        }
        return new SpatialLocation(value.get("server").getAsString(), value.get("world").getAsString(),
                value.get("dimension").getAsString(), value.get("x").getAsDouble(),
                value.get("y").getAsDouble(), value.get("z").getAsDouble());
    }

    public static Optional<SpatialLocation> readStored(String json) {
        if (json == null || json.length() > 2048) return Optional.empty();
        try { return Optional.of(decode(JsonParser.parseString(json).getAsJsonObject())); }
        catch (RuntimeException invalidOrHistorical) { return Optional.empty(); }
    }
}
