package com.silver.aipets.service.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.silver.aipets.common.transport.DialogueContextUsageWire;
import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialoguePrompt;
import com.silver.aipets.service.dialogue.DialogueStructuredSchema;
import com.silver.aipets.service.dialogue.DialogueTurn;
import com.silver.aipets.service.dialogue.OpenAiDialogueModelClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The OpenRouter fallback is the shared OpenAI client pointed at a different target. */
class OpenRouterTargetTest {
    private static final String KEY = "sk-or-synthetic-test-key";
    private static final String VOICE = "{\"reply\":\"Hmph.\",\"action\":\"NONE\",\"attention\":\"CALM\"}";

    private static OpenAiDialogueModelClient client(FakeOpenRouter fake) {
        return new OpenAiDialogueModelClient(
                ProviderTarget.openRouter(fake.base(), KEY, new BigDecimal("0.003")), "openai/gpt-6-luna",
                Duration.ofSeconds(2), "pet_dialogue", AiPricing.defaults(), "none", 1_000);
    }

    @Test
    void sendsTheSameStrictRequestToOpenRouterAndChargesItsReportedCost() throws Exception {
        try (FakeOpenRouter fake = new FakeOpenRouter()) {
            fake.reply(200, "{\"id\":\"gen-1\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + new com.google.gson.JsonPrimitive(VOICE) + "}}],"
                    + "\"usage\":{\"prompt_tokens\":420,\"completion_tokens\":31,\"cost\":0.0000578,"
                    + "\"prompt_tokens_details\":{\"cached_tokens\":64}}}");
            Instant at = Instant.parse("2026-10-07T18:00:00Z");
            DialoguePrompt prompt = new DialoguePrompt("You are Mica.", 50, DialogueContextUsageWire.empty(),
                    Optional.of("Did you miss me?"), List.of(new DialogueTurn(DialogueTurn.Role.OWNER, "Hi.", at),
                            new DialogueTurn(DialogueTurn.Role.PET, "Mm.", at.plusSeconds(5))));
            DialogueModelResponse response = client(fake).complete(UUID.randomUUID(), prompt,
                    DialogueStructuredSchema.VOICE_JSON_SCHEMA, Duration.ofSeconds(2));
            FakeOpenRouter.Request request = fake.requests().getFirst();
            assertEquals("/api/v1/chat/completions", request.path());
            assertEquals("Bearer " + KEY, request.authorization());
            JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();
            assertEquals("openai/gpt-6-luna", body.get("model").getAsString());
            assertEquals("none", body.get("reasoning_effort").getAsString());
            assertEquals(1_000, body.get("max_completion_tokens").getAsInt());
            assertTrue(body.getAsJsonObject("provider").get("require_parameters").getAsBoolean());
            assertTrue(body.getAsJsonObject("response_format").getAsJsonObject("json_schema").get("strict").getAsBoolean());
            var messages = body.getAsJsonArray("messages");
            assertEquals(4, messages.size());
            assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
            assertEquals(VOICE, response.structuredJson());
            assertEquals(420, response.inputTokens());
            assertEquals(64, response.cachedInputTokens());
            // The charge is OpenRouter's usage.cost, rounded up to the ledger's 8 decimal places.
            assertEquals(0, new BigDecimal("0.00005780").compareTo(response.estimatedCost()));
            assertTrue(response.servedByFallback());
            assertEquals("FALLBACK:openrouter:gpt-6-luna", response.usageModel("gpt-6-luna"));
        }
    }

    @Test
    void missingCostIsChargedConservativelyAndFailuresNeverLeakTheKey() throws Exception {
        try (FakeOpenRouter fake = new FakeOpenRouter()) {
            fake.reply(200, "{\"id\":\"gen-2\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + new com.google.gson.JsonPrimitive(VOICE) + "}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");
            DialogueModelResponse response = client(fake).complete(UUID.randomUUID(), new DialoguePrompt("x", 1),
                    DialogueStructuredSchema.VOICE_JSON_SCHEMA, Duration.ofSeconds(2));
            assertEquals(0, new BigDecimal("0.003").compareTo(response.estimatedCost()));

            fake.reply(429, "{\"error\":{\"message\":\"rate limited " + KEY + "\"}}");
            IllegalStateException limited = assertThrows(IllegalStateException.class, () -> client(fake).complete(
                    UUID.randomUUID(), new DialoguePrompt("x", 1), DialogueStructuredSchema.VOICE_JSON_SCHEMA,
                    Duration.ofSeconds(2)));
            assertFalse(String.valueOf(limited.getMessage()).contains(KEY));
        }
    }

    @Test
    void primaryOpenAiTargetIsPricedFromTokensAndNotMarkedAsFallback() {
        ProviderTarget target = ProviderTarget.openAi(java.net.URI.create("https://api.openai.com"), "sk-test");
        assertEquals("https://api.openai.com/v1/chat/completions", target.endpoint("chat/completions").toString());
        assertEquals("https://api.openai.com/v1/embeddings", target.endpoint("embeddings").toString());
        assertFalse(target.isFallback());
        assertEquals(0, new BigDecimal("0.00015").compareTo(target.cost(null,
                () -> AiPricing.defaults().dialogueCost(1000, 0, 100))));
        ProviderTarget router = ProviderTarget.openRouter(
                java.net.URI.create("https://openrouter.ai/api/v1/"), "sk-or-test", new BigDecimal("0.003"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions", router.endpoint("chat/completions").toString());
        assertTrue(router.isFallback());
    }
}
