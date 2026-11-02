package com.silver.aipets.service.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.config.PetServiceConfig;
import com.silver.aipets.service.provider.ProviderTarget;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * OpenAI embeddings client; sends only the bounded text supplied by the embedding boundary. The same
 * client serves the OpenRouter fallback (same model, identical vectors): only the target differs.
 */
public final class OpenAiEmbeddingModelClient implements EmbeddingModelClient {
    private final HttpClient client;
    private final ProviderTarget target;
    private final URI endpoint;
    /** The model name the index records; also what {@link #model()} reports. */
    private final String model;
    /** The model id sent to the provider (OpenRouter spells it {@code openai/<model>}). */
    private final String requestModel;
    private final int dimension;
    private final String apiKey;
    private final Duration timeout;
    private final AiPricing pricing;

    public OpenAiEmbeddingModelClient(
            URI baseUri,
            String model,
            int dimension,
            String apiKey,
            Duration timeout) {
        this(baseUri, model, dimension, apiKey, timeout, AiPricing.defaults());
    }

    public OpenAiEmbeddingModelClient(
            URI baseUri,
            String model,
            int dimension,
            String apiKey,
            Duration timeout,
            AiPricing pricing) {
        this(ProviderTarget.openAi(Objects.requireNonNull(baseUri, "baseUri"), apiKey), model, model,
                dimension, timeout, pricing);
    }

    public OpenAiEmbeddingModelClient(
            ProviderTarget target,
            String requestModel,
            String model,
            int dimension,
            Duration timeout,
            AiPricing pricing) {
        this.target = Objects.requireNonNull(target, "target");
        this.endpoint = target.endpoint("embeddings");
        this.requestModel = bounded(requestModel, "requestModel", 191);
        this.model = bounded(model, "model", 191);
        if (dimension < 1 || dimension > 16_384) {
            throw new IllegalArgumentException("dimension must be between 1 and 16384");
        }
        this.dimension = dimension;
        this.apiKey = target.apiKey();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
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
                config.openAiApiKey(), Duration.ofMillis(config.qdrantTimeoutMs()), config.aiPricing());
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public EmbeddingVector embed(String normalizedText) {
        return embedWithUsage(normalizedText).embedding();
    }

    @Override
    public EmbeddingModelResponse embedWithUsage(String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        if (normalizedText.isBlank() || normalizedText.length() > 4_000) {
            throw new IllegalArgumentException("normalizedText length is invalid");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", requestModel);
        body.addProperty("input", normalizedText);
        body.addProperty("dimensions", dimension);
        if (target.isFallback()) body.addProperty("encoding_format", "float");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        Instant started = Instant.now();
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
            JsonObject usage = root.getAsJsonObject("usage");
            final int inputTokens = usage != null && usage.has("prompt_tokens")
                    ? integer(usage, "prompt_tokens") : 0;
            long latency = Math.max(0L, Duration.between(started, Instant.now()).toMillis());
            return new EmbeddingModelResponse(
                    new EmbeddingVector(model, vector),
                    Optional.ofNullable(root.get("id"))
                            .filter(element -> element.isJsonPrimitive())
                            .map(element -> element.getAsString()),
                    inputTokens, target.cost(usage, () -> pricing.embeddingCost(inputTokens)), latency,
                    target.isFallback() ? "openrouter" : "openai");
        } catch (RuntimeException malformed) {
            if (malformed instanceof VectorStoreUnavailableException unavailable) {
                throw unavailable;
            }
            throw new VectorStoreUnavailableException("Embedding provider response was invalid", malformed);
        }
    }

    private static int integer(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isNumber()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        int value = object.get(name).getAsInt();
        if (value < 0 || object.get(name).getAsDouble() != value) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
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
