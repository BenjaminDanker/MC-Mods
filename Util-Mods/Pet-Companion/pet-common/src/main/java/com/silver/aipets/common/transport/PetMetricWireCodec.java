package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/** Strict, bounded JSON codec for backend operational metric events. */
public final class PetMetricWireCodec {
    private static final Set<String> FIELDS = Set.of("metric", "amount");
    private static final int MAX_JSON_CHARS = 256;

    public String encode(PetMetricWireEvent event) {
        JsonObject root = new JsonObject();
        root.addProperty("metric", event.metric().name());
        root.addProperty("amount", event.amount());
        return root.toString();
    }

    public PetMetricWireEvent decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_JSON_CHARS) {
            throw new PetWireFormatException("Metric payload is empty or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonObject() || !parsed.getAsJsonObject().keySet().equals(FIELDS)) {
                throw new PetWireFormatException("Metric payload fields are invalid");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement metricValue = root.get("metric");
            if (!metricValue.isJsonPrimitive()
                    || !metricValue.getAsJsonPrimitive().isString()) {
                throw new PetWireFormatException("metric must be a string");
            }
            JsonElement amountValue = root.get("amount");
            if (!amountValue.isJsonPrimitive()
                    || !amountValue.getAsJsonPrimitive().isNumber()) {
                throw new PetWireFormatException("amount must be an integer");
            }
            String amountText = amountValue.getAsString();
            if (!amountText.matches("[0-9]+")) {
                throw new PetWireFormatException("amount must be an integer");
            }
            long amount = Long.parseLong(amountText);
            return new PetMetricWireEvent(
                    PetMetricWireEvent.Metric.valueOf(metricValue.getAsString()), amount);
        } catch (PetWireFormatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PetWireFormatException("Metric payload is malformed", failure);
        }
    }
}
