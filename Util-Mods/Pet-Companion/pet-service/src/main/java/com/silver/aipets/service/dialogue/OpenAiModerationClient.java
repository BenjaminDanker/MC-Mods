package com.silver.aipets.service.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Bounded text moderation adapter; transport/availability failures are reported separately. */
public final class OpenAiModerationClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiModerationClient(URI baseUri, String model, String apiKey, Duration timeout) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (baseUri.getHost() == null || !("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        this.endpoint = baseUri.resolve("/v1/moderations");
        this.model = bounded(model, "model", 191);
        this.apiKey = bounded(apiKey, "apiKey", 512);
        this.timeout = timeout;
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public ModerationResult moderate(String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", normalizedText);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body().length() > 200_000) {
                return new ModerationResult(true, false, "MODERATION_UNAVAILABLE");
            }
            JsonArray results = JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonArray("results");
            if (results == null || results.size() != 1) {
                return new ModerationResult(true, false, "MODERATION_INVALID");
            }
            JsonObject result = results.get(0).getAsJsonObject();
            boolean flagged = result.get("flagged").getAsBoolean();
            return new ModerationResult(!flagged, true, flagged ? "MODERATION_FLAGGED" : "ALLOWED");
        } catch (IOException failure) {
            return new ModerationResult(true, false, "MODERATION_UNAVAILABLE");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new ModerationResult(true, false, "MODERATION_INTERRUPTED");
        } catch (RuntimeException failure) {
            return new ModerationResult(true, false, "MODERATION_INVALID");
        }
    }

    public record ModerationResult(boolean allowed, boolean available, String category) {
        public ModerationResult {
            Objects.requireNonNull(category, "category");
        }
    }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
