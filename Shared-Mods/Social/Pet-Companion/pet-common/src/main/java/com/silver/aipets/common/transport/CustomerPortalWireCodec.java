package com.silver.aipets.common.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Strict bounded internal response for a short-lived Stripe Customer Portal URL. */
public final class CustomerPortalWireCodec {
    private static final Set<String> FIELDS = Set.of("status", "portalUrl");

    public String encode(CustomerPortalWireResult result) {
        Objects.requireNonNull(result, "result");
        JsonObject root = new JsonObject();
        root.addProperty("status", result.status().name());
        root.add("portalUrl", result.portalUrl().<JsonElement>map(
                com.google.gson.JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        return root.toString();
    }

    public CustomerPortalWireResult decode(String json) {
        if (json == null || json.length() > 2_048) {
            throw new PetWireFormatException("customer portal response is missing or too large");
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new PetWireFormatException("response must be an object");
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(FIELDS)) {
                throw new PetWireFormatException("unexpected customer portal fields");
            }
            CustomerPortalWireStatus status = CustomerPortalWireStatus.valueOf(
                    root.get("status").getAsString());
            JsonElement url = root.get("portalUrl");
            Optional<String> portalUrl = url == null || url.isJsonNull()
                    ? Optional.empty() : Optional.of(url.getAsString());
            return new CustomerPortalWireResult(status, portalUrl);
        } catch (RuntimeException malformed) {
            if (malformed instanceof PetWireFormatException wire) throw wire;
            throw new PetWireFormatException("invalid customer portal response", malformed);
        }
    }
}
