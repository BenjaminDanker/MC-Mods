package com.silver.aipets.service.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.silver.aipets.service.billing.AiPricing;

/** OpenAI Chat Completions client using strict JSON Schema structured output. */
public final class OpenAiDialogueModelClient implements DialogueModelClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration defaultTimeout;
    private final String schemaName;
    private final AiPricing pricing;

    public OpenAiDialogueModelClient(
            URI baseUri, String model, String apiKey, Duration timeout) {
        this(baseUri, model, apiKey, timeout, "pet_dialogue", AiPricing.defaults());
    }

    public OpenAiDialogueModelClient(
            URI baseUri, String model, String apiKey, Duration timeout, String schemaName) {
        this(baseUri, model, apiKey, timeout, schemaName, AiPricing.defaults());
    }

    public OpenAiDialogueModelClient(
            URI baseUri, String model, String apiKey, Duration timeout,
            String schemaName, AiPricing pricing) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (baseUri.getHost() == null || !("http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("baseUri must be an absolute HTTP(S) URI");
        }
        this.endpoint = baseUri.resolve("/v1/chat/completions");
        this.model = bounded(model, "model", 191);
        this.apiKey = bounded(apiKey, "apiKey", 512);
        this.defaultTimeout = positive(timeout, "timeout");
        this.schemaName = bounded(schemaName, "schemaName", 64);
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.client = HttpClient.newBuilder().connectTimeout(defaultTimeout).build();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(requiredJsonSchema, "requiredJsonSchema");
        Duration effectiveTimeout = positive(timeout, "timeout");
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("reasoning_effort", "none");
        body.add("messages", messages(prompt.text()));
        body.addProperty("max_completion_tokens", 800);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        JsonObject schema = new JsonObject();
        schema.addProperty("name", schemaName);
        schema.addProperty("strict", true);
        schema.add("schema", parseObject(requiredJsonSchema));
        responseFormat.add("json_schema", schema);
        body.add("response_format", responseFormat);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(effectiveTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Request-Id", requestId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        Instant started = Instant.now();
        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("Dialogue provider transport failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dialogue provider request interrupted", failure);
        }
        long latency = Math.max(0L, Duration.between(started, Instant.now()).toMillis());
        if (response.statusCode() / 100 != 2 || response.body().length() > 1_000_000) {
            throw new IllegalStateException("Dialogue provider returned a bounded failure");
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() != 1) {
                throw new IllegalArgumentException("choices are invalid");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || message.has("refusal") && !message.get("refusal").isJsonNull()) {
                throw new IllegalArgumentException("provider refused structured output");
            }
            String content = message.get("content").getAsString();
            JsonObject usage = root.getAsJsonObject("usage");
            int input = usage == null ? prompt.inputTokens() : integer(usage, "prompt_tokens");
            int cached = usage == null ? 0 : cached(usage);
            int output = usage == null ? 0 : integer(usage, "completion_tokens");
            if (input < 0 || cached < 0 || cached > input || output < 0) {
                throw new IllegalArgumentException("usage is invalid");
            }
            return new DialogueModelResponse(
                    content,
                    Optional.ofNullable(root.get("id")).filter(JsonElement::isJsonPrimitive)
                            .map(JsonElement::getAsString),
                    input, cached, output, pricing.dialogueCost(input, cached, output), latency);
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Dialogue provider response was invalid", malformed);
        }
    }

    private static JsonArray messages(String prompt) {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", prompt);
        messages.add(system);
        return messages;
    }

    private static JsonObject parseObject(String value) {
        JsonElement parsed = JsonParser.parseString(value);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("schema must be an object");
        return parsed.getAsJsonObject();
    }

    private static int cached(JsonObject usage) {
        JsonObject details = usage.getAsJsonObject("prompt_tokens_details");
        return details == null || !details.has("cached_tokens")
                ? 0 : integer(details, "cached_tokens");
    }

    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        int result = value.getAsInt();
        if (value.getAsDouble() != result) throw new IllegalArgumentException(name + " is not integral");
        return result;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
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
