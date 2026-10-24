package com.silver.aipets.service.dialogue;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiDialogueProviderTest {
    private static final String TOKEN = "test-openai-key";
    private static final String OUTPUT = """
            {"reply":"Hello, owner!","importance":"LOW","memory_candidate":null,
             "trait_deltas":{"curiosity":0,"boldness":0,"playfulness":0,"expressiveness":0,
             "independence":0,"attachment":0,"trust":0,"security":0},
             "mood_deltas":{"content":0,"excited":0,"anxious":0,"tired":0}}
            """.replaceAll("\\s+", "");

    @Test
    void sendsStrictSchemaAndParsesUsage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes());
            assertTrue(body.contains("json_schema"));
            assertTrue(body.contains("\"model\":\"gpt-6-luna\""));
            assertTrue(body.contains("\"reasoning_effort\":\"none\""));
            assertTrue(body.contains("\"max_completion_tokens\":2000"));
            var messages = com.google.gson.JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("messages");
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
            assertEquals("Pet context and instructions", messages.get(0).getAsJsonObject().get("content").getAsString());
            assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
            assertEquals("I was thinking about going back there.", messages.get(1).getAsJsonObject().get("content").getAsString());
            assertFalse(body.contains("\"stream\":true"));
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            String responseJson = "{\"id\":\"chatcmpl_test\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + quote(OUTPUT) + "}}],\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":12,"
                    + "\"prompt_tokens_details\":{\"cached_tokens\":4}}}";
            byte[] response = responseJson.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiDialogueModelClient client = new OpenAiDialogueModelClient(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "gpt-6-luna", TOKEN, Duration.ofSeconds(2));
            DialogueModelResponse result = client.complete(
                    UUID.randomUUID(), new DialoguePrompt("Pet context and instructions", 5,
                            com.silver.aipets.common.transport.DialogueContextUsageWire.empty(),
                            java.util.Optional.of("I was thinking about going back there.")),
                    DialogueStructuredSchema.VOICE_JSON_SCHEMA, Duration.ofSeconds(2));
            assertEquals(OUTPUT, result.structuredJson());
            assertEquals(42, result.inputTokens());
            assertEquals(4, result.cachedInputTokens());
            assertEquals(12, result.outputTokens());
            assertEquals("stop", result.finishReason());
            assertEquals(2000, result.configuredCompletionLimit());
            assertEquals(0, result.estimatedCost().compareTo(
                    new java.math.BigDecimal("0.00000984")));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer moderationServer(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/moderations", exchange -> {
            byte[] response = body.getBytes();
            exchange.sendResponseHeaders(status, status == 200 ? response.length : 0);
            if (status == 200) exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static OpenAiModerationClient moderation(HttpServer server) {
        return new OpenAiModerationClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "omni-moderation-latest", TOKEN, Duration.ofSeconds(2));
    }

    private static String categories(String... on) {
        java.util.Set<String> enabled = java.util.Set.of(on);
        StringBuilder out = new StringBuilder("{\"results\":[{\"flagged\":true,\"categories\":{");
        boolean first = true;
        for (String name : OpenAiModerationClient.KNOWN_CATEGORIES.stream().sorted().toList()) {
            if (!first) out.append(',');
            out.append('"').append(name).append("\":").append(enabled.contains(name));
            first = false;
        }
        return out.append("}}]}").toString();
    }

    @Test
    void onlySexualAndHateCategoriesBlockAMessage() throws Exception {
        for (String blocked : new String[]{"sexual", "sexual/minors", "hate", "hate/threatening"}) {
            HttpServer server = moderationServer(200, categories(blocked));
            try {
                OpenAiModerationClient.ModerationResult result = moderation(server).moderate("x");
                assertFalse(result.allowed(), blocked);
                assertTrue(result.available());
                assertEquals("MODERATION_BLOCKED:" + blocked, result.category());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void selfHarmHarassmentAndViolencePassSoThePetCanRespondOrReact() throws Exception {
        // The provider marks these "flagged", but they are not blocking categories.
        HttpServer server = moderationServer(200, categories("self-harm", "self-harm/intent", "harassment", "violence"));
        try {
            OpenAiModerationClient.ModerationResult result = moderation(server).moderate("you useless furball");
            assertTrue(result.allowed());
            assertTrue(result.available());
            assertEquals("ALLOWED", result.category());
            assertTrue(new ModeratedDialogueSafety(moderation(server)).preprocess("I feel awful", 500).allowed());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blockedCategoriesAreConfigurable() throws Exception {
        HttpServer server = moderationServer(200, categories("harassment"));
        try {
            var client = new OpenAiModerationClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "omni-moderation-latest", TOKEN, Duration.ofSeconds(2), java.util.Set.of("harassment"));
            assertFalse(client.moderate("x").allowed());
            assertThrows(IllegalArgumentException.class, () -> new OpenAiModerationClient(
                    URI.create("http://127.0.0.1:1"), "m", TOKEN, Duration.ofSeconds(2), java.util.Set.of("nonsense")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void moderationOutageOrBadResponseLetsTheMessageThroughAndIsReported() throws Exception {
        var metrics = new com.silver.aipets.service.metrics.PetOperationalMetrics();
        var reporter = new com.silver.aipets.service.provider.AiFallbackReporter(metrics);
        for (var server : new HttpServer[]{moderationServer(503, ""), moderationServer(200, "{\"results\":[{\"flagged\":true}]}")}) {
            try {
                DialogueSafety.SafetyResult result = new ModeratedDialogueSafety(moderation(server), reporter)
                        .preprocess("hello pet", 500);
                assertTrue(result.allowed());
                assertEquals("MODERATION_BYPASSED", result.category());
            } finally {
                server.stop(0);
            }
        }
        assertEquals(2, metrics.counter(com.silver.aipets.service.metrics.PetOperationalMetrics.Counter.MODERATION_BYPASSED));
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
