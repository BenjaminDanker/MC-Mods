package com.silver.aipets.common.observability;

import com.google.gson.JsonObject;
import com.silver.aipets.common.domain.BackendId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Deliberately narrow JSON log event. There is no raw-message, prompt, authorization, URL, or
 * throwable-message field, which keeps ordinary operational logging away from secrets and chat.
 */
public final class StructuredPetEvent {
    private static final int MAX_TOKEN = 96;
    private final JsonObject fields = new JsonObject();

    private StructuredPetEvent(String operation) {
        fields.addProperty("event", "pet_operation");
        fields.addProperty("operation", token(operation, "operation"));
    }

    public static StructuredPetEvent operation(String operation) {
        return new StructuredPetEvent(operation);
    }

    public StructuredPetEvent correlation(UUID correlationId) {
        fields.addProperty("correlation_id", Objects.requireNonNull(correlationId).toString());
        return this;
    }

    public StructuredPetEvent pet(UUID petId) {
        fields.addProperty("pet_id", Objects.requireNonNull(petId).toString());
        return this;
    }

    /** Hashes owner identity so routine logs remain correlatable without publishing the UUID. */
    public StructuredPetEvent owner(UUID ownerUuid) {
        fields.addProperty("owner_hash", ownerHash(Objects.requireNonNull(ownerUuid)));
        return this;
    }

    public StructuredPetEvent backend(BackendId backendId) {
        fields.addProperty("backend_id", token(
                Objects.requireNonNull(backendId).value(), "backendId"));
        return this;
    }

    public StructuredPetEvent transition(String from, String to, long recordVersion) {
        if (recordVersion < 0) throw new IllegalArgumentException("recordVersion must be non-negative");
        fields.addProperty("state_from", token(from, "stateFrom"));
        fields.addProperty("state_to", token(to, "stateTo"));
        fields.addProperty("record_version", recordVersion);
        return this;
    }

    public StructuredPetEvent modelUsage(
            String model, int inputTokens, int cachedInputTokens, int outputTokens) {
        if (inputTokens < 0 || cachedInputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must be non-negative");
        }
        fields.addProperty("model", token(model, "model"));
        fields.addProperty("input_tokens", inputTokens);
        fields.addProperty("cached_input_tokens", cachedInputTokens);
        fields.addProperty("output_tokens", outputTokens);
        return this;
    }

    public StructuredPetEvent latencyMillis(long latencyMillis) {
        if (latencyMillis < 0) throw new IllegalArgumentException("latencyMillis must be non-negative");
        fields.addProperty("latency_ms", latencyMillis);
        return this;
    }

    public StructuredPetEvent count(long count) {
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        fields.addProperty("count", count);
        return this;
    }

    /** Records only the bounded exception class category; exception messages are intentionally lost. */
    public StructuredPetEvent failure(Throwable failure) {
        Throwable nonNull = Objects.requireNonNull(failure, "failure");
        String category = nonNull.getClass().getSimpleName();
        fields.addProperty("failure_category", token(
                category.isBlank() ? "Throwable" : category, "failureCategory"));
        return this;
    }

    public StructuredPetEvent outcome(String outcome) {
        fields.addProperty("outcome", token(outcome, "outcome"));
        return this;
    }

    public String toJson() {
        return fields.toString();
    }

    public static String ownerHash(UUID ownerUuid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ownerUuid.toString().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String token(String value, String name) {
        Objects.requireNonNull(value, name);
        String safe = value.trim().replaceAll("[^A-Za-z0-9_.:/-]", "_");
        if (safe.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return safe.substring(0, Math.min(safe.length(), MAX_TOKEN));
    }
}
