package com.silver.aipets.service.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.service.config.PetServiceConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** OpenAI embeddings client; sends only the bounded text supplied by the embedding boundary. */
public final class OpenAiEmbeddingModelClient implements EmbeddingModelClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final int dimension;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiEmbeddingModelClient(
            URI baseUri,
            String model,
            int dimension,
            String apiKey,
            Duration timeout) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (baseUri.getHost() == null
                || !("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        this.endpoint = baseUri.resolve("/v1/embeddings");
        this.model = bounded(model, "model", 191);
        if (dimension < 1 || dimension > 16_384) {
            throw new IllegalArgumentException("dimension must be between 1 and 16384");
        }
        this.dimension = dimension;
        this.apiKey = bounded(apiKey, "apiKey", 512);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public static OpenAiEmbeddingModelClient fromConfig(PetServiceConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.openAiApiKey().isBlank()) {
            throw new IllegalArgumentException("OpenAI embedding API key is not configured");
        }
        return new OpenAiEmbeddingModelClient(
                config.openAiBaseUri(), config.embeddingModel(), config.embeddingDimension(),
                config.openAiApiKey(), Duration.ofMillis(config.qdrantTimeoutMs()));
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public EmbeddingVector embed(String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        if (normalizedText.isBlank() || normalizedText.length() > 4_000) {
            throw new IllegalArgumentException("normalizedText length is invalid");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", normalizedText);
        body.addProperty("dimensions", dimension);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new VectorStoreUnavailableException("Embedding provider transport failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new VectorStoreUnavailableException("Embedding provider request interrupted", failure);
        }
        if (response.statusCode() / 100 != 2) {
            throw new VectorStoreUnavailableException(
                    "Embedding provider returned HTTP " + response.statusCode());
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null || data.size() != 1) {
                throw new IllegalStateException("embedding response data is invalid");
            }
            JsonArray values = data.get(0).getAsJsonObject().getAsJsonArray("embedding");
            if (values == null || values.size() != dimension) {
                throw new IllegalStateException("embedding response dimension is invalid");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                vector[index] = values.get(index).getAsFloat();
            }
            return new EmbeddingVector(model, vector);
        } catch (RuntimeException malformed) {
            if (malformed instanceof VectorStoreUnavailableException unavailable) {
                throw unavailable;
            }
            throw new VectorStoreUnavailableException("Embedding provider response was invalid", malformed);
        }
    }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }
}
