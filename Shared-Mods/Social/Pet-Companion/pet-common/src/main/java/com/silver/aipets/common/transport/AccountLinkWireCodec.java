package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bounded JSON for one-time UUID-bound Checkout URL generation. */
public final class AccountLinkWireCodec {
    private static final Set<String> RESULT_FIELDS = Set.of("status", "checkoutUrl", "expiresAt");

    public String encodeResult(AccountLinkWireResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        root.add("checkoutUrl", result.checkoutUrl().<JsonElement>map(value -> {
            com.google.gson.JsonPrimitive encoded = new com.google.gson.JsonPrimitive(value);
            return encoded;
        }).orElse(JsonNull.INSTANCE));
        root.add("expiresAt", result.expiresAt().<JsonElement>map(value -> {
            com.google.gson.JsonPrimitive encoded = new com.google.gson.JsonPrimitive(value.toString());
            return encoded;
        }).orElse(JsonNull.INSTANCE));
        return root.toString();
    }

    public AccountLinkWireResult decodeResult(String json) {
        JsonObject root = object(json, "account-link result");
        requireOnly(root, RESULT_FIELDS);
        AccountLinkWireStatus status = AccountLinkWireStatus.valueOf(string(root, "status"));
        Optional<String> checkoutUrl = nullableString(root, "checkoutUrl");
        Optional<Instant> expiresAt = nullableString(root, "expiresAt").map(Instant::parse);
        return new AccountLinkWireResult(status, checkoutUrl, expiresAt);
    }

    private static JsonObject object(String json, String context) {
        Objects.requireNonNull(json, "json");
        if (json.length() > 1_024) {
            throw new PetWireFormatException(context + " is too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PetWireFormatException(context + " must be an object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException malformed) {
            if (malformed instanceof PetWireFormatException wire) throw wire;
            throw new PetWireFormatException("Malformed " + context, malformed);
        }
    }

    private static void requireOnly(JsonObject root, Set<String> fields) {
        if (!root.keySet().equals(fields)) {
            throw new PetWireFormatException("Unexpected or missing account-link fields");
        }
    }

    private static String string(JsonObject root, String field) {
        JsonElement value = root.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new PetWireFormatException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static Optional<String> nullableString(JsonObject root, String field) {
        JsonElement value = root.get(field);
        return value == null || value.isJsonNull()
                ? Optional.empty()
                : Optional.of(string(root, field));
    }
}
