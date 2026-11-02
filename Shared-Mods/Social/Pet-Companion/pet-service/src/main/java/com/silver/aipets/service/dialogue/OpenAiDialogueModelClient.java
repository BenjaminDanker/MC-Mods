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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.provider.ProviderTarget;

/** OpenAI Chat Completions client using strict JSON Schema structured output. */
public final class OpenAiDialogueModelClient implements DialogueModelClient {
    private static final System.Logger LOGGER = System.getLogger(OpenAiDialogueModelClient.class.getName());
    public static final int MAX_COMPLETION_TOKENS = 2000;
    private final HttpClient client;
    private final ProviderTarget target;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration defaultTimeout;
    private final String schemaName;
    private final AiPricing pricing;
    private final String reasoningEffort;
    private final int maxCompletionTokens;

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
        this(baseUri, model, apiKey, timeout, schemaName, pricing, "none");
    }

    /** Explicit effort override is for isolated diagnostic calls only. */
    public OpenAiDialogueModelClient(
            URI baseUri, String model, String apiKey, Duration timeout,
            String schemaName, AiPricing pricing, String reasoningEffort) {
        this(baseUri, model, apiKey, timeout, schemaName, pricing, reasoningEffort, MAX_COMPLETION_TOKENS);
    }

    /**
     * @param reasoningEffort none/minimal/low/medium/high, or blank to omit the parameter
     * @param maxCompletionTokens completion ceiling for this call type
     */
    public OpenAiDialogueModelClient(
            URI baseUri, String model, String apiKey, Duration timeout,
            String schemaName, AiPricing pricing, String reasoningEffort, int maxCompletionTokens) {
        this(ProviderTarget.openAi(Objects.requireNonNull(baseUri, "baseUri"), apiKey), model, timeout,
                schemaName, pricing, reasoningEffort, maxCompletionTokens);
    }

    /**
     * The same client serves OpenAI and its OpenRouter fallback: the request body is identical, only the
     * target (endpoint, key, model id, and how cost is known) differs.
     */
    public OpenAiDialogueModelClient(
            ProviderTarget target, String model, Duration timeout,
            String schemaName, AiPricing pricing, String reasoningEffort, int maxCompletionTokens) {
        this.target = Objects.requireNonNull(target, "target");
        this.endpoint = target.endpoint("chat/completions");
        this.model = bounded(model, "model", 191);
        this.apiKey = target.apiKey();
        this.defaultTimeout = positive(timeout, "timeout");
        this.schemaName = bounded(schemaName, "schemaName", 64);
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        if (!reasoningEffort.isEmpty()
                && !java.util.Set.of("none", "minimal", "low", "medium", "high").contains(reasoningEffort)) {
            throw new IllegalArgumentException("reasoningEffort is invalid");
        }
        if (maxCompletionTokens < 1 || maxCompletionTokens > 8_000) {
            throw new IllegalArgumentException("maxCompletionTokens is invalid");
        }
        this.reasoningEffort = reasoningEffort;
        this.maxCompletionTokens = maxCompletionTokens;
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
        long serializationStart = System.nanoTime();
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        if (!reasoningEffort.isEmpty()) body.addProperty("reasoning_effort", reasoningEffort);
        body.add("messages", messages(prompt));
        body.addProperty("max_completion_tokens", maxCompletionTokens);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        JsonObject schema = new JsonObject();
        schema.addProperty("name", schemaName);
        schema.addProperty("strict", true);
        schema.add("schema", parseObject(requiredJsonSchema));
        responseFormat.add("json_schema", schema);
        body.add("response_format", responseFormat);
        if (target.isFallback()) {
            // Only route to providers that honor the structured-output parameters.
            JsonObject routing = new JsonObject();
            routing.addProperty("require_parameters", true);
            body.add("provider", routing);
        }
        String requestBody = body.toString();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(effectiveTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Request-Id", requestId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        DialogueProviderDiagnostics.emit("Request serialization complete: elapsed_ms="
                + ((System.nanoTime() - serializationStart) / 1_000_000));
        DialogueProviderDiagnostics.emit("Provider request: client_request_id=" + requestId
                + " endpoint=" + endpoint.getScheme() + "://" + endpoint.getHost()
                + endpoint.getPath() + " model=" + model + " reasoning_effort="
                + reasoningEffort + " max_completion_tokens=" + maxCompletionTokens + " stream=false"
                + " request_bytes=" + requestBody.getBytes(StandardCharsets.UTF_8).length
                + " timeout_ms=" + effectiveTimeout.toMillis());
        long transportStarted = System.nanoTime();
        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException failure) {
            throw new IllegalStateException("Dialogue provider transport failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dialogue provider request interrupted", failure);
        }
        long transportMs = (System.nanoTime() - transportStarted) / 1_000_000;
        long latency = transportMs;
        int responseBytes = response.body().getBytes(StandardCharsets.UTF_8).length;
        DialogueProviderDiagnostics.emit("Provider HTTP: status=" + response.statusCode()
                + " blocking_send_complete_ms=" + transportMs
                + " response_bytes=" + responseBytes
                + " x_request_id=" + response.headers().firstValue("x-request-id").orElse("<absent>")
                + " openai_processing_ms=" + response.headers().firstValue("openai-processing-ms")
                        .orElse("<absent>"));
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
            JsonObject completionDetails = usage == null ? null
                    : usage.getAsJsonObject("completion_tokens_details");
            String reasoningTokens = completionDetails == null
                    || !completionDetails.has("reasoning_tokens")
                    ? "<absent>" : completionDetails.get("reasoning_tokens").toString();
            JsonElement finishReason = choices.get(0).getAsJsonObject().get("finish_reason");
            if (finishReason == null || !finishReason.isJsonPrimitive()
                    || !finishReason.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("provider finish_reason is missing");
            }
            String finish = finishReason.getAsString();
            Optional<String> providerId = Optional.ofNullable(root.get("id"))
                    .filter(JsonElement::isJsonPrimitive).map(JsonElement::getAsString);
            Optional<String> providerRequestId = response.headers().firstValue("x-request-id")
                    .or(() -> providerId);
            DialogueProviderDiagnostics.emit("Provider result: id="
                    + providerId.orElse("<absent>")
                    + " provider_request_id=" + providerRequestId.orElse("<absent>")
                    + " configured_completion_limit=" + maxCompletionTokens
                    + " finish_reason=" + finish
                    + " token_limit_hit=" + finish.equals("length")
                    + " prompt_tokens=" + input + " cached_prompt_tokens=" + cached
                    + " completion_tokens=" + output + " reasoning_tokens=" + reasoningTokens
                    + " http_elapsed_ms=" + latency
                    + " structured_json_bytes=" + content.getBytes(StandardCharsets.UTF_8).length);
            LOGGER.log(System.Logger.Level.INFO, "pet_model_completion provider=" + target.name() + " model=" + model
                    + " request_id=" + providerRequestId.orElse("<absent>")
                    + " configured_completion_limit=" + maxCompletionTokens
                    + " finish_reason=" + finish + " token_limit_hit=" + finish.equals("length")
                    + " prompt_tokens=" + input + " cached_prompt_tokens=" + cached
                    + " completion_tokens=" + output + " reasoning_tokens=" + reasoningTokens
                    + " http_elapsed_ms=" + latency);
            return new DialogueModelResponse(
                    content,
                    providerId, input, cached, output,
                    target.cost(usage, () -> pricing.dialogueCost(input, cached, output)), latency, finish,
                    maxCompletionTokens, providerRequestId,
                    target.isFallback() ? DialogueModelResponse.FALLBACK : DialogueModelResponse.PRIMARY);
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("Dialogue provider response was invalid", malformed);
        }
    }

    private static JsonArray messages(DialoguePrompt prompt) {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", prompt.text());
        messages.add(system);
        for (DialogueTurn turn : prompt.chatHistory()) {
            JsonObject item = new JsonObject();
            item.addProperty("role", turn.role() == DialogueTurn.Role.OWNER ? "user" : "assistant");
            item.addProperty("content", turn.text());
            messages.add(item);
        }
        prompt.currentOwnerMessage().ifPresent(text -> {
            JsonObject owner = new JsonObject();
            owner.addProperty("role", "user");
            owner.addProperty("content", text);
            messages.add(owner);
        });
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
