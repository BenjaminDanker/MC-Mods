package com.silver.aipets.service.provider;

import com.google.gson.JsonObject;
import com.silver.aipets.service.metrics.PetOperationalMetrics;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes every provider fallback and every moderation bypass impossible to miss. The log line is
 * the alert: it starts with the word {@code ALERT} and a fixed marker, so a single
 * {@code grep ALERT} over the service log finds every event. Each event is:
 * <ul>
 *   <li>a WARNING log line (ERROR when the fallback itself fails) that starts with a fixed, greppable marker
 *       ({@value #FALLBACK_USED}, {@value #FALLBACK_FAILED}, {@value #MODERATION_BYPASSED}) followed by JSON;</li>
 *   <li>a counter on the metrics endpoint ({@code aipets_ai_fallback_*_total},
 *       {@code aipets_moderation_bypassed_total});</li>
 *   <li>for fallback calls that were billed, a marked {@code ai_usage} row whose model starts with
 *       {@value #LEDGER_PREFIX}.</li>
 * </ul>
 * Reasons are bounded exception categories; no prompts, messages, or credentials are included.
 */
public final class AiFallbackReporter {
    public static final String FALLBACK_USED = "ALERT AI_FALLBACK_USED";
    public static final String FALLBACK_FAILED = "ALERT AI_FALLBACK_FAILED";
    public static final String MODERATION_BYPASSED = "ALERT AI_MODERATION_BYPASSED";
    /** Prefix of {@code ai_usage.model} for a call served by the fallback provider. */
    public static final String LEDGER_PREFIX = "FALLBACK:openrouter:";
    public static final AiFallbackReporter LOG_ONLY = new AiFallbackReporter(null);

    private static final System.Logger LOGGER = System.getLogger(AiFallbackReporter.class.getName());

    public enum Operation { CHAT, EMBEDDING }

    private final PetOperationalMetrics metrics;

    public AiFallbackReporter(PetOperationalMetrics metrics) {
        this.metrics = metrics;
    }

    public void fallbackUsed(Operation operation, String model, String reason, Optional<UUID> correlation) {
        count(operation == Operation.CHAT ? PetOperationalMetrics.Counter.AI_FALLBACK_CHAT_USED
                : PetOperationalMetrics.Counter.AI_FALLBACK_EMBEDDING_USED);
        LOGGER.log(System.Logger.Level.WARNING, FALLBACK_USED + " " + json(operation.name(), model, reason, correlation,
                "openai", "openrouter"));
    }

    public void fallbackFailed(Operation operation, String model, String reason, Optional<UUID> correlation) {
        count(PetOperationalMetrics.Counter.AI_FALLBACK_FAILED);
        LOGGER.log(System.Logger.Level.ERROR, FALLBACK_FAILED + " " + json(operation.name(), model, reason, correlation,
                "openai", "openrouter"));
    }

    /** Moderation was unavailable, so the message was let through unmoderated. */
    public void moderationBypassed(String reason) {
        count(PetOperationalMetrics.Counter.MODERATION_BYPASSED);
        LOGGER.log(System.Logger.Level.WARNING, MODERATION_BYPASSED + " " + json("MODERATION", "", reason,
                Optional.empty(), "openai", "none"));
    }

    public void moderationBlocked() {
        count(PetOperationalMetrics.Counter.MODERATION_BLOCKED);
    }

    private void count(PetOperationalMetrics.Counter counter) {
        if (metrics != null) metrics.increment(counter);
    }

    private static String json(String operation, String model, String reason, Optional<UUID> correlation,
            String from, String to) {
        Objects.requireNonNull(reason, "reason");
        JsonObject object = new JsonObject();
        object.addProperty("operation", operation);
        object.addProperty("from", from);
        object.addProperty("to", to);
        if (!model.isEmpty()) object.addProperty("model", model);
        object.addProperty("reason", reason.length() > 80 ? reason.substring(0, 80) : reason);
        correlation.ifPresent(id -> object.addProperty("correlation_id", id.toString()));
        return object.toString();
    }
}
