package com.silver.aipets.service.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Where an OpenAI-compatible request goes and how its cost is known. OpenAI is the primary target;
 * OpenRouter (same API, same models) is the fallback target and reports the dollar cost of each
 * call in {@code usage.cost}, which is charged as reported. OpenAI reports tokens only, so its
 * calls are priced from {@code AiPricing}.
 */
public record ProviderTarget(
        String name, URI apiBase, String apiKey, boolean costInResponse, BigDecimal missingCostCharge) {
    public static final String OPENAI = "openai";
    public static final String OPENROUTER = "openrouter";

    public ProviderTarget {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(apiBase, "apiBase");
        if (apiBase.getHost() == null || !("http".equalsIgnoreCase(apiBase.getScheme())
                || "https".equalsIgnoreCase(apiBase.getScheme()))) {
            throw new IllegalArgumentException("apiBase must be an absolute HTTP(S) URI");
        }
        Objects.requireNonNull(apiKey, "apiKey");
        apiKey = apiKey.strip();
        if (apiKey.isEmpty() || apiKey.length() > 512 || apiKey.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("apiKey is invalid");
        }
        Objects.requireNonNull(missingCostCharge, "missingCostCharge");
        if (missingCostCharge.signum() < 0) throw new IllegalArgumentException("missingCostCharge is invalid");
    }

    /** OpenAI at the given origin (for example {@code https://api.openai.com}). */
    public static ProviderTarget openAi(URI origin, String apiKey) {
        return new ProviderTarget(OPENAI, URI.create(trimmed(origin) + "/v1"), apiKey, false, BigDecimal.ZERO);
    }

    /**
     * OpenRouter at the given API base (for example {@code https://openrouter.ai/api/v1}).
     *
     * @param missingCostCharge charged if a response omits {@code usage.cost}
     */
    public static ProviderTarget openRouter(URI apiBase, String apiKey, BigDecimal missingCostCharge) {
        return new ProviderTarget(OPENROUTER, URI.create(trimmed(apiBase)), apiKey, true, missingCostCharge);
    }

    public boolean isFallback() {
        return !OPENAI.equals(name);
    }

    public URI endpoint(String path) {
        return URI.create(trimmed(apiBase) + "/" + path);
    }

    /**
     * The charge for one call: the provider-reported cost when this target reports one (rounded up to
     * the ledger's eight decimal places so a call is never recorded as free), otherwise the supplied
     * token-priced amount. A target that should report cost but did not is charged conservatively.
     */
    public BigDecimal cost(JsonObject usage, Supplier<BigDecimal> tokenPriced) {
        if (!costInResponse) return tokenPriced.get();
        JsonElement reported = usage == null ? null : usage.get("cost");
        if (reported != null && reported.isJsonPrimitive() && reported.getAsJsonPrimitive().isNumber()) {
            BigDecimal cost = reported.getAsBigDecimal();
            if (cost.signum() >= 0) return cost.setScale(8, RoundingMode.UP);
        }
        return missingCostCharge;
    }

    private static String trimmed(URI uri) {
        String text = uri.toString();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }
}
