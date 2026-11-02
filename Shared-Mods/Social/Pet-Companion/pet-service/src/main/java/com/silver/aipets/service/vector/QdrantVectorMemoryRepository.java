package com.silver.aipets.service.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.service.config.PetServiceConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Qdrant HTTP adapter; MariaDB remains authoritative for the card and version. */
public final class QdrantVectorMemoryRepository implements VectorMemoryRepository {
    private final HttpClient client;
    private final URI baseUri;
    private final String collection;
    private final int dimension;
    private final Duration timeout;
    private final Optional<String> apiKey;
    private boolean collectionReady;

    public static QdrantVectorMemoryRepository fromConfig(PetServiceConfig config) {
        Objects.requireNonNull(config, "config");
        if (!config.qdrantEnabled()) {
            throw new IllegalArgumentException("Qdrant is disabled by configuration");
        }
        return new QdrantVectorMemoryRepository(
                config.qdrantUri(), config.qdrantCollection(), config.qdrantDimension(),
                Duration.ofMillis(config.qdrantTimeoutMs()),
                Optional.ofNullable(config.qdrantApiKey()));
    }

    public QdrantVectorMemoryRepository(
            URI baseUri,
            String collection,
            int dimension,
            Duration timeout,
            Optional<String> apiKey) {
        this.baseUri = normalizeBaseUri(baseUri);
        this.collection = validateCollection(collection);
        if (dimension < 1 || dimension > 16_384) {
            throw new IllegalArgumentException("dimension must be between 1 and 16384");
        }
        this.dimension = dimension;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public synchronized String upsert(VectorMemoryDocument document) {
        Objects.requireNonNull(document, "document");
        ensureDimension(document.embedding());
        ensureCollection();

        JsonObject point = new JsonObject();
        point.addProperty("id", document.card().memoryId().toString());
        JsonArray vector = new JsonArray();
        for (float value : document.embedding().values()) {
            vector.add(value);
        }
        point.add("vector", vector);
        JsonObject payload = new JsonObject();
        payload.addProperty("pet_id", document.card().petId().toString());
        payload.addProperty("memory_id", document.card().memoryId().toString());
        payload.addProperty("memory_version", document.card().version());
        payload.addProperty("active", document.card().active());
        payload.addProperty("embedding_model", document.embedding().model());
        point.add("payload", payload);

        JsonObject body = new JsonObject();
        JsonArray points = new JsonArray();
        points.add(point);
        body.add("points", points);
        send("PUT", "/collections/" + collection + "/points?wait=true", body);
        return document.card().memoryId().toString();
    }

    @Override
    public synchronized void delete(UUID petId, UUID memoryId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        ensureCollection();
        JsonObject body = new JsonObject();
        JsonObject filter = new JsonObject();
        JsonArray must = new JsonArray();
        must.add(match("pet_id", petId.toString()));
        must.add(match("memory_id", memoryId.toString()));
        filter.add("must", must);
        body.add("filter", filter);
        send("POST", "/collections/" + collection + "/points/delete?wait=true", body);
    }

    @Override
    public synchronized List<VectorMemoryMatch> search(
            MemorySearchQuery query, EmbeddingVector queryEmbedding) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding");
        ensureDimension(queryEmbedding);
        ensureCollection();

        JsonObject body = new JsonObject();
        JsonArray vector = new JsonArray();
        for (float value : queryEmbedding.values()) {
            vector.add(value);
        }
        body.add("vector", vector);
        body.addProperty("limit", query.maximumResults());
        body.addProperty("with_payload", true);
        body.addProperty("score_threshold", query.minimumSimilarity());
        JsonObject filter = new JsonObject();
        JsonArray must = new JsonArray();
        must.add(match("pet_id", query.petId().toString()));
        must.add(match("active", true));
        filter.add("must", must);
        body.add("filter", filter);

        JsonObject response = send("POST", "/collections/" + collection + "/points/search", body);
        JsonArray results = response.getAsJsonArray("result");
        if (results == null) {
            throw unavailable("Qdrant response did not contain result");
        }
        java.util.ArrayList<VectorMemoryMatch> matches = new java.util.ArrayList<>();
        for (JsonElement element : results) {
            JsonObject result = element.getAsJsonObject();
            JsonObject payload = result.getAsJsonObject("payload");
            if (payload == null) {
                continue;
            }
            UUID petId = UUID.fromString(payload.get("pet_id").getAsString());
            if (!query.petId().equals(petId)) {
                continue;
            }
            UUID memoryId = UUID.fromString(payload.get("memory_id").getAsString());
            long version = payload.get("memory_version").getAsLong();
            matches.add(new VectorMemoryMatch(petId, memoryId, version,
                    result.get("score").getAsDouble()));
        }
        return List.copyOf(matches);
    }

    private void ensureCollection() {
        if (collectionReady) {
            return;
        }
        HttpResponse<String> existing = sendRaw("GET", "/collections/" + collection, null, false);
        if (existing.statusCode() == 404) {
            JsonObject vectors = new JsonObject();
            vectors.addProperty("size", dimension);
            vectors.addProperty("distance", "Cosine");
            JsonObject body = new JsonObject();
            body.add("vectors", vectors);
            HttpResponse<String> created = sendRaw(
                    "PUT", "/collections/" + collection, body.toString(), false);
            if (created.statusCode() / 100 != 2 && created.statusCode() != 409) {
                throw unavailable("Qdrant collection creation failed with HTTP " + created.statusCode());
            }
        } else if (existing.statusCode() / 100 != 2) {
            throw unavailable("Qdrant collection lookup failed with HTTP " + existing.statusCode());
        }
        collectionReady = true;
    }

    private JsonObject send(String method, String path, JsonObject body) {
        HttpResponse<String> response = sendRaw(method, path, body.toString(), true);
        if (response.statusCode() / 100 != 2) {
            throw unavailable("Qdrant request failed with HTTP " + response.statusCode());
        }
        try {
            return response.body().isBlank()
                    ? new JsonObject() : JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw unavailable("Qdrant returned malformed JSON", malformed);
        }
    }

    private HttpResponse<String> sendRaw(
            String method, String path, String body, boolean failOnTransport) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(timeout)
                    .header("Accept", "application/json");
            apiKey.ifPresent(value -> builder.header("api-key", value));
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw unavailable("Qdrant transport failed", failOnTransport ? failure : failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unavailable("Qdrant request interrupted", failure);
        }
    }

    private void ensureDimension(EmbeddingVector embedding) {
        if (embedding.values().length != dimension) {
            throw new IllegalArgumentException(
                    "embedding dimension does not match Qdrant collection configuration");
        }
    }

    private static JsonObject match(String key, String value) {
        JsonObject condition = new JsonObject();
        condition.addProperty("key", key);
        JsonObject match = new JsonObject();
        match.addProperty("value", value);
        condition.add("match", match);
        return condition;
    }

    private static JsonObject match(String key, boolean value) {
        JsonObject condition = new JsonObject();
        condition.addProperty("key", key);
        JsonObject match = new JsonObject();
        match.addProperty("value", value);
        condition.add("match", match);
        return condition;
    }

    private static URI normalizeBaseUri(URI value) {
        Objects.requireNonNull(value, "baseUri");
        if (value.getScheme() == null || value.getHost() == null) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        String scheme = value.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("baseUri must use HTTP(S)");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
    }

    private static String validateCollection(String value) {
        Objects.requireNonNull(value, "collection");
        if (!value.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("collection contains unsupported characters");
        }
        return value;
    }

    private static VectorStoreUnavailableException unavailable(String message) {
        return new VectorStoreUnavailableException(message);
    }

    private static VectorStoreUnavailableException unavailable(String message, Throwable cause) {
        return new VectorStoreUnavailableException(message, cause);
    }
}
