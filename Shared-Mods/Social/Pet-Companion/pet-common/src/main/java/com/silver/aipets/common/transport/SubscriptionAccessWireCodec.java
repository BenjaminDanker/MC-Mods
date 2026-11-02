package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;
import java.util.Set;
import java.time.Instant;
import java.math.BigDecimal;

/** Strict, bounded codec for the authenticated subscription-access read. */
public final class SubscriptionAccessWireCodec {
    private static final Set<String> FIELDS = Set.of(
            "aiAccessEnabled", "status", "cancelAtPeriodEnd", "currentPeriodEnd",
            "budgetUsd", "consumedUsd", "remainingUsd");
    private static final int MAX_JSON_CHARS = 384;

    public String encode(SubscriptionAccessWireResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("aiAccessEnabled", result.aiAccessEnabled());
        root.addProperty("status", result.status());
        root.addProperty("cancelAtPeriodEnd", result.cancelAtPeriodEnd());
        if (result.currentPeriodEnd() == null) {
            root.add("currentPeriodEnd", null);
        } else {
            root.addProperty("currentPeriodEnd", result.currentPeriodEnd());
        }
        addDecimal(root, "budgetUsd", result.budgetUsd());
        addDecimal(root, "consumedUsd", result.consumedUsd());
        addDecimal(root, "remainingUsd", result.remainingUsd());
        return root.toString();
    }

    private static void addDecimal(JsonObject root, String name, BigDecimal value) {
        if (value == null) root.add(name, null);
        else root.addProperty(name, value);
    }

    public SubscriptionAccessWireResult decode(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) {
            throw new PetWireFormatException("subscription access response is missing or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException("subscription access response must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(FIELDS)) {
                throw new PetWireFormatException("unexpected subscription access fields");
            }
            JsonElement value = root.get("aiAccessEnabled");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new PetWireFormatException("aiAccessEnabled must be a boolean");
            }
            JsonElement status = root.get("status");
            if (status == null || !status.isJsonPrimitive()
                    || !status.getAsJsonPrimitive().isString()
                    || status.getAsString().isBlank() || status.getAsString().length() > 32) {
                throw new PetWireFormatException("status must be a short string");
            }
            JsonElement cancelAtPeriodEnd = root.get("cancelAtPeriodEnd");
            if (cancelAtPeriodEnd == null || !cancelAtPeriodEnd.isJsonPrimitive()
                    || !cancelAtPeriodEnd.getAsJsonPrimitive().isBoolean()) {
                throw new PetWireFormatException("cancelAtPeriodEnd must be a boolean");
            }
            JsonElement periodEnd = root.get("currentPeriodEnd");
            String periodEndText = null;
            if (periodEnd != null && !periodEnd.isJsonNull()) {
                if (!periodEnd.isJsonPrimitive() || !periodEnd.getAsJsonPrimitive().isString()) {
                    throw new PetWireFormatException("currentPeriodEnd must be an ISO instant or null");
                }
                periodEndText = periodEnd.getAsString();
                try {
                    Instant.parse(periodEndText);
                } catch (RuntimeException invalidInstant) {
                    throw new PetWireFormatException("currentPeriodEnd must be an ISO instant", invalidInstant);
                }
            }
            BigDecimal budget = decimal(root.get("budgetUsd"), "budgetUsd");
            BigDecimal consumed = decimal(root.get("consumedUsd"), "consumedUsd");
            BigDecimal remaining = decimal(root.get("remainingUsd"), "remainingUsd");
            return new SubscriptionAccessWireResult(
                    value.getAsBoolean(),
                    status.getAsString(),
                    cancelAtPeriodEnd.getAsBoolean(),
                    periodEndText, budget, consumed, remaining);
        } catch (RuntimeException malformed) {
            if (malformed instanceof PetWireFormatException wire) {
                throw wire;
            }
            throw new PetWireFormatException("invalid subscription access response", malformed);
        }
    }

    private static BigDecimal decimal(JsonElement value, String name) {
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PetWireFormatException(name + " must be a decimal or null");
        }
        try {
            BigDecimal result = new BigDecimal(value.getAsString());
            if (result.signum() < 0 || result.scale() > 8) {
                throw new PetWireFormatException(name + " is invalid");
            }
            return result;
        } catch (NumberFormatException malformed) {
            throw new PetWireFormatException(name + " is invalid", malformed);
        }
    }
}
